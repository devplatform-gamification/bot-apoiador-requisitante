package dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.event.GitlabEventPushTag;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class Gitlab04TagPushHandler extends Handler<GitlabEventPushTag>{

	private static final Logger logger = LoggerFactory.getLogger(Gitlab04TagPushHandler.class);

	@Value("${clients.gitlab.url}")
	private String gitlabUrl;

	@Value("${clients.jira.user}")
	private String jiraBotUser;

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	public String getMessagePrefix() {
		return "|GITLAB||04||TAG-PUSHED|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	private static final String TRANSITION_PROPERTY_KEY_FINALIZAR_VERSAO = "FINALIZAR VERSAO";
	private static final String TRANSITION_PROPERTY_KEY_REABRIR_VERSAO = "REABRIR VERSAO";

	private List<IssueTransitions> issueTransitions = new ArrayList<>();
	/**
	 * :: Lançamento de tag :: (geral)
	 * ok - recupera qual é a versão do projeto no commit da tag
	 * ok - recupera qual(is) é o(são os) projeto(s) no jira relacionado(s) ao projeto da tag
	 * ok - busca todas as issues identificadas como sendo dessa versão 
	 * ok - Verifica se a tag está sendo criada ou removida
	 * ok -- se tag criada:
	 * ok --- tramita as issues para o fechamento da versão dada pelo número da tag
	 * -- se tag removida:
	 * --- tramita as issues para uma situacao de reabertura da versao encerrada 
	 */
	@Override
	public void handle(GitlabEventPushTag gitlabEventTag) throws Exception {
		messages.clean();
		if (gitlabEventTag != null && gitlabEventTag.getProjectId() != null) {

			String gitlabProjectId = gitlabEventTag.getProjectId().toString();
			Boolean tagCreated = true;
			String referenceCommit = gitlabEventTag.getAfter();
			if(StringUtils.isNotBlank(referenceCommit)) {
				try {
					Integer afterHash = Integer.valueOf(referenceCommit);
					if(afterHash == 0) {
						tagCreated = false;
						referenceCommit = gitlabEventTag.getBefore();
					}
				}catch (Exception e) {
					if(e instanceof NumberFormatException) {
						messages.debug("O hash do commit " + referenceCommit + " parece válido");
					}
				}
			}
			if(StringUtils.isNotBlank(referenceCommit)) {
				String actualVersion = gitlabService.getActualVersion(gitlabProjectId, referenceCommit, true);
				if(StringUtils.isNotBlank(actualVersion)) {
					// identifica qual é o projeto do jira no qual foi integrada a TAG
					String jiraProjectKey = gitlabService.getJiraRelatedProjectKey(gitlabProjectId);
					// verifica se existem outros projetos relacionados a esse do jira
					List<String> jiraRelatedProjectKeys = jiraService.getJiraRelatedProjects(jiraProjectKey);
					// recupera a lista de issues com o status "aguardando o lançamento da versão"
					String jql = jiraService.getJqlIssuesFromFixVersion(actualVersion, false, jiraRelatedProjectKeys);
					List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
					indicaLancamentoVersaoIssues(issues, actualVersion, tagCreated);
				}else {
					messages.error("Falhou ao tentar identificar a versão do projeto: " + gitlabProjectId + " no commit: "+ referenceCommit);
				}
			}
		}
	}
	
	private void indicaLancamentoVersaoIssues(List<JiraIssue> issuesParaTramitar, String versaoLancada, Boolean tagCreated) throws Exception {
		if(issuesParaTramitar != null && !issuesParaTramitar.isEmpty()) {
			for (JiraIssue issue : issuesParaTramitar) {

				if(issue.getFields() != null && issue.getFields().getProject() != null && issue.getFields().getStatus() != null && issue.getFields().getStatus().getId() != null) {
					Map<String, Object> updateFields = new HashMap<>();
					String projectId = issue.getFields().getProject().getKey();
					String statusId = issue.getFields().getStatus().getId().toString();
					JiraIssueTransition transition = findTransitionForProjectAndStatus(projectId, statusId);
					if(transition == null) {
						if(tagCreated) {
							transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, TRANSITION_PROPERTY_KEY_FINALIZAR_VERSAO);
						}else {
							transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, TRANSITION_PROPERTY_KEY_REABRIR_VERSAO);
						}
					}
					if(transition == null) {
						if(tagCreated) {
							messages.info("Não foi identificada uma saída padrão desta issue para a finalização da versão.");
						}else {
							messages.info("Não foi identificada uma saída padrão desta issue para a reabertura da versão.");
						}
						messages.info("Buscando transição '" + JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA + "'");
						transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA);
					}
					if(transition == null) {
						messages.info("Não foi identificada uma transição válida para registro da nova informação, os dados relacionados"
								+ " serão registrados na issue como comentário.");
					}
					JiraMarkdown jiraMarkdown = new JiraMarkdown();
					StringBuilder textoComentario = new StringBuilder(messages.getMessagesToJira());
					textoComentario.append(jiraMarkdown.newLine());
					if(tagCreated) {
						textoComentario.append(jiraMarkdown.block("A versão " + versaoLancada + " foi lançada e as alterações desta issue disponibilizadas na versão nacional."));
					}else {
						textoComentario.append(jiraMarkdown.block("A tag da versão " + versaoLancada + " foi cancelada e as alterações desta issue já não estão mais disponíveis em uma versão nacional."));
					}

					jiraService.adicionarComentario(issue, textoComentario.toString(), updateFields);

					addIssueToIssueTransition(projectId, statusId, transition, updateFields, issue, textoComentario.toString());
				}else {
					messages.error("Não conseguiu identificar o projeto e/ou o status da issue: " + issue.getKey());
				}
			}
		}
		executeInBulkIssueTransitions();
	}
	
	private JiraIssueTransition findTransitionForProjectAndStatus(String projectId, String statusId) {
		JiraIssueTransition transition = null;
		if(this.issueTransitions == null) {
			for (IssueTransitions issueTransition : issueTransitions) {
				if(issueTransition.getProjectId().equals(projectId) && issueTransition.getStatusId().equals(statusId)) {
					transition = issueTransition.getTransition();
					break;
				}
			}
		}
		return transition;
	}
	
	private void addIssueToIssueTransition(String projectId, String statusId, JiraIssueTransition transition,
			Map<String, Object> updateFields, JiraIssue issue, String comment) {
		
		if(this.issueTransitions == null) {
			this.issueTransitions = new ArrayList<>();
		}
		IssueTransitions issueTransition = null;
		List<JiraIssue> issues = null;
		for (int i=0; i < this.issueTransitions.size(); i++) {
			IssueTransitions issueTransitionAux = this.issueTransitions.get(i);
			if(issueTransitionAux.getProjectId().equals(projectId) 
					&& issueTransitionAux.getStatusId().equals(statusId) 
					&& issueTransitionAux.getTransition() != null && issueTransitionAux.getTransition().equals(transition)) {
				issueTransition = issueTransitionAux;
				issues = issueTransition.getIssues();
				this.issueTransitions.remove(i);
				break;
			}
		}
		if(issueTransition == null) {
			issueTransition = new IssueTransitions(projectId, statusId, transition, updateFields, issues, comment);
		}
		if(issues == null) {
			issues = new ArrayList<>();
		}
		if(!issues.contains(issue)) {
			issues.add(issue);
			issueTransition.setIssues(issues);
		}
		this.issueTransitions.add(issueTransition);
	}
	
	private void executeInBulkIssueTransitions() throws Exception {
		if(this.issueTransitions != null) {
			for (IssueTransitions issueTransition : issueTransitions) {
				Map<String, Object> updateFields = issueTransition.getUpdateFields();
				for (JiraIssue issue : issueTransition.getIssues()) {
					JiraIssueTransition transition = issueTransition.getTransition();
					if(transition != null) {
						String transitionID = transition.getId();
						try {
							enviarAlteracaoJira(issue, updateFields, transitionID, false, false);
						}catch (Exception e) {
							messages.error("Falhou ao tentar transitar a issue: " + issue.getKey() + " para a transição: " + transition.getName() + " (" + transition.getId() + ") - erro: " + e.getLocalizedMessage());
							throw new Exception(e);
						}
					}else {
						String comment = issueTransition.getComment();
						if(StringUtils.isNotBlank(comment)) {
							jiraService.sendTextAsComment(issue, comment);									
						}
					}
				}
			}
		}
	}
}

