package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.jira.JiraEventChangelogItems;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraVersionReleaseNotes;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;
import dev.pje.bots.apoiadorrequisitante.utils.ReleaseNotesConverter;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.GitlabMarkdown;

@Component
public class JiraEventHandlerVersionTag {

	private static final Logger logger = LoggerFactory.getLogger(JiraEventHandlerVersionTag.class);

	@Autowired
	private JiraService jiraService;

	@Autowired
	private GitlabService gitlabService;

	@Autowired
	private TelegramService telegramService;
	
	@Autowired
	private ReleaseNotesConverter releaseNotesConverter;
	
	@Autowired
	private GitlabEventHandlerPublishReleaseNotes gitlabEventHandlerPublishReleaseNotes;

	public static final String MESSAGE_PREFIX = "[VERSION-LAUNCH][GITLAB]";

	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if (issue.getFields() != null && issue.getFields().getIssuetype() != null
					&& issue.getFields().getIssuetype().getId() != null && JiraService.ISSUE_TYPE_NEW_VERSION.toString()
							.equals(issue.getFields().getIssuetype().getId().toString())) {
				// 2. verifica se a issue tramitou para a situacao "confirmar" release notes
				boolean releaseNotesConfirmed = false;
				if (jiraEventIssue.getChangelog() != null) {
					List<JiraEventChangelogItems> changeItems = jiraEventIssue.getChangelog().getItems();
					for (JiraEventChangelogItems changedItem : changeItems) {
						if (changedItem != null && JiraService.FIELD_STATUS.equals(changedItem.getField())) {
							if (JiraService.STATUS_RELEASE_NOTES_CONFIRMED_ID.toString().equals(changedItem.getTo())) {
								releaseNotesConfirmed = true;
								break;
							}
						}
					}
				}
				if (releaseNotesConfirmed) {
					// 3. estamos no caminho correto, indicar no log
					telegramService.sendBotMessage(MESSAGE_PREFIX + " - " + issue.getKey() + " - "
							+ jiraEventIssue.getIssueEventTypeName().name());
					// 4.- a pessoa que atuou na issue está dentro do grupo de pessoas que podem
					// atuar?
					if (jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
						// 1. Recupera o anexo com o json do releaseNotes
						String releaseNotesAttachment = jiraService.getAttachmentContent(issue,
								JiraService.RELEASE_NOTES_JSON_FILENAME);
						if (releaseNotesAttachment != null) {
							JiraVersionReleaseNotes releaseNotes = Utils
									.convertJsonToJiraReleaseNotes(releaseNotesAttachment);

							if (releaseNotes != null) {
								releaseNotes.setGitlabProjectId("572"); // FIXME - retirar isso daqui, é só para testes
								releaseNotes.setReleaseDate(null); // FIXME - retirar isso daqui, é só para testes
								if (StringUtils.isNotBlank(releaseNotes.getReleaseDate())) {
									// TODO - gambiarra, o ideal é ter outro consumer que identifica que a issue foi
									// tramitada para o status correto e chame esse outro handler
									gitlabEventHandlerPublishReleaseNotes.handleReleaseNotesCreation(issue,
											releaseNotes);
								} else {
									// 1. identifica se o projeto utiliza ou não o gitflow - branch principal do
									// projeto
									Boolean implementsGitflow = false;
									if (StringUtils.isNotBlank(releaseNotes.getGitlabProjectId())) {
										implementsGitflow = gitlabService
												.isProjectImplementsGitflow(releaseNotes.getGitlabProjectId());
									}
									finalizaVersaoGitlab(implementsGitflow, releaseNotes);
									// 2.6 lancar mensagem de log: comentário na issue + telegram, indicando que a
									// tag foi lançada *não é a mensagem oficial de lancamento da versao

									// >>> escultar o evento de tag lançada e:
									// 3. Fazer preparativos para a próxima versão:
									// 3.1 se utilizar gitflow:
									// 3.2 atualizar versao do pom do branch develop (para o nome da próxima versao
									// indicada no arquivo release na issue de lancamento de versao) indicando
									// SNAPSHOT
									// 3.2.1 atualizar nome + scripts da pasta de scripts da versao (se for o caso
									// de alteracao)
									// 3.3 criar tag da release candidate
									// 3.4 criar branch da release candidate
									// 3.5 criar pasta de script + script inicial da próxima versao
									// 3.6 lancar mensagem: comentário na issue + telegram, indicando que a nova
									// versao foi iniciada

									// 3.10 se não utilizar gitflow
									// 3.11 atualizar a versao do pom do branch master, para o nome da próxima
									// versao indicada no arquivo de release da issue de lancamento de versao)
									// indicando SNAPSHOT
									// 3.12 criar pasta de scripts + script inicial da próxima versao
									// 3.13 lancar mensagem: comentário na issue + telegram, indicando que a nova
									// versao foi iniciada

									/**
									 * TODO - este handler fará apenas o lançamento da TAG correspondente +
									 * preparativos para a próxima versão - outros consumers farão o tratamento pós
									 * lançamento: -- 1. gerar / lançar release notes no projeto docs.pje >> ao
									 * final, lança evento no rabbit indicando esse lançamento -- 2. gerar / lançar
									 * release notes no gitlab, na própria tag -- 3. fazer alterações relacionadas
									 * ao lançamento de versão no jira -- outros consumers farão o tratamento pós
									 * lançamento de release notes --- 1. comunicar via telegram --- 2. comunicar
									 * via rocketchat --- 3. comunicar via slack
									 */
									// 1. gerar tag no gitlab
									// 2. fazer alteracoes relacionadas à tag

									// TODO - fazem parte de outros consumers
									// 4. Fazer alterações relacionadas ao gitlab
									// 5. Fazer alterações relacionadas ao jira
									// 6. gerar informações para a próxima versão no gitlab
									// 7. fazer comunicação de que a nova versão foi disponibilizada
//							throw new Exception("erro para os testes continuarem!!!");
								}
							}
						}
					}
				}
			}
//		List<String> epicThemeList = jiraEventIssue.getIssue().getFields().getEpicTheme();
//		List<String> superEpicThemeList = jiraService.findSuperEpicTheme(epicThemeList);
//
//		atualizaAreasConhecimento(jiraEventIssue.getIssue(), superEpicThemeList);
		}
	}

	/**
	 * Finaliza a versao:
	 * - se estiver usando o gitflow:
	 * -- atualiza o POM do branch release para a nova versao
	 * -- atualiza número da pasta de scripts/+scripts se for necessário ainda no branch release
	 * -- faz o merge do branch release com o branch master
	 * - se NAO estiver usando o gitflow:
	 * -- atualiza o POM do branch master
	 * -- atualiza número da pasta de scripts/+scripts se for necessario, no branch master
	 * - gera a TAG da versao a partir do branch master
	 * @param implementsGitflow
	 * @param releaseNotes
	 */
	private void finalizaVersaoGitlab(Boolean implementsGitflow, JiraVersionReleaseNotes releaseNotes) {
		String gitlabProjectId = releaseNotes.getGitlabProjectId();
		List<String> pomsList = getListPathPoms(gitlabProjectId);
		String commitMessage = "[" + releaseNotes.getIssueKey() + "] Finalizando a versão "
				+ releaseNotes.getVersion();

		if (implementsGitflow) {
			String branchReleaseName = GitlabService.BRANCH_RELEASE_CANDIDATE_PREFIX + releaseNotes.getVersion();
			String actualVersion = getActualVersion(gitlabProjectId, branchReleaseName);
			GitlabCommitResponse pomResponse = atualizaVersaoPom(gitlabProjectId, branchReleaseName, pomsList, releaseNotes, actualVersion, commitMessage);
			if(pomResponse != null) {
				atualizaNumeracaoPastaScriptsVersao(gitlabProjectId, branchReleaseName, pomResponse.getId(), releaseNotes, actualVersion);
			}
			realizaPassosGitflowFechamentoVersao(gitlabProjectId, branchReleaseName, releaseNotes);
		} else {
			String branchName = GitlabService.BRANCH_MASTER;
			String actualVersion = getActualVersion(gitlabProjectId, branchName);
			GitlabCommitResponse pomResponse = atualizaVersaoPom(gitlabProjectId, branchName, pomsList, releaseNotes, actualVersion, commitMessage);
			if(pomResponse != null) {
				atualizaNumeracaoPastaScriptsVersao(gitlabProjectId, branchName, pomResponse.getId(), releaseNotes, actualVersion);
			}
		}
		// 2.5 criar tag relacionada à versao que está sendo lançada
		String tagMessage = "[" + releaseNotes.getIssueKey() + "] Lançamento da versão "
				+ releaseNotes.getVersion();

		GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
		if(StringUtils.isBlank(releaseNotes.getReleaseDate())) {
			String dateStr = Utils.dateToStringPattern(new Date(), JiraService.JIRA_DATETIME_PATTERN);
			releaseNotes.setReleaseDate(dateStr);
		}
		String releaseText = releaseNotesConverter.convert(releaseNotes, gitlabMarkdown);
		gitlabService.createVersionTag(gitlabProjectId, releaseNotes.getVersion(), GitlabService.BRANCH_MASTER,
				tagMessage, releaseText);
		// TODO - retirar no pipeline da versao atual o envio de mensagem no
		// slack/telegram, isso será feito por um bot específico para termos mais
		// controle da informacao
		// 2.6 lancar mensagem de log: comentário na issue + telegram, indicando que a
		// tag foi lançada *não é a mensagem oficial de lancamento da versao
	}

	private void realizaPassosGitflowFechamentoVersao(String projectId, String branchName,
			JiraVersionReleaseNotes releaseNotes) {
		// 2.1 busca qual é o branch ativo da release candidate
		String gitProjectId = releaseNotes.getGitlabProjectId();
		String releaseBranch = gitlabService.getActualReleaseBranch(gitProjectId);
		if (StringUtils.isNotBlank(releaseBranch) && releaseBranch.equals(branchName)) {
			// 2.2 merge do branch da release no branch master
			String commitMessage = "[" + releaseNotes.getIssueKey() + "] Integrando o branch " + branchName;
			gitlabService.mergeBranchReleaseIntoMaster(projectId, branchName, commitMessage);
		} else {
			String errorMsg = MESSAGE_PREFIX + " - Erro! não conseguiu encontrar o release branch do projeto: "
					+ releaseNotes.getProject();
			logger.error(errorMsg);
			telegramService.sendBotMessage(errorMsg);
		}
	}
	
	private String getActualVersion(String gitlabProjectId, String branchName) {
		String defaultPomFile = "pom.xml";
		String actualVersion = null;
		String pomContent = gitlabService.getRawFile(gitlabProjectId, defaultPomFile, branchName);
		if (StringUtils.isNotBlank(pomContent)) {
			actualVersion = Utils.getVersionFromPomXML(pomContent);
		}
		
		return actualVersion;
	}

	private GitlabCommitResponse atualizaVersaoPom(String gitlabProjectId, String branchName, List<String> pomsList, 
			JiraVersionReleaseNotes releaseNotes, String actualVersion, String commitMessage) {
		GitlabCommitResponse response = null;
		
		if(releaseNotes != null && StringUtils.isNotBlank(releaseNotes.getVersion()) && StringUtils.isNotBlank(actualVersion) && !actualVersion.equalsIgnoreCase(releaseNotes.getVersion())) {
			if (pomsList != null && !pomsList.isEmpty()) {
				Map<String, String> poms = new HashMap<>();
				String newVersion = releaseNotes.getVersion();
				for (String pomFilePath : pomsList) {
					String pomContent = gitlabService.getRawFile(gitlabProjectId, pomFilePath, branchName);
					if (StringUtils.isNotBlank(pomContent)) {
						String pomContentChanged = Utils.changePomXMLVersion(actualVersion, newVersion, pomContent);
						poms.put(pomFilePath, pomContentChanged);
					} else {
						String msgError = MESSAGE_PREFIX + " Error trying to get pom (" + pomFilePath + ") content.";
						logger.error(msgError);
						telegramService.sendBotMessage(msgError);
					}
				}
				if (poms != null && poms.size() > 0 && poms.size() == pomsList.size()) {
					response = gitlabService.sendTextAsFileToBranch(gitlabProjectId, branchName,
							poms, commitMessage);
				}
			}
		}
		return response;
	}
	
	private List<String> getListPathPoms(String projectId) {
		List<String> listaPoms = new ArrayList<String>();
		if ("7".equals(projectId)) { // TODO - colocar isso na parametrizacao do projeto no gitlab
			listaPoms.add("pom.xml");
			listaPoms.add("pje-comum/pom.xml");
			listaPoms.add("pje-web/pom.xml");
		} else {
			listaPoms.add("pom.xml"); // FIXME - verificar para os outros projetos
		}
		return listaPoms;
	}
	
	private void atualizaNumeracaoPastaScriptsVersao(String projectId, String branchName, String lastCommitId, JiraVersionReleaseNotes releaseNotes, String actualVersion) {
		if(releaseNotes != null && StringUtils.isNotBlank(releaseNotes.getVersion()) && StringUtils.isNotBlank(actualVersion) && !actualVersion.equalsIgnoreCase(releaseNotes.getVersion())) {
			actualVersion = actualVersion.replaceAll("\\-SNAPSHOT", "");
			String previousPath = GitlabService.SCRIPS_MIGRATION_BASE_PATH + actualVersion;
			String newPath = GitlabService.SCRIPS_MIGRATION_BASE_PATH + releaseNotes.getVersion();

			String commitMessage = "[" + releaseNotes.getIssueKey() + "] Renomeando pasta de scripts";
			gitlabService.renameDir(projectId, branchName, lastCommitId, previousPath, newPath, commitMessage);
		}
	}

}