package dev.pje.bots.apoiadorrequisitante.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.devplatform.model.slack.request.SlackSendMessage;
import com.devplatform.model.slack.request.SlackUserInfo;
import com.devplatform.model.slack.response.SlackMessageResponse;
import com.devplatform.model.slack.response.SlackUserResponse;

import dev.pje.bots.apoiadorrequisitante.clients.SlackClient;
import dev.pje.bots.apoiadorrequisitante.clients.SlackClientDebug;

@Service
public class SlackService {
	private static final Logger logger = LoggerFactory.getLogger(SlackService.class);

	@Autowired
	private SlackClient slackClient;

	@Autowired
	private SlackClientDebug slackClientDebug;
	
	public static final String NOME_GRUPO_BOT_TRIAGEM = "C014HN6SSPR";

	public void whois(String userid) {
		SlackUserInfo user = new SlackUserInfo(userid);
		SlackUserResponse response = slackClient.whois(user);
		logger.debug("User [" + userid + "]: " +response.toString());
	}
	
	public void sendSimpleMessage(String channel, String text) {
		SlackSendMessage message = new SlackSendMessage(channel, text);
		SlackMessageResponse response = slackClient.sendMessage(message);
		logger.debug("Message response: "+ response.toString());
	}
	
	public void sendBotMessage(String text) {
		sendSimpleMessage(NOME_GRUPO_BOT_TRIAGEM, text);
	}
}
