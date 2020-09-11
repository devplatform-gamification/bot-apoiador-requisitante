package dev.pje.bots.apoiadorrequisitante.handlers.lancamentoversao;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.VersionTypeEnum;
import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;

@Component
public class LanVersion015PrepareActualVersionHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion015PrepareActualVersionHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||015||ACTUAL-VERSION|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	private static final String TRANSITION_PROPERTY_KEY_PREPARAR_RELEASE_CANDIDATE = "GERAR_RELEASE_CANDIDATE";
	private static final String TRANSITION_PROPERTY_KEY_GERAR_RELEASE_NOTES = "GERAR_RELEASE_NOTES";

	/**
	 * :: Preparacao para a versao atual ::
	 * ok- verifica se a versão já foi lançada:
	 * ok-- se sim: comenta na issue e encaminha para "gerar release notes"
	 * ok-- se não: 
	 * 
	 * ok--- cria a "sprint do grupo" da versão atual
	 * --- NÃO FAZ (DEVE SER MANUAL) - migra issues de sprints anteriores (e que não estejam integradas na versão anterior) para a próxima sprint
	 * ok--- valida as versões do jira para os projetos associados
	 * 
	 * ok--- verifica se implementa gitflow:
	 * ok---- se sim: encaminha para o status de "gerar RC"
	 * ---- se não:
	 * ok----- identifica o branch padrão
	 * ok----- chama a função de preparação da versão (que tratará do POM.xml / da pasta de scripts / )
	 * 
	 */
	@Override
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_NEW_VERSION) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_PREPARAR_VERSAO_ATUAL_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_PREPARAR_VERSAO_ATUAL_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());
				
				String gitlabProjectId = jiraService.getGitlabProjectFromIssue(issue);
				boolean implementsGitflow = false;
				if (StringUtils.isNotBlank(gitlabProjectId)) {
					implementsGitflow = gitlabService
							.isProjectImplementsGitflow(gitlabProjectId);
				}
				
				String versaoASerLancada = issue.getFields().getVersaoSeraLancada();
				boolean versaoJaLancada = false;
				String propriedadeTransicao = null;
				if (jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
					// valida se foi indicada uma versão afetada - caso contrário fecha a issue
					String versaoAfetada = JiraUtils.getVersaoAfetada(issue.getFields().getVersions());
					if(StringUtils.isNotBlank(versaoAfetada)) {
						versaoASerLancada = issue.getFields().getVersaoSeraLancada();
						if(StringUtils.isBlank(versaoASerLancada)){
							versaoASerLancada = versaoAfetada;
						}
						// verifica se a versão a ser lançada já não foi lançada - identifica isso no gitlab, tentando identificar uma tag relacionada
						if(StringUtils.isNotBlank(gitlabProjectId)) {
							GitlabTag tag = gitlabService.getVersionTag(gitlabProjectId, versaoASerLancada);
							if(tag != null && tag.getCommit() != null) {
								versaoJaLancada = true;
								messages.info("A versão: " + versaoASerLancada + " já foi lançada");
							}
						}

						if(!versaoJaLancada) {
							if(implementsGitflow) {
								// verifica se a sprint do grupo da 'versão a ser lançada' já existe - se não, cria
								String sprintDoGrupoVersaoAtual = JiraUtils.getSprintDoGrupoName(versaoASerLancada);
								try {
									jiraService.createSprintDoGrupoOption(sprintDoGrupoVersaoAtual);
									messages.info("Criada a sprint do grupo: " + sprintDoGrupoVersaoAtual);
								}catch (Exception e) {
									messages.error("Não foi possível criar a sprint do grupo: " + sprintDoGrupoVersaoAtual);
								}
							}
							// valida as versões do jira para os projetos associados
							jiraService.createVersionInRelatedProjects(issue.getFields().getProject().getKey(), versaoASerLancada);
							if(implementsGitflow) {
								propriedadeTransicao = TRANSITION_PROPERTY_KEY_PREPARAR_RELEASE_CANDIDATE;
								messages.info("Este projeto implementa o gitflow e por isso, a release candidate "+ versaoASerLancada + " deve ser preparada.");
							}else {
								// não implementa gitflow, precisa então tratar do POM.XML e da pasta de scripts
								String branchDesenvolvimento = GitlabService.BRANCH_MASTER;
								prepareVersion(issue.getKey(), gitlabProjectId, branchDesenvolvimento, versaoASerLancada, VersionTypeEnum.SNAPSHOT);
								if(!messages.hasSomeError()) {
									propriedadeTransicao = TRANSITION_PROPERTY_KEY_GERAR_RELEASE_NOTES;
								}
							}
						}else {
							// tramita para "gerar release notes"
							propriedadeTransicao = TRANSITION_PROPERTY_KEY_GERAR_RELEASE_NOTES;
						}
					}else {
						messages.error("Não foi identificada uma versão afetada, ou há mais de uma indicação.");
					}
				}else {
					messages.error("O usuário [~" + jiraEventIssue.getUser().getName() + "] não tem permissão para criar esta issue.");
				}
				
				if(messages.hasSomeError() || StringUtils.isBlank(propriedadeTransicao)) {
					// tramita para o impedmento, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_IMPEDIMENTO, true, true);
				}else {
					// tramita automaticamente, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, null, propriedadeTransicao, true, true);
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