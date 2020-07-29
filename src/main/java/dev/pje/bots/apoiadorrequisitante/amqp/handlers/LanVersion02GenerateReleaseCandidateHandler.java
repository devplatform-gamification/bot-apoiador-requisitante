package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.ReleaseCandidateTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.GitlabMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.TelegramMarkdownHtml;

@Component
public class LanVersion02GenerateReleaseCandidateHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion02GenerateReleaseCandidateHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||02||RELEASE CANDIDATE|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	@Autowired
	private ReleaseCandidateTextModel releaseCandidateModel;

	private static final String TRANSITION_ID_IMPEDIMENTO = "221"; // TODO buscar por propriedade da transicao
	private static final String TRANSITION_ID_CONCLUIR_RC = "211"; // TODO buscar por propriedade da transicao
		

	/**
	 * - Verificar se já não existe a tag da RC
	 * - Gerar tag RC
	 * - Verificar se já não existe a tag da RC
	 * - Gerar branch release da versão candidata
	 * - Comunicar a disponibilização da release candidate
	 */
	@Override
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_NEW_VERSION) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_GERAR_RELEASE_CANDIDATE_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_GERAR_RELEASE_CANDIDATE_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());
				
				String versaoASerLancada = issue.getFields().getVersaoSeraLancada();
				String versaoAfetada = JiraUtils.getVersaoAfetada(issue.getFields().getVersions());
				boolean tagJaExistente = false;
				boolean tagReleaseJaExistente = false;
				boolean releaseBranchJaExistente = false;
				String gitlabProjectId = jiraService.getGitlabProjectFromIssue(issue);
				
				boolean implementsGitflow = false;
				if (StringUtils.isNotBlank(gitlabProjectId)) {
					implementsGitflow = gitlabService
							.isProjectImplementsGitflow(gitlabProjectId);
				}
				
				if(implementsGitflow) {
					if(StringUtils.isNotBlank(versaoAfetada)) {
						if(StringUtils.isBlank(versaoASerLancada)){
							versaoASerLancada = versaoAfetada;
						}
						// verifica se a tag da versão já existe
						if(StringUtils.isNotBlank(gitlabProjectId)) {
							GitlabTag tag = gitlabService.getVersionTag(gitlabProjectId, versaoASerLancada);
							if(tag != null && tag.getCommit() != null) {
								tagJaExistente = true;
							}
						}

						if(!tagJaExistente) {
							String nomeReleaseBranch = GitlabService.BRANCH_RELEASE_CANDIDATE_PREFIX + versaoASerLancada;
							String jql = jiraService.getJqlIssuesFromFixVersion(versaoASerLancada, false, issue.getFields().getProject().getKey());

							releaseCandidateModel.setProjectName(issue.getFields().getProject().getName());
							releaseCandidateModel.setVersion(versaoASerLancada);
							releaseCandidateModel.setJql(jql);
							releaseCandidateModel.setBranchName(nomeReleaseBranch);
							String nomeTagReleaseCandidate = versaoASerLancada + GitlabService.TAG_RELEASE_CANDIDATE_SUFFIX;
							// verifica se a tag existe
							if(StringUtils.isNotBlank(gitlabProjectId)) {
								GitlabTag tag = gitlabService.getVersionTag(gitlabProjectId, nomeTagReleaseCandidate);
								if(tag != null && tag.getCommit() != null) {
									tagReleaseJaExistente = true;
								}
							}
		
							if(!tagReleaseJaExistente) {
								messages.debug("Lançando a tag: " + nomeTagReleaseCandidate);
								String tagMessage = "[" + issue.getKey() + "] Iniciando a homologação da release candidate " + nomeTagReleaseCandidate;
								GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
								GitlabTag tagRC = gitlabService.createVersionTag(gitlabProjectId, nomeTagReleaseCandidate, GitlabService.BRANCH_DEVELOP,
										tagMessage, releaseCandidateModel.convert(gitlabMarkdown));
								if(tagRC != null) {
									messages.info("Tag lançada: " + nomeTagReleaseCandidate);
								}else {
									messages.error("Falhou ao tentar lançar a tag: " + nomeTagReleaseCandidate);
								}
							}else {
								messages.info("A tag (" + nomeTagReleaseCandidate + ") da release candidate já havia sido gerada.");
							}
							//verifca se o brannch RC já existe
							String releaseBranch = gitlabService.getActualReleaseBranch(gitlabProjectId);
							if (StringUtils.isNotBlank(releaseBranch) && releaseBranch.equals(nomeReleaseBranch)) {
								releaseBranchJaExistente = true;
							}
							if(!releaseBranchJaExistente) {
								messages.debug("Gerando branch: " + nomeReleaseBranch);
								GitlabBranchResponse branchResponse = gitlabService.createBranch(gitlabProjectId, nomeReleaseBranch, GitlabService.BRANCH_DEVELOP);
								
								if(branchResponse != null) {
									messages.info("Branch criada: " + nomeReleaseBranch);
								}else {
									messages.error("Falhou ao tentar criar o branch: " + nomeReleaseBranch);
								}
							}else {
								messages.info("O branch (" + nomeReleaseBranch + ") da release candidate já havia sido gerado.");
							}
						}else {
							messages.error("A tag: " + versaoASerLancada + " já foi lançada, não há como gerar uma release candidate desta versão.");
						}
					}else {
						messages.error("Não foi identificada uma versão afetada, por favor, valide as informações da issue.");
					}
				}else {
					messages.error("O projeto atual relacionado, não implementa o gitflow.");
				}
				if(messages.hasSomeError()) {
					// tramita para o impedmento, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_IMPEDIMENTO);
				}else {
					if(!tagReleaseJaExistente || !releaseBranchJaExistente) {
						publicarMensagemLancamentoVersao(releaseCandidateModel);

						JiraMarkdown jiraMarkdown = new JiraMarkdown();
						messages.info(releaseCandidateModel.convert(jiraMarkdown));
					}
					// tramita automaticamente, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.atualizarVersaoASerLancada(issue, versaoASerLancada, updateFields);
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_CONCLUIR_RC);
				}
			}
		}
	}
	
	private void publicarMensagemLancamentoVersao(ReleaseCandidateTextModel modelo) {
		TelegramMarkdownHtml telegramMarkdown = new TelegramMarkdownHtml();
		telegramService.sendBotMessageHtml(modelo.convert(telegramMarkdown));// TODO - mandar também para o canal oficial
		
		GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
		slackService.sendBotMessage(modelo.convert(gitlabMarkdown)); // TODO - criar markdown slack + rocketchat
	}
}