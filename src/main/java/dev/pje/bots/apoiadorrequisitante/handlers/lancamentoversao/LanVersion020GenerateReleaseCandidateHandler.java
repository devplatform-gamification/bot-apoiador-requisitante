package dev.pje.bots.apoiadorrequisitante.handlers.lancamentoversao;

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

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.GitlabMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.RocketchatMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.SlackMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.TelegramMarkdownHtml;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.ReleaseCandidateTextModel;

@Component
public class LanVersion020GenerateReleaseCandidateHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion020GenerateReleaseCandidateHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||020||RELEASE CANDIDATE|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	@Autowired
	private ReleaseCandidateTextModel releaseCandidateModel;

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
									messages.info("Branch criada: " + nomeReleaseBranch + " no projeto: " + gitlabProjectId);
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
					enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_IMPEDIMENTO, true, true);
				}else {
					if(!tagReleaseJaExistente || !releaseBranchJaExistente) {
						Boolean comunicarLancamentoVersaoCanaisOficiais = false;
						if(issue.getFields().getComunicarLancamentoVersao() != null 
								&& !issue.getFields().getComunicarLancamentoVersao().isEmpty()) {
							
							if(issue.getFields().getComunicarLancamentoVersao().get(0).getValue().equalsIgnoreCase("Sim")) {
								comunicarLancamentoVersaoCanaisOficiais = true;
							}
						}
						comunicarLancamentoVersao(issue, comunicarLancamentoVersaoCanaisOficiais);

						JiraMarkdown jiraMarkdown = new JiraMarkdown();
						messages.info(releaseCandidateModel.convert(jiraMarkdown));
					}
					// tramita automaticamente, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.atualizarVersaoASerLancada(issue, versaoASerLancada, updateFields);
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_SAIDA_PADRAO, true, true);
				}
			}
		}
	}
	
	private void comunicarLancamentoVersao(JiraIssue issue, Boolean comunicarCanaisOficiais) {
		String mensagemRoketchat = null;
		String mensagemSlack = null;
		String mensagemTelegram = null;
		if(issue != null && issue.getFields() != null) {
			mensagemRoketchat = issue.getFields().getMensagemRocketchat();
			mensagemSlack = issue.getFields().getMensagemSlack();
			mensagemTelegram = issue.getFields().getMensagemTelegram();
		}
		
		if(StringUtils.isBlank(mensagemRoketchat)) {
			mensagemRoketchat = gerarMensagemRocketchat();
		}
		if(StringUtils.isBlank(mensagemSlack)) {
			mensagemSlack = gerarMensagemSlack();
		}
		if(StringUtils.isBlank(mensagemTelegram)) {
			mensagemTelegram = gerarMensagemTelegram();
		}

		if(StringUtils.isNotBlank(mensagemRoketchat)) {
			rocketchatService.sendBotMessage(mensagemRoketchat);
			if(comunicarCanaisOficiais) {
				rocketchatService.sendMessagePJENews(mensagemRoketchat, false);
				rocketchatService.sendMessageGrupoRevisorTecnico(mensagemRoketchat);
				rocketchatService.sendMessageGrupoNegocial(mensagemRoketchat);
			}
		}
		if(StringUtils.isNotBlank(mensagemSlack)) {
			slackService.sendBotMessage(mensagemSlack);
			if(comunicarCanaisOficiais) {
				slackService.sendMessagePJENews(mensagemSlack);
				slackService.sendMessageGrupoRevisorTecnico(mensagemSlack);
				slackService.sendMessageGrupoNegocial(mensagemSlack);
			}
		}
		if(StringUtils.isNotBlank(mensagemTelegram)) {
			telegramService.sendBotMessageHtml(mensagemTelegram);
			if(comunicarCanaisOficiais) {
				telegramService.sendMessageGeralHtml(mensagemTelegram);
			}
		}
	}
	
	private String gerarMensagemTelegram() {
		TelegramMarkdownHtml telegramMarkdown = new TelegramMarkdownHtml();

		String versionReleasedNews = releaseCandidateModel.convert(telegramMarkdown);

		return versionReleasedNews;
	}

	private String gerarMensagemRocketchat() {
		RocketchatMarkdown rocketchatMarkdown = new RocketchatMarkdown();

		String versionReleasedSimpleCallRocket = releaseCandidateModel.convert(rocketchatMarkdown);

		return versionReleasedSimpleCallRocket;
	}

	private String gerarMensagemSlack() {
		SlackMarkdown slackMarkdown = new SlackMarkdown();

		String versionReleasedSimpleCallSlack = releaseCandidateModel.convert(slackMarkdown);

		return versionReleasedSimpleCallSlack;
	}
}