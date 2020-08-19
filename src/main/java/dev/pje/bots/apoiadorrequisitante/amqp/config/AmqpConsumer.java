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
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab.Gitlab04TagPushHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab.GitlabEventHandlerGitflow;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.jira.Jira01ClassificationHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.jira.Jira02ApoiadorRequisitanteHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.jira.Jira03DemandanteHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion01TriageHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion02GenerateReleaseCandidateHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion03PrepareNextVersionHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion04GenerateReleaseNotesHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion05ProcessReleaseNotesHandler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao.LanVersion06FinishReleaseNotesProcessingHandler;

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
	private Gitlab04TagPushHandler gitlab04TagPushHandler;

	/**************/
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
			autoStartup = "${spring.rabbitmq.template.custom.gitlab04-tag-pushed.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.gitlab04-tag-pushed.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.gitlab.tag-push.routing-key}"})
		)
	public void gitlab04TagPushed(Message msg) throws Exception {
		if(msg != null && msg.getBody() != null && msg.getMessageProperties() != null) {
			String body = new String(msg.getBody());
			GitlabEventPushTag gitEventTag = objectMapper.readValue(body, GitlabEventPushTag.class);
			if(gitEventTag != null) {
				String projectName = gitEventTag.getProject().getName();
				String tagName = "Título não identificado";
				if(StringUtils.isNotBlank(gitEventTag.getRef())) {
					tagName = gitEventTag.getRef();
				}
				logger.info(gitlab04TagPushHandler.getMessagePrefix() + " - " + "project: " + projectName + " - TAG: " + tagName);
				gitlab04TagPushHandler.handle(gitEventTag);
			}else {
				logger.error(gitlab04TagPushHandler.getMessagePrefix() + " Objeto não parece ser de um evento de MR");
			}
		}
	}
	
	/************************/
	// Consumers de lancamento de versao
	/************************/

	@RabbitListener(
			autoStartup = "${spring.rabbitmq.template.custom.lanver01-triage-queue.auto-startup}",
			bindings = @QueueBinding(
				value = @Queue(value = "${spring.rabbitmq.template.custom.lanver01-triage-queue.name}", durable = "true", autoDelete = "false", exclusive = "false"), 
				exchange = @Exchange(value = "${spring.rabbitmq.template.exchange}", type = ExchangeTypes.TOPIC), 
				key = {"${spring.rabbitmq.template.custom.lanver01-triage-queue.routing-key-created}", "${spring.rabbitmq.template.custom.lanver01-triage-queue.routing-key-updated}"})
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
				key = {"${spring.rabbitmq.template.custom.lanver02-release-candidate-queue.routing-key-updated}"})
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
				key = {"${spring.rabbitmq.template.custom.lanver03-next-version-queue.routing-key-updated}"})
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
				key = {"${spring.rabbitmq.template.custom.lanver04-release-notes-queue.routing-key-updated}"})
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
				key = {"${spring.rabbitmq.template.custom.lanver05-version-launch-queue.routing-key-updated}"})
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
				key = {"${spring.rabbitmq.template.custom.lanver06-publish-release-notes-queue.routing-key-updated}"})
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