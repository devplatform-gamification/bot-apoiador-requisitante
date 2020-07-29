package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.ProcessingMessage;
import com.devplatform.model.bot.ProcessingTypeEnum;

import dev.pje.bots.apoiadorrequisitante.utils.MessagesTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.TelegramMarkdownHtml;

@Component
class MessagesLogger {

	private Logger logger;
	public String id;
	public String messagePrefix;
	public static final int LOGLEVEL_ERROR = 1;
	public static final int LOGLEVEL_WARNING = 2;
	public static final int LOGLEVEL_INFO = 3;
	public static final int LOGLEVEL_DEBUG = 4;

	public MessagesLogger() {
		super();
	}
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setMessagePrefix(String messagePrefix) {
		this.messagePrefix = messagePrefix;
	}
	
	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	public void setLogLevel(int logLevel) {
		this.logLevel = logLevel;
	}

	public List<ProcessingMessage> messages = new ArrayList<>();
	public boolean someError = false;
	public int logLevel = 3;
	
	public void clean() {
		id = null;
		messages = new ArrayList<>();
		someError = false;
	}
	
	private String createMessage(String text) {
		StringBuilder messageSb = new StringBuilder(messagePrefix);
		if(StringUtils.isNotBlank(getId())) {
			messageSb.append("|")
				.append(getId())
				.append("|");
		}
		messageSb.append(" - ").append(text);
		return messageSb.toString();
	}
	
	public void debug(String text) {
		String message = createMessage(text);
		messages.add(new ProcessingMessage(message, ProcessingTypeEnum.DEBUG));
		logger.debug(message);
	}
	
	public void info(String text) {
		String message = createMessage(text);
		messages.add(new ProcessingMessage(message, ProcessingTypeEnum.INFO));
		logger.info(message);
	}

	public void warn(String text) {
		String message = createMessage(text);
		messages.add(new ProcessingMessage(message, ProcessingTypeEnum.WARNING));
		logger.warn(message);
	}
	
	public void error(String text) {
		String message = createMessage(text);
		messages.add(new ProcessingMessage(message, ProcessingTypeEnum.ERROR));
		logger.error(message);
		someError = true;
	}
	
	public boolean hasSomeError() {
		return someError;
	}
	
	private List<ProcessingMessage> getMesssagesWithLogLevel() {
		List<ProcessingMessage> messageWitLogLevel = new ArrayList<>();
		for (ProcessingMessage msg : messages) {
			if(msg.getType().equals(ProcessingTypeEnum.ERROR)) { // error is always added
				messageWitLogLevel.add(msg);
				continue;
			}
			if(this.logLevel >= LOGLEVEL_WARNING) {
				if(msg.getType().equals(ProcessingTypeEnum.WARNING)) {
					messageWitLogLevel.add(msg);
					continue;
				}
			}
			if(this.logLevel >= LOGLEVEL_INFO) {
				if(msg.getType().equals(ProcessingTypeEnum.INFO)) {
					messageWitLogLevel.add(msg);
					continue;
				}
			}
			if(this.logLevel >= LOGLEVEL_DEBUG) {
				if(msg.getType().equals(ProcessingTypeEnum.DEBUG)) {
					messageWitLogLevel.add(msg);
					continue;
				}
			}
		}
		return messageWitLogLevel;
	}

	public String getMessagesToJira() {
		JiraMarkdown jiraMarkdown = new JiraMarkdown();
		MessagesTextModel messagesTextModel = new MessagesTextModel(getMesssagesWithLogLevel());
		return messagesTextModel.convert(jiraMarkdown);
	}

	public String getMessagesToTelegram() {
		TelegramMarkdownHtml telegramMarkdown = new TelegramMarkdownHtml();
		MessagesTextModel messagesTextModel = new MessagesTextModel(getMesssagesWithLogLevel());
		return messagesTextModel.convert(telegramMarkdown);
	}

//	protected String getMessagesToSlack() {
//		Slackm telegramMarkdown = new TelegramMarkdownHtml();
//		MessagesTextModel messagesTextModel = new MessagesTextModel(getMesssagesWithLogLevel());
//		return messagesTextModel.convert(telegramMarkdown);
//	}

//	protected String getMessagesToRocketChat() {
//	Slackm telegramMarkdown = new TelegramMarkdownHtml();
//	MessagesTextModel messagesTextModel = new MessagesTextModel(getMesssagesWithLogLevel());
//	return messagesTextModel.convert(telegramMarkdown);
//}
}