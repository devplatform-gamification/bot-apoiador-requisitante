package dev.pje.bots.apoiadorrequisitante.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.devplatform.model.slack.request.SlackSendMessage;
import com.devplatform.model.slack.request.SlackUserInfo;
import com.devplatform.model.slack.response.SlackMessageResponse;
import com.devplatform.model.slack.response.SlackUserResponse;

import dev.pje.bots.apoiadorrequisitante.clients.SlackClient;

@Service
public class SlackService {
	private static final Logger logger = LoggerFactory.getLogger(SlackService.class);

	@Autowired
	private SlackClient slackClient;

	@Value("${project.slack.channel.triage-bot-id}") 
	private String GRUPO_BOT_TRIAGEM;

	@Value("${project.slack.channel.grupo-revisor-id}") 
	private String GRUPO_REVISOR_TECNICO;

	@Value("${project.slack.channel.grupo-negocial-id}") 
	private String GRUPO_NEGOCIAL;

	@Value("${project.slack.channel.pje-news-id}") 
	private String GRUPO_PJE_NEWS;

	@Value("${project.slack.channel.geral}") 
	private String GRUPO_GERAL;

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
		sendSimpleMessage(GRUPO_BOT_TRIAGEM, text);
	}
	
	public void sendMessageGrupoRevisorTecnico(String text) {
		sendSimpleMessage(GRUPO_REVISOR_TECNICO, text);
	}
	
	public void sendMessageGrupoNegocial(String text) {
		sendSimpleMessage(GRUPO_NEGOCIAL, text);
	}
	
	public void sendMessagePJENews(String text) {
		sendSimpleMessage(GRUPO_PJE_NEWS, text);
	}
	
	public void sendMessageGeral(String text) {
		sendSimpleMessage(GRUPO_GERAL, text);
	}
}
