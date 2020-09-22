package dev.pje.bots.apoiadorrequisitante.handlers.gitlab;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.GitlabMergeRequestActionsEnum;
import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.gitlab.event.GitlabEventMergeRequest;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraUser;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class Gitlab030MergeRequestMergeOrCloseHandler extends Handler<GitlabEventMergeRequest>{

	private static final Logger logger = LoggerFactory.getLogger(Gitlab030MergeRequestMergeOrCloseHandler.class);

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
		return "|GITLAB||030||MERGE-REQUEST-MERGE-CLOSE|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	private static final Boolean ATUAR_PROJETO_PJE = false; // TODO - retirar isso depois que transformar o bot revisor em um consumer

	/***
	 * Monitora as aprovações de MR no git
	 * ok- identifica se o MR foi aceito ou fechado:
	 * :: se aceito ::
	 * ok-- localiza cada commit do MR aprovado e tenta identificar as issues relacionadas (pode haver mais de um commit, cada um de uma issue diferente)
	 * ok--- indica em qual branch foi integrado
	 * ok --- indica quem foi o responsável pela revisão
	 * ok--- recupera a lista de "MRs Abertos" e verifica se continuam abertos
	 * ok--- adiciona o MR atual na lista de "MRs Aceitos"
	 * ok--- adiciona o sourceBranch na lista de branchs relacionados
	 * ok--- verifica se há transição de saída de aprovação;
	 * ok---- se houver, transita
	 * ok---- se não houver, verifica se há transição de saída de "edição avançada"
	 * ok----- se houver, transita
	 * ok----- se não houver ou houver erro na transição anterior, faz apenas um comentário na issue , passando todos os dados que deveriam estar na issue
	 * :: se fechado ::
	 * ok-- localiza cada commit do MR e tenta idnetificar as issues relacionadas
	 * ok--- indica quem foi o responsável pela revisão
	 * ok--- recupera a lista de "MRs Abertos" e verifica se continuam abertos
	 * ok--- adiciona o sourceBranch na lista de branchs relacionados
	 * ok--- verifica se há transição de saída de reprovação
	 * ok---- se houver, transita
	 * ok---- se não houver, verifica se há transição de saída de "edição avançada"
	 * ok----- se houver, transita
	 * ok----- se não houver ou houver erro na transição anterior, faz apenas um comentário na issue , passando todos os dados que deveriam estar na issue
	 */
	@Override
	public void handle(GitlabEventMergeRequest gitlabEventMR) throws Exception {
		messages.clean();
		if (gitlabEventMR != null && gitlabEventMR.getObjectAttributes() != null && gitlabEventMR.getObjectAttributes().getAction() != null
				&& (GitlabMergeRequestActionsEnum.MERGE.equals(gitlabEventMR.getObjectAttributes().getAction())
						|| GitlabMergeRequestActionsEnum.CLOSE.equals(gitlabEventMR.getObjectAttributes().getAction())
						)) {

			String gitlabProjectId = gitlabEventMR.getProject().getId().toString();

			String sourceBranch = gitlabEventMR.getObjectAttributes().getSourceBranch();
			String targetBranch = gitlabEventMR.getObjectAttributes().getTargetBranch();
			String mergeTitle = gitlabEventMR.getObjectAttributes().getTitle();
			GitlabUser revisor = gitlabEventMR.getUser();
			GitlabCommit lastCommit = gitlabEventMR.getObjectAttributes().getLastCommit();
			String lastCommitTitle = lastCommit.getTitle();

			if(StringUtils.isNotBlank(mergeTitle) && mergeTitle.startsWith("Revert")) {
				messages.info("Este é um merge de revert, ignorando.");
			}else {

				// localiza a issue relacionda
				String issueKey = Utils.getIssueKeyFromCommitMessage(mergeTitle);
				if(StringUtils.isBlank(issueKey)) {
					issueKey = Utils.getIssueKeyFromCommitMessage(lastCommitTitle);
				}

				if(StringUtils.isNotBlank(issueKey)) {
					JiraIssue issue = jiraService.recuperaIssueDetalhada(issueKey);
					// recupera a lista de "MRs Abertos" e verifica se continuam abertos
					if(issue != null) {

						if(ATUAR_PROJETO_PJE || !issue.getFields().getProject().getProjectCategory().getName().equalsIgnoreCase("PJE")) {
							String MRsAbertos = issue.getFields().getMrAbertos();
							String MrsAbertosConfirmados = gitlabService.checkMRsOpened(MRsAbertos);
							Map<String, Object> updateFields = new HashMap<>();
							String transitionID = null;
							jiraService.atualizarMRsAbertos(issue, MrsAbertosConfirmados, updateFields, true);
							JiraUser revisorUsuarioJira = jiraService.getJiraUserFromGitlabUser(revisor);
							jiraService.atualizarResponsavelRevisao(issue, revisorUsuarioJira, updateFields);
							jiraService.atualizarBranchRelacionado(issue, sourceBranch, updateFields, false);

							JiraIssueTransition transition = null;
							JiraMarkdown jiraMarkdown = new JiraMarkdown();
							StringBuilder textoComentarioAcao = new StringBuilder();

							String MrURL = gitlabEventMR.getObjectAttributes().getUrl();
							String MrIId = gitlabEventMR.getObjectAttributes().getIid().toString();

							if(GitlabMergeRequestActionsEnum.MERGE.equals(gitlabEventMR.getObjectAttributes().getAction())) {
								jiraService.atualizarMRsAceitos(issue, MrURL, updateFields, false);
								jiraService.atualizarIntegradoNosBranches(issue, targetBranch, updateFields, false);
								textoComentarioAcao.append(jiraMarkdown.underline(jiraMarkdown.link(MrURL, "MR#" + MrIId)))
									.append(" integrado ao branch ")
									.append(jiraMarkdown.bold(targetBranch));
								if(revisorUsuarioJira != null && revisorUsuarioJira.getDisplayName() != null) {
									if(revisorUsuarioJira.getDisplayName().equalsIgnoreCase(jiraBotUser)) {
										textoComentarioAcao.append(" automaticamente pelo ")
										.append(jiraMarkdown.underline("[~" + revisorUsuarioJira.getName() + "]"));
									}else {
										textoComentarioAcao.append(" pelo usuário revisor ")
										.append(jiraMarkdown.underline("[~" + revisorUsuarioJira.getName() + "]"));
									}
								}
								String fixVersion = gitlabService.getActualVersion(gitlabProjectId, targetBranch, true);
								if(StringUtils.isNotBlank(fixVersion)) {
									jiraService.atualizarFixVersion(issue, fixVersion, updateFields, false);
									transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, JiraService.TRANSITION_PROPERTY_KEY_APROVADA);
								}else {
									messages.error("Não conseguiu identificar a versão atual do projeto: " + gitlabProjectId + " - targetBranch: "+targetBranch);
								}

							}else if(GitlabMergeRequestActionsEnum.CLOSE.equals(gitlabEventMR.getObjectAttributes().getAction())) {
								transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, JiraService.TRANSITION_PROPERTY_KEY_REPROVADA);						

								textoComentarioAcao.append(jiraMarkdown.underline(jiraMarkdown.link(MrURL, "MR#" + MrIId)))
								.append(" " + jiraMarkdown.bold("fechado") + " ");
								if(revisorUsuarioJira != null && revisorUsuarioJira.getDisplayName() != null) {
									if(revisorUsuarioJira.getDisplayName().equalsIgnoreCase(jiraBotUser)) {
										textoComentarioAcao.append(" automaticamente pelo ")
										.append(jiraMarkdown.underline("[~" + revisorUsuarioJira.getName() + "]"));
									}else {
										textoComentarioAcao.append(" pelo usuário revisor ")
										.append(jiraMarkdown.underline("[~" + revisorUsuarioJira.getName() + "]"));
									}
								}
							}

							if(transition != null) {
								transitionID = transition.getId();
							}

							StringBuilder textoComentario = new StringBuilder(messages.getMessagesToJira());
							textoComentario.append(jiraMarkdown.newLine());
							textoComentario.append(textoComentarioAcao.toString());

							jiraService.adicionarComentario(issue, textoComentario.toString(), updateFields);

							try {
								enviarAlteracaoJira(issue, updateFields, null, transitionID, true, true);
							}catch (Exception e) {
								messages.error("Falhou ao tentar atualizar a issue: " + issue.getKey() + " - erro: " + e.getLocalizedMessage());
							}
						}else {
							messages.info("Este consumer não atuará nas issues do PJe (por hora)");
						}

					}else {
						messages.error("A issue indicada: " + issueKey + ", não está accessível.");
					}
				}else {
					messages.error("Não foi encontrada uma issue referência no merge request");
				}
			}
		}
	}	
}