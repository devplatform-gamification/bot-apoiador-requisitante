package dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.event.GitlabEventPushTag;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTipoVersaoEnum;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.rocketchat.RocketchatUser;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.RocketchatMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.TagAddedOrRemovedTextModel;

@Component
public class LanVersion070TagPushedEventHandler extends Handler<GitlabEventPushTag>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion070TagPushedEventHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||070||TAG-PUSHED|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	@Autowired
	private TagAddedOrRemovedTextModel tagAddedOrRemovedTextModel;
	
	private static final String TRANSITION_PROPERTY_KEY_GERAR_RELEASE_NOTES = "GERAR_RELEASE_NOTES";
	private static final String TRANSITION_PROPERTY_KEY_REABRIR_DEMANDA = "REABRIR_DEMANDA";

	/**
	 * :: TAG pushed :: (tag criada ou removida)
	 *    ok- verifica se a tag é de uma release candidate:
	 *    ok-- se for, ignora, não precisa fazer nada
	 *    ok-- se não for, continua
	 *    Verifica se há issue relacionada
	 *    ok- se não houver
	 *    ok-- tenta identificar se trata-se de versão hotfix ou versão ordinária
	 *    ok-- cria a nova issue
	 *    ok--- cria a versão associada à tag em todos os projetos relacionados
	 *    ok--- se a tag foi criada: >> o próprio fluxo já verificará se a tag foi lançada e encaminhará para a geração do release notes
	 *    ok--- se a tag foi removida >> a issue será encaminhada para a preparação da versão (no git), se a versão já estiver ok, gerará o release notes
	 *    ok- se houver issue
	 *    ok-- se a tag foi criada:
	 *    ok--- tenta identificar uma transição para "gerar/regerar" release notes
	 *    ok-- se a tag foi removida:
	 *    ok--- tenta identificar uma transição para "reabrir a issue"
	 *    ok- cria um comentário com o nome do usuário que criou/removeu a tag para confirmar o release notes
	 *    ok-- se o número do POM não bater com o número da versão da TAG, lança um comentário para que a pessoa verifique (mas não indica como impedimento)
	 *    - publica indica a issue ao autor via rocketchat
	 *    - publica no grupo XXX(grupo revisor negocial e revisor técnico) do rocketchat
	 */
	public void handle(GitlabEventPushTag gitlabEventPushTag) throws Exception {
		messages.clean();
		if (gitlabEventPushTag != null && gitlabEventPushTag.getProjectId() != null) {

			String gitlabProjectId = gitlabEventPushTag.getProjectId().toString();
			Boolean isTagReleaseCandidate = false;
			if(StringUtils.isNotBlank(gitlabEventPushTag.getRef()) 
					&& gitlabEventPushTag.getRef().endsWith(GitlabService.TAG_RELEASE_CANDIDATE_SUFFIX)) {
				// para confirmar, verifica se o projeto implementa o gitflow
				isTagReleaseCandidate = gitlabService.isProjectImplementsGitflow(gitlabProjectId);
			}
			if(!isTagReleaseCandidate) {
				Boolean tagCreated = true;
				String referenceCommit = gitlabEventPushTag.getAfter();
				if(StringUtils.isNotBlank(referenceCommit)) {
					try {
						Integer afterHash = Integer.valueOf(referenceCommit);
						if(afterHash == 0) {
							tagCreated = false;
							referenceCommit = gitlabEventPushTag.getBefore();
						}
					}catch (Exception e) {
						if(e instanceof NumberFormatException) {
							messages.debug("O hash do commit " + referenceCommit + " parece válido");
						}
					}
				}
				if(StringUtils.isNotBlank(referenceCommit)) {
					String tagVersion = Utils.getFileNameFromFilePath(gitlabEventPushTag.getRef());
					String pomActualVersion = gitlabService.getActualVersion(gitlabProjectId, referenceCommit, true);
					if(StringUtils.isNotBlank(tagVersion) && StringUtils.isNotBlank(pomActualVersion)) {
						String actualVersion = Utils.clearVersionNumber(tagVersion);
						if(StringUtils.isNotBlank(actualVersion)) {
							// identifica qual é o projeto do jira no qual foi integrada a TAG
							String jiraProjectKey = gitlabService.getJiraRelatedProjectKey(gitlabProjectId);
							// verifica se existem outros projetos relacionados a esse do jira
							List<String> jiraRelatedProjectKeys = jiraService.getJiraRelatedProjects(jiraProjectKey);
							// buscar a issue de "lancamento de versão" dos projetos relacionados - se for criacao de tag: retorna as issues abertas, caso contrário, só as fechadas
							if(StringUtils.isNotBlank(jiraProjectKey)) {
								String projectKeys = String.join(", ", jiraRelatedProjectKeys);
								String jql = jiraService.getJqlIssuesLancamentoVersao(actualVersion, projectKeys, tagCreated);
								List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
								
								JiraIssue issueVersao = null;
								if(issues != null && !issues.isEmpty()) {
									// a issue já existe
									issueVersao = issues.get(0);
									/*	-- se a tag foi criada:
									 *  --- tenta identificar uma transição para "gerar/regerar" release notes
									 *  -- se a tag foi removida:
									 *  --- tenta identifiar uma transição para  "gerar/regerar" release notes
									 *  --- se não houver: tenta identificar uma transição para "reabrir a issue"
									 */
									String transitionPropertyKey = TRANSITION_PROPERTY_KEY_GERAR_RELEASE_NOTES;
									if(!tagCreated){
										JiraIssueTransition transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issueVersao, transitionPropertyKey);
										if(transition == null) {
											transitionPropertyKey = TRANSITION_PROPERTY_KEY_REABRIR_DEMANDA;
										}
									}
									enviarAlteracaoJira(issueVersao, null, null, transitionPropertyKey, false, false);
									
								}else {
									// é necessário criar a nova issue
									JiraIssue novaIssueLancamentoVersao = null;
									Map<String, Object> issueFields = new HashMap<>();
									Map<String, Object> issueUpdateFields = new HashMap<>();
									
									// add project
									jiraService.novaIssueCriarProject(jiraProjectKey, issueFields);
									// add issueType
									jiraService.novaIssueCriarIssueType(jiraService.getIssueTypeLancamentoVersao(), issueFields);
									// add summary
									jiraService.novaIssueCriarSummary("Lançamento da versão " + actualVersion, issueFields);
									jiraService.novaIssueCriarIndicacaoPrepararVersaoAutomaticamente(issueFields);
									// add versao afetada (version)
									JiraVersion versao = new JiraVersion();
									versao.setName(actualVersion);
									// verificar se já existe a versão no projeto relacionado, se não tiver criar a versão em todos os projetos relacionados
									jiraService.createVersionInRelatedProjects(jiraProjectKey, actualVersion);
									
									jiraService.novaIssueCriarAffectedVersion(versao, issueFields);
									// identificar se trata-se de uma versão ordinária ou não, tipicamente a hotfix será a que tiver apenas issues do tipo hotfix e nenhuma de outro tipo
									List<String> issueTypeArray = new ArrayList<String>();
									if(issueTypeArray != null) {
										issueTypeArray.add(JiraService.ISSUE_TYPE_HOTFIX);
									}
									String jqlCheckVersionHotfix = jiraService.getJqlIssuesFromFixVersionWithIssueTypes(actualVersion, false, jiraRelatedProjectKeys, issueTypeArray, false);
									List<JiraIssue> issuesVersionHotfix = jiraService.getIssuesFromJql(jqlCheckVersionHotfix);
									JiraIssueTipoVersaoEnum tipoVersao = JiraIssueTipoVersaoEnum.HOTFIX;
									if(issuesVersionHotfix != null && !issuesVersionHotfix.isEmpty()) {
										tipoVersao = JiraIssueTipoVersaoEnum.ORDINARIA;
									}
									
									// add tipo de versao (ordinaria/hotfix)
									jiraService.novaIssueCriarTipoVersao(tipoVersao, issueFields);
									novaIssueLancamentoVersao = enviarCriacaoJiraIssue(issueFields, issueUpdateFields);
									
									if(novaIssueLancamentoVersao != null) {
										messages.info("Issue de Lançamento de versão para a versão: " + actualVersion);
										issueVersao = novaIssueLancamentoVersao;
									}else {
										messages.error("Não foi possível criar a issue de Lançamento de versão para a versão: " + actualVersion + " no projeto: " + jiraProjectKey);
									}
								}

								if(!messages.someError) {
									/**- cria um comentário com o nome do usuário que criou/removeu a tag para confirmar o release notes
									 */
									tagAddedOrRemovedTextModel.setIssueKey(issueVersao.getKey());
									tagAddedOrRemovedTextModel.setActualVersion(actualVersion);
									tagAddedOrRemovedTextModel.setPomActualVersion(pomActualVersion);
									tagAddedOrRemovedTextModel.setTagMessage(gitlabEventPushTag.getMessage());
									tagAddedOrRemovedTextModel.setTagCreated(tagCreated);

									String gitUserName = gitlabEventPushTag.getUserEmail();
									if(StringUtils.isBlank(gitUserName)) {
										gitUserName = gitlabEventPushTag.getUserUsername();
									}
									JiraUser usuarioCommiter = null;
									if(StringUtils.isNotBlank(gitUserName)) {
										usuarioCommiter = jiraService.findUserByUserName(gitUserName);
									}

									jiraService.sendTextAsComment(issueVersao, gerarMensagemTagPushedJira(usuarioCommiter));
									String rocketMessage = gerarMensagemTagPushedRocketchat(usuarioCommiter);
									if(StringUtils.isNotBlank(rocketMessage)) {
										// publica indicacao da issue ao autor via rocketchat
										if(usuarioCommiter != null) {
											RocketchatUser rocketUser = rocketchatService.findUser(usuarioCommiter.getEmailAddress());
											if(rocketUser != null) {
												String rocketUserName = rocketUser.getUsername();
												rocketchatService.sendMessageToUsername(rocketUserName, rocketMessage);
											}
										}
										// publica nos grupos grupo revisor negocial e revisor técnico do rocketchat
										rocketchatService.sendBotMessage(rocketMessage);
										rocketchatService.sendMessageGrupoNegocial(rocketMessage);
										rocketchatService.sendMessageGrupoRevisorTecnico(rocketMessage);
									}
								}
							}
						}
					}else {
						messages.error("Falhou ao tentar identificar a versão do projeto: " + gitlabProjectId + " no commit: "+ referenceCommit);
					}
				}
			}else {
				messages.info("A tag lançada é de uma release candidate (" + gitlabEventPushTag.getRef() + "), ignorando.");
			}
		}
	}
	
	private String gerarMensagemTagPushedJira(JiraUser jirauser) {
		JiraMarkdown jiraMarkdown = new JiraMarkdown();
		
		String userName = null;
		if(jirauser != null) {
			userName = jirauser.getName();
		}
		tagAddedOrRemovedTextModel.setUserName(userName);

		return tagAddedOrRemovedTextModel.convert(jiraMarkdown);
	}
	
	private String gerarMensagemTagPushedRocketchat(JiraUser jirauser) {
		RocketchatMarkdown rocketchatMarkdown = new RocketchatMarkdown();
		
		String userName = null;
		if(jirauser != null) {
			userName = jirauser.getName();
			RocketchatUser rocketUser = rocketchatService.findUser(jirauser.getEmailAddress());
			if(rocketUser != null) {
				userName = rocketUser.getUsername();
			}
		}
		tagAddedOrRemovedTextModel.setUserName(userName);

		return tagAddedOrRemovedTextModel.convert(rocketchatMarkdown);
	}

}