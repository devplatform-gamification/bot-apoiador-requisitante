package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.request.JiraIssueCreateAndUpdate;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.SlackService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Component
public abstract class Handler<E> {

	abstract protected Logger getLogger();
	
	abstract public String getMessagePrefix();

	abstract public int getLogLevel();

	@Autowired
	protected JiraService jiraService;

	@Autowired
	protected GitlabService gitlabService;

	@Autowired
	protected TelegramService telegramService;

	@Autowired
	protected SlackService slackService;

	protected MessagesLogger messages;
	
	@PostConstruct
	public void init() {
		messages = new MessagesLogger();
		messages.setLogger(getLogger());
		messages.setMessagePrefix(getMessagePrefix());
		messages.setLogLevel(getLogLevel());
	}
	
	public abstract void handle(E event) throws Exception;
	
	protected void enviarAlteracaoJira(JiraIssue issue, Map<String, Object> updateFields, String transictionId) throws Exception{
		if(!updateFields.isEmpty()) {
			JiraIssueCreateAndUpdate jiraIssueCreateAndUpdate = new JiraIssueCreateAndUpdate();
			jiraIssueCreateAndUpdate.setUpdate(updateFields);
			
			if(StringUtils.isNotBlank(transictionId)) {
				JiraIssueTransition transition = jiraService.findTransitionById(issue, transictionId);
				if(transition != null) {
					jiraIssueCreateAndUpdate.setTransition(transition);
				}else {
					messages.error("Não há transição para realizar esta alteração: " + transictionId);
				}
			}
			messages.debug("update string: " + Utils.convertObjectToJson(jiraIssueCreateAndUpdate));
			jiraService.updateIssue(issue, jiraIssueCreateAndUpdate);
			messages.info("Issue atualizada");
		}
	}

	protected JiraIssue enviarCriacaoJiraIssue(Map<String, Object> createFields, Map<String, Object> updateFields) throws Exception{
		JiraIssue issue = null;
		if(createFields != null && !createFields.isEmpty()) {
			JiraIssueCreateAndUpdate jiraIssueCreateAndUpdate = new JiraIssueCreateAndUpdate();
			jiraIssueCreateAndUpdate.setFields(createFields);
			jiraIssueCreateAndUpdate.setUpdate(updateFields);
			messages.debug("create issue string: " + Utils.convertObjectToJson(jiraIssueCreateAndUpdate));
			issue = jiraService.createIssue(jiraIssueCreateAndUpdate);
		}
		if(issue != null) {
			messages.info("Issue criada: " + issue.getKey());
		}else {
			messages.error("Não foi criada a nova issue");
		}
		return issue;
	}

}