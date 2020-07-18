package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.ArrayList;
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
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

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
						// TODO
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
									// 2.0 se utilizar: (se não utilizar, ir para o passo: 2.3)
									// 2.1 busca qual é o branch ativo da release candidate
									// 2.2 merge do branch da release no branch master
									// 2.3 altera a versao do pom, indicando a versão do arquivo de release criado
									// na issue de lancamento de versao
									// 2.4 fazer último commit da versao com o número da issue de lancamento de
									// versao
									// 2.5 criar tag relacionada à versao que está sendo lançada
									// 2.6 lancar mensagem de log: comentário na issue + telegram, indicando que a
									// tag foi lançada *não é a mensagem oficial de lancamento da versao

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

	private void finalizaVersaoGitlab(Boolean implementsGitflow, JiraVersionReleaseNotes releaseNotes) {
		String gitlabProjectId = releaseNotes.getGitlabProjectId();
		List<String> pomsList = getListPathPoms(gitlabProjectId);
		String commitMessage = "[" + releaseNotes.getIssueKey() + "] Finalizando a versão "
				+ releaseNotes.getVersion();
		if (implementsGitflow) {
			String branchReleaseName = GitlabService.BRANCH_RELEASE_CANDIDATE_PREFIX + releaseNotes.getVersion();
			// TODO - criar uma funcao na service para a qual se passe o branch alvo e o
			GitlabCommitResponse response = atualizaVersaoPom(gitlabProjectId, branchReleaseName, pomsList, releaseNotes, commitMessage);
			// número da versão pretendida e ele altera onde precisa no POM e nos arquivos
			// de script da versao se for necessário
			realizaPassosGitflowFechamentoVersao(gitlabProjectId, branchReleaseName, releaseNotes);
		} else {
			// 2.3 altera a versao do pom, indicando a versão do arquivo de release criado
			// na issue de lancamento de versao
			String branchName = GitlabService.BRANCH_MASTER;
			GitlabCommitResponse response = atualizaVersaoPom(gitlabProjectId, branchName, pomsList, releaseNotes, commitMessage);
		}
		// 2.5 criar tag relacionada à versao que está sendo lançada
		// TODO - retirar no pipeline da versao atual o envio de mensagem no
		// slack/telegram, isso será feito por um bot específico para termos mais
		// controle da informacao
		// 2.6 lancar mensagem de log: comentário na issue + telegram, indicando que a
		// tag foi lançada *não é a mensagem oficial de lancamento da versao
	}

	private void realizaPassosGitflowFechamentoVersao(String projectId, String branchName,
			JiraVersionReleaseNotes releaseNotes) {
		// 2.1 busca qual é o branch ativo da release candidate
		String releaseBranch = gitlabService.getActualReleaseBranch(releaseNotes.getGitlabProjectId());
		if (StringUtils.isNotBlank(releaseBranch) && releaseBranch.equals(branchName)) {
			// 2.2 merge do branch da release no branch master
			String commitMessage = "[" + releaseNotes.getIssueKey() + "] Integrando o branch " + branchName;
			gitlabService.mergeBranchReleaseIntoMaster(projectId, branchName, commitMessage);
			// TODO - como o merge é assíncrono, tem que ver o que fazer neste ponto aqui
			// para aguardar o processo ser finalizado
		} else {
			String errorMsg = MESSAGE_PREFIX + " - Erro! não conseguiu encontrar o release branch do projeto: "
					+ releaseNotes.getProject();
			logger.error(errorMsg);
			telegramService.sendBotMessage(errorMsg);
		}
	}

	private GitlabCommitResponse atualizaVersaoPom(String gitlabProjectId, String branchName, List<String> pomsList, 
			JiraVersionReleaseNotes releaseNotes, String commitMessage) {
		GitlabCommitResponse response = null;
		
		// 2.3 alterar a versao do pom, indicando a versão do arquivo de release
		// criado na issue de lancamento de versao
		if (pomsList != null && !pomsList.isEmpty()) {
			Map<String, String> poms = new HashMap<>();
			String actualVersion = null;
			String newVersion = releaseNotes.getVersion();
			for (String pomFilePath : pomsList) {
				String pomContent = gitlabService.getRawFile(gitlabProjectId, pomFilePath, branchName);
				if (StringUtils.isNotBlank(pomContent)) {
					if (StringUtils.isBlank(actualVersion)) {
						actualVersion = Utils.getVersionFromPomXML(pomContent);
					}
					String pomContentChanged = Utils.changePomXMLVersion(actualVersion, newVersion, pomContent);
					poms.put(pomFilePath, pomContentChanged);
				} else {
					String msgError = MESSAGE_PREFIX + " Error trying to get pom (" + pomFilePath + ") content.";
					logger.error(msgError);
					telegramService.sendBotMessage(msgError);
				}
			}
			// TODO - antes de seguir, verificar se a pasta de scripts precisa ser renomeada
			if (poms != null && poms.size() > 0 && poms.size() == pomsList.size()) {
				response = gitlabService.sendTextAsFileToBranch(gitlabProjectId, branchName,
						poms, commitMessage);
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

}