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

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.CheckingNewScriptMigrationsInCommitHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.GitlabEventHandlerGitflow;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.JiraEventHandlerClassification;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.JiraIssueCheckApoiadorRequisitanteEventHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.LanVersion01TriageHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.LanVersion02GenerateReleaseCandidateHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.LanVersion03PrepareNextVersionHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.LanVersion04GenerateReleaseNotesHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.LanVersion05ProcessReleaseNotesHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.LanVersion06FinishReleaseNotesProcessingHandler;

@Component
public class AmqpConsumer {

	private static final Logger logger = LoggerFactory.getLogger(AmqpConsumer.class);
    
	@Autowired
    private ObjectMapper objectMapper;
	
	@Autowired
	private JiraIssueCheckApoiadorRequisitanteEventHandler jiraIssueCheckApoiadorRequisitanteEventHandler;

	@Autowired
	private JiraEventHandlerClassification jiraEventHandlerClassification;

	@Autowired
	private CheckingNewScriptMigrationsInCommitHandler checkingNewScriptMigrationsInCommit;

	@Autowired
	private GitlabEventHandlerGitflow gitlabEventHandlerGitflow;
	
	@Autowired
	private LanVersion01TriageHandler lanversion01;
	
	@Autowired
	private LanVersion02GenerateReleaseCandidateHandler lanversion02;

	@Autowired
	private LanVersion03PrepareNextVersionHandler lanversion03;

	@Autowired
	private LanVersion04GenerateReleaseNotesHandler lanversion04;

	@Autowired
	private LanVersion05ProcessReleaseNotesHandler lanversion05;
	
	@Autowired
	private LanVersion06FinishReleaseNotesProcessingHandler lanversion06;
	

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
			jiraIssueCheckApoiadorRequisitanteEventHandler.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.classification-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.classification-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
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
			autoStartup = "${spring.rabbitmq.template.custom.commit-script-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.commit-script-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
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
			checkingNewScriptMigrationsInCommit.handle(gitEventPush);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.gitflow-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.gitflow-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
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
			autoStartup = "${spring.rabbitmq.template.custom.lanver01-triage-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver01-triage-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void lanVer01Triage(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion01.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion01.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver02-release-candidate-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver02-release-candidate-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void lanVer02GenerateReleaseCandidate(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion02.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion02.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver03-next-version-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver03-next-version-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void lanVer03PrepareNextVersion(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion03.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion03.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver04-release-notes-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver04-release-notes-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void lanVer04GenerateReleaseNotes(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion04.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion04.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver05-version-launch-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver05-version-launch-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void lanVer05ProcessReleaseNotes(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion05.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion05.handle(jiraEventIssue);
		}
	}
	
	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver06-publish-release-notes-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver06-publish-release-notes-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
//				key = {"${spring.rabbitmq.template.custom.gitlab.tag-push.routing-key}"})
		)
	public void lanVer06FinishReleaseNotesProcessing(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion06.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion06.handle(jiraEventIssue);
		}
	}

}