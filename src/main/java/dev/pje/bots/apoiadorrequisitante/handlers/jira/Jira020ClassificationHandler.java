package dev.pje.bots.apoiadorrequisitante.handlers.jira;

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
import com.devplatform.model.jira.request.JiraIssueCreateAndUpdate;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;

@Component
public class Jira020ClassificationHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(Jira020ClassificationHandler.class);

	@Autowired
	private JiraService jiraService;
	
	@Autowired
	private TelegramService telegramService;

	public void handle(JiraEventIssue jiraEventIssue) {
		telegramService.sendBotMessage("|JIRA||020||AREA-CONHECIMENTO| - " + jiraEventIssue.getIssue().getKey() + " - " + jiraEventIssue.getIssueEventTypeName().name());
		List<String> epicThemeList = jiraEventIssue.getIssue().getFields().getEpicTheme();
		List<String> superEpicThemeList = jiraService.findSuperEpicTheme(epicThemeList);

		atualizaAreasConhecimento(jiraEventIssue.getIssue(), superEpicThemeList);
	}
	
	private void atualizaAreasConhecimento(JiraIssue issue, List<String> areasConhecimento) {
		Map<String, Object> updateFields = null;
		updateFields = new HashMap<>();
		try {
			jiraService.atualizarAreasConhecimento(issue, areasConhecimento, updateFields);
			if(!updateFields.isEmpty()) {
				JiraIssueTransition edicaoAvancada = jiraService.findTransitionByName(issue, JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA);
				if(edicaoAvancada != null) {
					JiraIssueCreateAndUpdate jiraIssueCreateAndUpdate = new JiraIssueCreateAndUpdate();
					jiraIssueCreateAndUpdate.setTransition(edicaoAvancada);
					jiraIssueCreateAndUpdate.setUpdate(updateFields);

					jiraService.updateIssue(issue, jiraIssueCreateAndUpdate);
					telegramService.sendBotMessage("|JIRA||020||AREA-CONHECIMENTO|[" + issue.getKey() + "] Issue atualizada");
					logger.info("Issue atualizada");
				}else {
					telegramService.sendBotMessage("|JIRA||020||AREA-CONHECIMENTO|[" + issue.getKey() + "] Erro!!* \n Não há transição para realizar esta alteração");
					logger.error("Não há transição para realizar esta alteração");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}