class IssueTransitions{
	private String projectId;
	private String statusId;
	private JiraIssueTransition transition;
	private Map<String, Object> updateFields = new HashMap<>();
	private List<JiraIssue> issues;
	private String comment;
	
	public IssueTransitions(String projectId, String statusId, JiraIssueTransition transition,
			Map<String, Object> updateFields, List<JiraIssue> issues, String comment) {
		super();
		this.projectId = projectId;
		this.statusId = statusId;
		this.transition = transition;
		this.updateFields = updateFields;
		this.issues = issues;
		this.comment = comment;
	}
	public String getProjectId() {
		return projectId;
	}
	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}
	public String getStatusId() {
		return statusId;
	}
	public void setStatusId(String statusId) {
		this.statusId = statusId;
	}
	public JiraIssueTransition getTransition() {
		return transition;
	}
	public void setTransition(JiraIssueTransition transition) {
		this.transition = transition;
	}
	public Map<String, Object> getUpdateFields() {
		return updateFields;
	}
	public void setUpdateFields(Map<String, Object> updateFields) {
		this.updateFields = updateFields;
	}
	public List<JiraIssue> getIssues() {
		return issues;
	}
	public void setIssues(List<JiraIssue> issues) {
		this.issues = issues;
	}
	
	
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("class IssueTransitions{\n    projectId:     ");
		builder.append(projectId);
		builder.append("\n    statusId:     ");
		builder.append(statusId);
		builder.append("\n    transition:     ");
		builder.append(transition);
		builder.append("\n    updateFields:     ");
		builder.append(updateFields);
		builder.append("\n    issues:     ");
		builder.append(issues);
		builder.append("\n    comment:     ");
		builder.append(comment);
		builder.append("\n}");
		return builder.toString();
	}
	
}