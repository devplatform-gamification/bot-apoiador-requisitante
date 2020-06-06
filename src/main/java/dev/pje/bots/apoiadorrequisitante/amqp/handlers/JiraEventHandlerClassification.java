package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.SlackService;

@Component
public class JiraEventHandlerClassification {
	
	private static final Logger logger = LoggerFactory.getLogger(JiraEventHandlerClassification.class);

	@Autowired
	private JiraService jiraService;
	
	@Autowired
	private SlackService slackService;

	public void handle(JiraEventIssue jiraEventIssue) {
		slackService.sendBotMessage("[AREA-CONHECIMENTO][JIRA] - " + jiraEventIssue.getIssue().getKey() + " - " + jiraEventIssue.getIssueEventTypeName().name());
		List<String> epicThemeList = jiraEventIssue.getIssue().getFields().getEpicTheme();
		List<String> superEpicThemeList = jiraService.findSuperEpicTheme(epicThemeList);

		atualizaAreasConhecimento(jiraEventIssue.getIssue(), superEpicThemeList);
	}
	
	private void atualizaAreasConhecimento(JiraIssue issue, List<String> areasConhecimento) {
		Map<String, Object> updateFields = new HashMap<>();
		try {
			jiraService.atualizarAreasConhecimento(issue, areasConhecimento, updateFields);
			if(!updateFields.isEmpty()) {
				JiraIssueTransition edicaoAvancada = jiraService.findTransicao(issue, JiraService.TRANSICION_DEFAULT_EDICAO_AVANCADA);
				if(edicaoAvancada != null) {
					JiraIssueTransitionUpdate issueTransitionUpdate = new JiraIssueTransitionUpdate(edicaoAvancada, updateFields);
					jiraService.updateIssue(issue, issueTransitionUpdate);
					slackService.sendBotMessage("[AREA-CONHECIMENTO][" + issue.getKey() + "] Issue atualizada");
					logger.info("Issue atualizada");
				}else {
					slackService.sendBotMessage("*[AREA-CONHECIMENTO][" + issue.getKey() + "] Erro!!* \n Não há transição para realizar esta alteração");
					logger.error("Não há transição para realizar esta alteração");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}