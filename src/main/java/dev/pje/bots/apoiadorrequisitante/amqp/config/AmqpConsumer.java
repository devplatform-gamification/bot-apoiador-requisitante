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

import com.devplatform.model.jira.event.JiraEventIssue;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.JiraEventHandler;

@Component
public class AmqpConsumer {

	private static final Logger logger = LoggerFactory.getLogger(AmqpConsumer.class);
    
	@Autowired
    private ObjectMapper objectMapper;
	
	@Autowired
	private JiraEventHandler jiraEventHandler;
    
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
			logger.info("[JIRA] - " + issueKey + jiraEventIssue.getIssueEventTypeName().name());
			jiraEventHandler.handle(jiraEventIssue);
		}
	}	
	
}
