package dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Component
public class LanVersion03PrepareNextVersionHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion03PrepareNextVersionHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||03||NEXT-VERSION|";
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
	 *   Migrar issues de sprints anteriores para a próxima sprint
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

					String versaoAtual = gitlabService.getActualVersion(gitlabProjectId, branchDesenvolvimento, false);
					String lastCommitId = null;
					// verifica se é necessário alterar o POM
					if(StringUtils.isNotBlank(versaoAtual) && StringUtils.isNotBlank(proximaVersao)) {
						if(!Utils.clearVersionNumber(versaoAtual).equalsIgnoreCase(proximaVersao)) {
							// é necessário alterar o POM
							String commitMessage = "[" + issue.getKey() + "] Início da versão " + proximaVersao;
							String proximaVersaoSNAPSHOT = proximaVersao + "-SNAPSHOT";
							GitlabCommitResponse response = gitlabService.atualizaVersaoPom(gitlabProjectId, branchDesenvolvimento, 
									proximaVersaoSNAPSHOT, versaoAtual, commitMessage);
							if(response != null) {
								messages.info("Atualizada a versão do POM.XML de: " + versaoAtual + " - para: " + proximaVersaoSNAPSHOT);
								lastCommitId = response.getId();
							}else {
								messages.error("Falhou ao tentar atualizar o POM.XML de: " + versaoAtual + " - para: " + proximaVersaoSNAPSHOT);
							}
						}else {
							messages.info("O POM.XML já está atualizado");
						}
						if(!messages.hasSomeError()) {
							// Gerar a pasta de scripts da próxima versão
							String commitMessage = "[" + issue.getKey() + "] Criacao da pasta de scripts da versao " + proximaVersao;
							GitlabCommitResponse response = gitlabService.createScriptsDir(gitlabProjectId, branchDesenvolvimento, lastCommitId, proximaVersao, commitMessage);
							if(response != null) {
								messages.info("Criada pasta de scripts da versao " + proximaVersao);
							}
							// Inicializar os metadados da próxima versão no jira
							// identificar quais os projetos relacionados ao da issue, ex.: PJE: PJEII / PJEVII / PJELEG / para os demais usar o próprio projeto da issue
							jiraService.createVersionInRelatedProjects(issue.getFields().getProject().getKey(), proximaVersao);
							// cria o "sprint do grupo" para a próxima versao
							String novaSprintDoGrupo = "Sprint " + proximaVersao;
							try {
								jiraService.createSprintDoGrupoOption(novaSprintDoGrupo);
								messages.info("Criada a sprint do grupo: " + novaSprintDoGrupo);
							}catch (Exception e) {
								messages.error("Não foi possível criar a sprint do grupo: " + novaSprintDoGrupo);
							}
							// migra as issues da sprint anterior para a nova sprint
						}
					}else {
						messages.error("Não foi possível identificar a versão atual (" + versaoAtual + ") ou a próxima versão: (" + proximaVersao + ")");
					}
				}else {
					messages.error("Não foi identificada uma versão a ser lançada, por favor, valide as informações da issue.");
				}

				if(messages.hasSomeError()) {
					// tramita para o impedmento, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, JiraService.TRANSITION_PROPERTY_KEY_IMPEDIMENTO, true, true);
				}else {
					// tramita automaticamente, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.atualizarProximaVersao(issue, proximaVersao, updateFields);
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, JiraService.TRANSITION_PROPERTY_KEY_SAIDA_PADRAO, true, true);
				}
			}
		}
	}
}