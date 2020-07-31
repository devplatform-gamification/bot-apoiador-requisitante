package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.request.JiraIssueFieldsRequest;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.SlackService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Component
abstract class Handler<E> {

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
	
	abstract void handle(E event) throws Exception;
	
	protected void enviarAlteracaoJira(JiraIssue issue, Map<String, Object> updateFields, String transictionId) throws JsonProcessingException{
		if(!updateFields.isEmpty()) {
			JiraIssueTransition transition = jiraService.findTransitionById(issue, transictionId);
			if(transition != null) {
				JiraIssueTransitionUpdate issueTransitionUpdate = new JiraIssueTransitionUpdate(transition, updateFields);
				messages.debug("update string: " + Utils.convertObjectToJson(issueTransitionUpdate));
				jiraService.updateIssue(issue, issueTransitionUpdate);
				messages.info("Issue atualizada");
			}else {
				messages.error("Não há transição para realizar esta alteração");
			}
		}
	}

	protected void enviarCriacaoJiraIssue(Map<String, Object> issueFields) throws JsonProcessingException{
		JiraIssue issue = null;
		if(issueFields != null && !issueFields.isEmpty()) {
			JiraIssueFieldsRequest createIssueDefaultFields = new JiraIssueFieldsRequest(issueFields);
			messages.debug("update string: " + Utils.convertObjectToJson(createIssueDefaultFields));
			issue = jiraService.createIssue(createIssueDefaultFields);
		}
		if(issue != null) {
			messages.info("Issue criada: " + issue.getKey());
		}else {
			messages.error("Não foi criada a nova issue");
		}
	}

}