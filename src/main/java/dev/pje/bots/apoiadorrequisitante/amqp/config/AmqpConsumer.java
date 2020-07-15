package dev.pje.bots.apoiadorrequisitante.amqp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.event.GitlabEventPush;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.GitlabEventHandlerCommit;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.GitlabEventHandlerGitflow;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.JiraEventHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.JiraEventHandlerClassification;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.JiraEventHandlerReleaseNotes;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.VersionLaunchHandler;

@Component
public class AmqpConsumer {

	private static final Logger logger = LoggerFactory.getLogger(AmqpConsumer.class);
    
	@Autowired
    private ObjectMapper objectMapper;
	
	@Autowired
	private JiraEventHandler jiraEventHandler;

	@Autowired
	private JiraEventHandlerClassification jiraEventHandlerClassification;
	
	@Autowired
	private JiraEventHandlerReleaseNotes jiraEventHandlerReleaseNotes;

	@Autowired
	private GitlabEventHandlerCommit gitlabEventHandlerCommit;

	@Autowired
	private GitlabEventHandlerGitflow gitlabEventHandlerGitflow;

	@Autowired
	private VersionLaunchHandler versionLaunchHandler;

	@RabbitListener(
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.default-receive-queue}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void receiveIssue(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info("[REQUISITANTE][JIRA] - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			jiraEventHandler.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.classification-queue}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void receiveIssueToCheckEpicTheme(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info("[CLASSIFICACAO][JIRA] - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			jiraEventHandlerClassification.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.commit-script-queue}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.gitlab.push.routing-key}"})
		)
	public void gitlabPushCodeToCheckScript(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			GitlabEventPush gitEventPush = objectMapper.readValue(body, GitlabEventPush.class);
			String projectName = gitEventPush.getProject().getName();
			String branchName = gitEventPush.getRef();
			logger.info("[COMMIT][GITLAB] - project: " + projectName + " branch: " + branchName);
			gitlabEventHandlerCommit.handle(gitEventPush);
		}
	}	

	@RabbitListener(
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.gitflow-queue}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.gitlab.push.routing-key}"})
		)
	public void gitlabGitflowPush(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			GitlabEventPush gitEventPush = objectMapper.readValue(body, GitlabEventPush.class);
			String projectName = gitEventPush.getProject().getName();
			String branchName = gitEventPush.getRef();
			logger.info("[GITFLOW][GITLAB] - project: " + projectName + " branch: " + branchName);
			gitlabEventHandlerGitflow.handle(gitEventPush);
		}
	}	

	@RabbitListener(
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.release-notes-queue}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void generateReleaseNotes(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(JiraEventHandlerReleaseNotes.MESSAGE_PREFIX + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			jiraEventHandlerReleaseNotes.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.version-launch-queue}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void versionLaunch(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(VersionLaunchHandler.MESSAGE_PREFIX + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			versionLaunchHandler.handle(jiraEventIssue);
		}
	}	
}