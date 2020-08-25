package dev.pje.bots.apoiadorrequisitante.amqp.config;

import org.apache.commons.lang3.StringUtils;
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

import com.devplatform.model.gitlab.event.GitlabEventMergeRequest;
import com.devplatform.model.gitlab.event.GitlabEventPush;
import com.devplatform.model.gitlab.event.GitlabEventPushTag;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.docs.Documentation01TriageHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.docs.Documentation02CreateSolutionHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.docs.Documentation03CheckAutomaticMergeHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.docs.Documentation04ManualMergeHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.docs.Documentation05FinishHomologationHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab.CheckingNewScriptMigrationsInCommitHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab.Gitlab03MergeRequestUpdateHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab.Gitlab04TagPushFinishVersionHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab.Gitlab05TagPushPrepareNextVersionHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab.GitlabEventHandlerGitflow;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.jira.Jira01ClassificationHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.jira.Jira02ApoiadorRequisitanteHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.jira.Jira03DemandanteHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion010TriageHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion015PrepareActualVersionHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion020GenerateReleaseCandidateHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion030PrepareNextVersionHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion040GenerateReleaseNotesHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion050ProcessReleaseNotesHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion060FinishReleaseNotesProcessingHandler;

@Component
public class AmqpConsumer {

	private static final Logger logger = LoggerFactory.getLogger(AmqpConsumer.class);
    
	@Autowired
    private ObjectMapper objectMapper;
	
	@Autowired
	private Jira01ClassificationHandler jira01ClassificationHandler;

	@Autowired
	private Jira02ApoiadorRequisitanteHandler jira02ApoiadorRequisitanteHandler;
	
	@Autowired
	private Jira03DemandanteHandler jira03DemandanteHandler;

	/**************/
	@Autowired
	private CheckingNewScriptMigrationsInCommitHandler checkingNewScriptMigrationsInCommit;

	@Autowired
	private GitlabEventHandlerGitflow gitlabEventHandlerGitflow;

	@Autowired
	private Gitlab03MergeRequestUpdateHandler gitlab03MergeRequestUpdate;

	@Autowired
	private Gitlab04TagPushFinishVersionHandler gitlab04TagPushFinishVersion;

	@Autowired
	private Gitlab05TagPushPrepareNextVersionHandler gitlab05TagPushPrepareNextVersion;

	/**************/
	@Autowired
	private LanVersion010TriageHandler lanversion010;

	@Autowired
	private LanVersion015PrepareActualVersionHandler lanversion015;

	@Autowired
	private LanVersion020GenerateReleaseCandidateHandler lanversion020;

	@Autowired
	private LanVersion030PrepareNextVersionHandler lanversion030;

	@Autowired
	private LanVersion040GenerateReleaseNotesHandler lanversion040;

	@Autowired
	private LanVersion050ProcessReleaseNotesHandler lanversion050;
	
	@Autowired
	private LanVersion060FinishReleaseNotesProcessingHandler lanversion060;
	
	/**************/
	@Autowired
	private Documentation01TriageHandler documentation01;

	@Autowired
	private Documentation02CreateSolutionHandler documentation02;

	@Autowired
	private Documentation03CheckAutomaticMergeHandler documentation03;
	
	@Autowired
	private Documentation04ManualMergeHandler documentation04;
	
	@Autowired
	private Documentation05FinishHomologationHandler documentation05;
	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.jira02-classification-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.jira02-classification-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void jira01Classification(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			if(jiraEventIssue.getIssueEventTypeName() == null) {
				logger.error("[CLASSIFICACAO][JIRA] - " + issueKey + "Falha na identificação do tipo de evento");
			}else {
				logger.info("[CLASSIFICACAO][JIRA] - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
				jira01ClassificationHandler.handle(jiraEventIssue);
			}
		}
	}

