package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueComment;
import com.devplatform.model.jira.JiraIssueFieldOption;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.event.JiraWebhookEventEnum;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;

@Component
public class JiraEventHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(JiraEventHandler.class);

	@Autowired
	private JiraService jiraService;
	
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		List<JiraIssueFieldOption> tribunalRequisitante = this.getTribunaisRequisitantes(jiraEventIssue.getIssue());
		JiraUser usuarioAcao = null;
		if(jiraEventIssue.getWebhookEvent() == JiraWebhookEventEnum.ISSUE_CREATED) {
			usuarioAcao = this.getIssueReporter(jiraEventIssue.getIssue());
		}else if(jiraEventIssue.getWebhookEvent() == JiraWebhookEventEnum.ISSUE_UPDATED) {
			if(jiraEventIssue.getComment() != null) {
				if(this.verificaSeRequisitouIssue(jiraEventIssue.getComment())) {
					usuarioAcao = this.getCommentAuthor(jiraEventIssue.getComment());
				}
			}
			if(usuarioAcao == null) {
				usuarioAcao = this.getIssueAssignee(jiraEventIssue.getIssue());
			}
		}
		
		// TODO - recuperar o tribunal requisitante do usuário
		jiraService.teste();
	}
	
	private boolean verificaSeRequisitouIssue(JiraIssueComment comment) {
		return (comment.getBody().toLowerCase().contains("temos interesse") 
				|| comment.getBody().toLowerCase().contains("estamos interessados"));
	}

	private JiraUser getCommentAuthor(JiraIssueComment comment) {
		JiraUser author = null;
		try {
			author = comment.getAuthor();
		}catch (Exception e) {
			// ignora
		}
		return author;
	}

	private List<JiraIssueFieldOption> getTribunaisRequisitantes(JiraIssue jiraIssue) {
		List<JiraIssueFieldOption> tribunaisRequisitantes = null;
		try {
			tribunaisRequisitantes = jiraIssue.getFields().getTribunalRequisitante();
		}catch (Exception e) {
			// ignora
		}
		return tribunaisRequisitantes;
	}

	private JiraUser getIssueReporter(JiraIssue jiraIssue) {
		JiraUser reporter = null;
		try {
			reporter = jiraIssue.getFields().getReporter();
		}catch (Exception e) {
			// ignora
		}
		return reporter;
	}
	
	private JiraUser getIssueAssignee(JiraIssue jiraIssue) {
		JiraUser assignee = null;
		try {
			assignee = jiraIssue.getFields().getAssignee();
		}catch (Exception e) {
			// ignora
		}
		return assignee;
	}

}