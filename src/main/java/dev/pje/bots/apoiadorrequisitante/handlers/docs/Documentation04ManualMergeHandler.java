package dev.pje.bots.apoiadorrequisitante.handlers.docs;

import java.math.BigDecimal;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabMergeRequestStateEnum;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.GitlabUtils;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;

@Component
public class Documentation04ManualMergeHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(Documentation04ManualMergeHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	public String getMessagePrefix() {
		return "|DOCUMENTATION||04||MANUAL-MERGE|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	private static final boolean APROVAR_MR_NA_TORA = true; // FIXME - retirar quando colocar em produção
	/**
	 * Aprova todos os merges abertos da issue atual 
	 * - a tramitação da issue será feita por outro handler genérico que monitore os merges
	 */
	@Override
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			if(	(
					JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_DOCUMENTATION)
					|| JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES)
					) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_DOCUMENTATION_MANUAL_INTEGRATION_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_DOCUMENTATION_MANUAL_INTEGRATION_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());
				issue = jiraService.recuperaIssueDetalhada(issue.getKey());

				if(issue.getFields() != null && issue.getFields().getProject() != null) {

					String jiraProjectKey = issue.getFields().getProject().getKey();
					if(StringUtils.isBlank(jiraProjectKey)) {
						messages.error("Não foi possível identificar a chave do projeto atual");
					}else {
						String gitlabProjectId = jiraService.getGitlabProjectFromJiraProjectKey(jiraProjectKey);
						if(StringUtils.isBlank(gitlabProjectId)) {
							messages.error("Não foi possível identificar qual é o repositório no gitlab para este projeto do jira.");
						}else {
							String MRsAbertos = issue.getFields().getMrAbertos();
							if(StringUtils.isNotBlank(MRsAbertos)) {
								List<String> mergeIIds = GitlabUtils.getMergeIIdListFromString(MRsAbertos);
								for (String mrIId : mergeIIds) {
									BigDecimal mrIIdBD = new BigDecimal(mrIId);
									GitlabMRResponse mrCandidate = gitlabService.getMergeRequest(gitlabProjectId, mrIIdBD);
									if(mrCandidate.getState().equals(GitlabMergeRequestStateEnum.OPENED)) {
										messages.info("Aprovando o MR: "+ mrIId + " - do projeto: " + gitlabProjectId);
										GitlabMRResponse response = null;
										if(APROVAR_MR_NA_TORA) {
											response = gitlabService.acceptMergeRequest(gitlabProjectId, mrIIdBD);
											if(response == null) {
												messages.error("Falhou ao tentar aprovar o MR: "+ mrIId + " - do projeto: " + gitlabProjectId);
											}
										}
									}else {
										messages.info("Ignorando o MR: " + mrIId + " - do projeto: " + gitlabProjectId + " - sua situação é: " + mrCandidate.getState().toString());
									}
								}
							}else {
								messages.error("Não foi possível identificar nenhum MR aberto para a issue atual.");
							}
						}
					}
				}
				jiraService.sendTextAsComment(issue, messages.getMessagesToJira());
			}
		}
	}
}