package dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab;

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

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class MergeRequestUpdateHandler extends Handler<GitlabEventMergeRequest>{

	private static final Logger logger = LoggerFactory.getLogger(MergeRequestUpdateHandler.class);

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
		return "|GITLAB||MERGE-REQUEST-UPDATED|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}



	private static final String TRANSITION_PROPERTY_KEY_INDICAR_APROVACAO = "MR APROVADO"; // TODO buscar por propriedade da transicao
	private static final String TRANSITION_PROPERTY_KEY_INDICAR_FECHAMENTO = "MR FECHADO"; // TODO buscar por propriedade da transicao

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
				&& GitlabMergeRequestActionsEnum.MERGE.equals(gitlabEventMR.getObjectAttributes().getAction())
				&& GitlabMergeRequestActionsEnum.CLOSE.equals(gitlabEventMR.getObjectAttributes().getAction())) {

			String sourceBranch = gitlabEventMR.getObjectAttributes().getSourceBranch();

			String targetBranch = gitlabEventMR.getObjectAttributes().getTargetBranch();
			String mergeTitle = gitlabEventMR.getObjectAttributes().getTitle();
			GitlabUser revisor = gitlabEventMR.getUser();
			GitlabCommit lastCommit = gitlabEventMR.getObjectAttributes().getLastCommit();
			String lastCommitTitle = lastCommit.getTitle();

			// localiza a issue relacionda
			String issueKey = Utils.getIssueKeyFromCommitMessage(mergeTitle);
			if(StringUtils.isBlank(issueKey)) {
				issueKey = Utils.getIssueKeyFromCommitMessage(lastCommitTitle);
			}

			if(StringUtils.isNotBlank(issueKey)) {
				JiraIssue issue = jiraService.recuperaIssueDetalhada(issueKey);
				// recupera a lista de "MRs Abertos" e verifica se continuam abertos
				if(issue != null) {
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
						textoComentarioAcao.append(jiraMarkdown.link(MrURL, "MR#" + MrIId))
							.append(" integrado ao branch ")
							.append(targetBranch);
						if(revisorUsuarioJira != null && revisorUsuarioJira.getDisplayName() != null) {
							if(revisorUsuarioJira.getDisplayName().equalsIgnoreCase(jiraBotUser)) {
								textoComentarioAcao.append(" automaticamente pelo [~" + revisorUsuarioJira.getDisplayName() + "]");
							}else {
								textoComentarioAcao.append(" pelo usuário revisor [~" + revisorUsuarioJira.getDisplayName() + "]");
							}
						}
						transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, TRANSITION_PROPERTY_KEY_INDICAR_APROVACAO);

					}else if(GitlabMergeRequestActionsEnum.CLOSE.equals(gitlabEventMR.getObjectAttributes().getAction())) {
						transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, TRANSITION_PROPERTY_KEY_INDICAR_FECHAMENTO);						

						textoComentarioAcao.append(jiraMarkdown.link(MrURL, "MR#" + MrIId))
							.append(" fechado ");
						if(revisorUsuarioJira != null && revisorUsuarioJira.getDisplayName() != null) {
							if(revisorUsuarioJira.getDisplayName().equalsIgnoreCase(jiraBotUser)) {
								textoComentarioAcao.append(" automaticamente pelo [~" + revisorUsuarioJira.getDisplayName() + "]");
							}else {
								textoComentarioAcao.append(" pelo usuário revisor [~" + revisorUsuarioJira.getDisplayName() + "]");
							}
						}
					}

					// se não tiver encontrado a transição específica relacionada à operação, busca a transição de "Edição avançada"
					if(transition == null) {
						messages.info("Não foi identificada uma saída padrão desta issue para registro da nova situação do "
								+ "MR relacionado. Buscando transição '" + JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA + "'");
						transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA);
					}

					// se encontrou a transicao, transita para ela, caso contrário apenas adiciona um comentário à issue
					if(transition != null) {
						transitionID = transition.getId();

						StringBuilder textoComentario = new StringBuilder(messages.getMessagesToJira());
						textoComentario.append(jiraMarkdown.newLine());
						textoComentario.append(jiraMarkdown.block("Caso se pretenda utilizar anexos (imagem ou outro formato) deve-se utilizar no arquivo principal de documentação .adoc, referências"
								+ " à pasta '" + JiraService.DOCUMENTATION_ASSETS_DIR + "', pois todos os documentos anexados a esta issue que não sejam .adoc serão enviados ao reposiorio na pasta"
								+ " '" + JiraService.DOCUMENTATION_ASSETS_DIR + "' no mesmo path do arquivo principal."));

						jiraService.adicionarComentario(issue, textoComentario.toString(), updateFields);
						jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);

						enviarAlteracaoJira(issue, updateFields, transitionID);
					}else {
						messages.info("Não foi identificada uma transição válida para registro da nova situação do MR, os dados relacionados"
								+ " serão registrados na issue como comentário.");
						messages.info(updateFields.toString());
						jiraService.sendTextAsComment(issue, messages.getMessagesToJira());
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