	@RabbitListener(
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.default-receive-queue}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-created.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}"})
		)
	public void jira02Requisitante(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			if(jiraEventIssue.getIssueEventTypeName() == null) {
				logger.error("[REQUISITANTE][JIRA] - " + issueKey + "Falha na identificação do tipo de evento");
			}else {
				logger.info("[REQUISITANTE][JIRA] - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
				jira02ApoiadorRequisitanteHandler.handle(jiraEventIssue);
			}
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.jira03-resposta-demandante.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.jira03-resposta-demandante.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.jira.issue-updated.routing-key}", "${spring.rabbitmq.template.custom.jira.issue-generic.routing-key}"})
		)
	public void jira03RespostaDemandante(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(jira03DemandanteHandler.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			jira03DemandanteHandler.handle(jiraEventIssue);
		}
	}


	/************************/
	// Consumers de gitlab
	/************************/

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
			autoStartup = "${spring.rabbitmq.template.custom.gitlab03-merge-request-updated.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.gitlab03-merge-request-updated.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.gitlab.merge-request.routing-key}"})
		)
	public void gitlab03MergeRequestUpdated(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			GitlabEventMergeRequest gitEventMR = objectMapper.readValue(body, GitlabEventMergeRequest.class);
			if(gitEventMR != null) {
				String projectName = gitEventMR.getProject().getName();
				String mergeTitle = "Título não identificado";
				if(gitEventMR.getObjectAttributes() != null && StringUtils.isNotBlank(gitEventMR.getObjectAttributes().getTitle())) {
					mergeTitle = gitEventMR.getObjectAttributes().getTitle();
				}
				logger.info(gitlab03MergeRequestUpdate.getMessagePrefix() + " - " + "project: " + projectName + " - MR: " + mergeTitle);
				gitlab03MergeRequestUpdate.handle(gitEventMR);
			}else {
				logger.error(gitlab03MergeRequestUpdate.getMessagePrefix() + " Objeto não parece ser de um evento de MR");
			}
		}
	}
	
	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.gitlab04-tag-pushed-end-version.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.gitlab04-tag-pushed-end-version.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.gitlab.tag-push.routing-key}"})
		)
	public void gitlab04TagPushedFinishVersion(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			GitlabEventPushTag gitEventTag = objectMapper.readValue(body, GitlabEventPushTag.class);
			if(gitEventTag != null) {
				String projectName = gitEventTag.getProject().getName();
				String tagName = "Título não identificado";
				if(StringUtils.isNotBlank(gitEventTag.getRef())) {
					tagName = gitEventTag.getRef();
				}
				logger.info(gitlab04TagPushFinishVersion.getMessagePrefix() + " - " + "project: " + projectName + " - TAG: " + tagName);
				gitlab04TagPushFinishVersion.handle(gitEventTag);
			}else {
				logger.error(gitlab04TagPushFinishVersion.getMessagePrefix() + " Objeto não parece ser de um evento de MR");
			}
		}
	}

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.gitlab05-tag-pushed-prepare-next-version.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.gitlab05-tag-pushed-prepare-next-version.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.gitlab.tag-push.routing-key}"})
		)
	public void gitlab05TagPushPrepareNextVersion(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			GitlabEventPushTag gitEventTag = objectMapper.readValue(body, GitlabEventPushTag.class);
			if(gitEventTag != null) {
				String projectName = gitEventTag.getProject().getName();
				String tagName = "Título não identificado";
				if(StringUtils.isNotBlank(gitEventTag.getRef())) {
					tagName = gitEventTag.getRef();
				}
				logger.info(gitlab05TagPushPrepareNextVersion.getMessagePrefix() + " - " + "project: " + projectName + " - TAG: " + tagName);
				gitlab05TagPushPrepareNextVersion.handle(gitEventTag);
			}else {
				logger.error(gitlab05TagPushPrepareNextVersion.getMessagePrefix() + " Objeto não parece ser de um evento de MR");
			}
		}
	}
	

	/************************/
	// Consumers de lancamento de versao
	/************************/

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver010-triage-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver010-triage-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.lanver010-triage-queue.routing-key-created}", "${spring.rabbitmq.template.custom.lanver010-triage-queue.routing-key-updated}"})
		)
	public void lanVer010Triage(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion010.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion010.handle(jiraEventIssue);
		}
	}
	
	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver015-prepare-actual-version-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver015-prepare-actual-version-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.lanver015-prepare-actual-version-queue.routing-key-updated}"})
		)
	public void lanVer015PrepareActualVersion(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion015.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion015.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver020-release-candidate-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver020-release-candidate-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.lanver020-release-candidate-queue.routing-key-updated}"})
		)
	public void lanVer020GenerateReleaseCandidate(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion020.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion020.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver030-next-version-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver030-next-version-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.lanver030-next-version-queue.routing-key-updated}"})
		)
	public void lanVer030PrepareNextVersion(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion030.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion030.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver040-release-notes-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver040-release-notes-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.lanver040-release-notes-queue.routing-key-updated}"})
		)
	public void lanVer040GenerateReleaseNotes(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion040.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion040.handle(jiraEventIssue);
		}
	}	

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver050-version-launch-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver050-version-launch-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.lanver050-version-launch-queue.routing-key-updated}"})
		)
	public void lanVer050ProcessReleaseNotes(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion050.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion050.handle(jiraEventIssue);
		}
	}
	
	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver060-publish-release-notes-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver060-publish-release-notes-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.lanver060-publish-release-notes-queue.routing-key-updated}"})
		)
	public void lanVer060FinishReleaseNotesProcessing(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(lanversion060.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			lanversion060.handle(jiraEventIssue);
		}
	}

	/************************/
	// Consumers de documentacao
	/************************/
	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.documentation01-triage-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.documentation01-triage-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.documentation01-triage-queue.routing-key-created}",
						"${spring.rabbitmq.template.custom.documentation01-triage-queue.routing-key-updated}"})
		)
	public void documentation01TriageProcessing(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(documentation01.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			documentation01.handle(jiraEventIssue);
		}
	}

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.documentation02-create-solution-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.documentation02-create-solution-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.documentation02-create-solution-queue.routing-key-updated}"})
		)
	public void documentation02CreateSolution(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(documentation02.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			documentation02.handle(jiraEventIssue);
		}
	}

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.documentation03-check-automatic-merge.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.documentation03-check-automatic-merge.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.documentation03-check-automatic-merge.routing-key-updated}"})
		)
	public void documentation03CheckAutomaticMerge(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(documentation03.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			documentation03.handle(jiraEventIssue);
		}
	}

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.documentation04-manual-merge.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.documentation04-manual-merge.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.documentation04-manual-merge.routing-key-updated}"})
		)
	public void documentation04ManualMerge(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(documentation04.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			documentation04.handle(jiraEventIssue);
		}
	}
	
	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.documentation05-finish-homologation.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.documentation05-finish-homologation.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.documentation05-finish-homologation.routing-key-updated}"})
		)
	public void documentation05FinishHomologation(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			JiraEventIssue jiraEventIssue = objectMapper.readValue(body, JiraEventIssue.class);
			String issueKey = jiraEventIssue.getIssue().getKey();
			logger.info(documentation05.getMessagePrefix() + " - " + issueKey + " - " + jiraEventIssue.getIssueEventTypeName().name());
			documentation05.handle(jiraEventIssue);
		}
	}

}