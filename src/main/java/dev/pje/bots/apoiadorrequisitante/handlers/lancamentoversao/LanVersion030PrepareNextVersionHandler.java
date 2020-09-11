package dev.pje.bots.apoiadorrequisitante.handlers.lancamentoversao;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.VersionTypeEnum;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;

@Component
public class LanVersion030PrepareNextVersionHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion030PrepareNextVersionHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||030||NEXT-VERSION|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	/**
	 * :: Preparacao para a proxima versao ::
	 *   Alterar o POM.XML do branch develop para a próxima versão 
	 *   Gerar a pasta de scripts da próxima versão
	 *   Inicializar os metadados da próxima versão no jira
	 * 
	 */
	@Override
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_NEW_VERSION) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_PREPARAR_PROXIMA_VERSAO_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_PREPARAR_PROXIMA_VERSAO_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());
				
				String versaoASerLancada = issue.getFields().getVersaoSeraLancada();
				String proximaVersao = issue.getFields().getProximaVersao();
				
				String gitlabProjectId = jiraService.getGitlabProjectFromIssue(issue);
				boolean implementsGitflow = false;
				if (StringUtils.isNotBlank(gitlabProjectId)) {
					implementsGitflow = gitlabService
							.isProjectImplementsGitflow(gitlabProjectId);
				}
				
				String branchDesenvolvimento = GitlabService.BRANCH_MASTER;
				if(implementsGitflow) {
					branchDesenvolvimento = GitlabService.BRANCH_DEVELOP;
					messages.info("O projeto atual implementa o gitflow.");
				}

				if(StringUtils.isNotBlank(versaoASerLancada)) {
					if(StringUtils.isBlank(proximaVersao)){
						proximaVersao = jiraService.calulateNextVersionNumber(issue.getFields().getProject().getKey(), versaoASerLancada);
					}
					
					// verifica se é necessário alterar o POM
					if(StringUtils.isNotBlank(proximaVersao)) {
						prepareVersion(issue.getKey(), gitlabProjectId, branchDesenvolvimento, proximaVersao, VersionTypeEnum.SNAPSHOT);
						// Inicializar os metadados da próxima versão no jira
						// identificar quais os projetos relacionados ao da issue, ex.: PJE: PJEII / PJEVII / PJELEG / para os demais usar o próprio projeto da issue
						jiraService.createVersionInRelatedProjects(issue.getFields().getProject().getKey(), proximaVersao);
						if(implementsGitflow) {
							// cria o "sprint do grupo" para a próxima versao
							String novaSprintDoGrupo = JiraUtils.getSprintDoGrupoName(proximaVersao);
							try {
								jiraService.createSprintDoGrupoOption(novaSprintDoGrupo);
								messages.info("Criada a sprint do grupo: " + novaSprintDoGrupo);
							}catch (Exception e) {
								messages.error("Não foi possível criar a sprint do grupo: " + novaSprintDoGrupo);
							}
						}
					}else {
						messages.error("Não foi possível identificar a próxima versão: (" + proximaVersao + ")");
					}
				}else {
					messages.error("Não foi identificada uma versão a ser lançada, por favor, valide as informações da issue.");
				}

				if(messages.hasSomeError()) {
					// tramita para o impedmento, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_IMPEDIMENTO, true, true);
				}else {
					// tramita automaticamente, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.atualizarProximaVersao(issue, proximaVersao, updateFields);
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_SAIDA_PADRAO, true, true);
				}
			}
		}
	}
	
	public String prepareVersion(String issueKey, String gitlabProjectId, String branchRef, String nextVersion, VersionTypeEnum versionType) {
		// atualiza a versão do POM
		String commitMessageAlteracaoPom = "[" + issueKey + "] Início da versão " + nextVersion;
		String versaoAtual = gitlabService.getActualVersion(gitlabProjectId, branchRef, false);
		String lastCommitId = null;
		try {
			lastCommitId = gitlabService.changePomVersion(gitlabProjectId, branchRef, versaoAtual, nextVersion
					, versionType, commitMessageAlteracaoPom, messages);
			if(StringUtils.isNotBlank(lastCommitId)) {
				messages.info("Atualizada a versão do POM.XML de: " + versaoAtual + " - para: " + nextVersion);
			}else {
				messages.info("O POM.XML já está atualizado");
			}
		}catch (Exception e) {
			messages.error("Falhou ao tentar atualizar o POM.XML de: " + versaoAtual + " - para: " + nextVersion);
		}
		// cria a pasta de scripts para a próxima versão - se o projeto precisar de scripts
		if(!messages.hasSomeError()) {
			String commitMessageCreateScriptDir = "[" + issueKey + "] Criacao da pasta de scripts da versao " + nextVersion;
			GitlabCommitResponse response = gitlabService.createScriptsDir(gitlabProjectId, branchRef, lastCommitId, 
					nextVersion, commitMessageCreateScriptDir);
			if(response != null) {
				messages.info("Criada pasta de scripts da versao " + nextVersion);
				lastCommitId = response.getId();
			}
		}
		return lastCommitId;
	}
}