package dev.pje.bots.apoiadorrequisitante.services;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.devplatform.model.telegram.TelegramMessage;
import com.devplatform.model.telegram.TelegramUser;
import com.devplatform.model.telegram.request.TelegramSendMessage;
import com.devplatform.model.telegram.response.TelegramResponse;

import dev.pje.bots.apoiadorrequisitante.clients.TelegramClient;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Service
public class TelegramService {
	private static final Logger logger = LoggerFactory.getLogger(TelegramService.class);

	@Autowired
	private TelegramClient telegramClient;

	public static final String NOME_GRUPO_BOT_TRIAGEM = "-1001454483737";
	public static Integer numMessagesSent = 0;

	public void whoami() {
		TelegramResponse<TelegramUser> response = telegramClient.whoami();
		logger.debug("User: " +response.toString());
	}
	
	// TODO - transformar isso em um buffer de mensagens quando forem para o mesmo canal.... ter um schedule que limpa esse buffer de 1 em 1 m
	public void sendSimpleMessage(String channel, String text, Boolean silent) {
		if(silent == null) {
			silent = false;
		}
		TelegramSendMessage message = new TelegramSendMessage(channel, Utils.escapeTelegramMarkup(text));
		message.setDisable_notification(silent);
		boolean passou = false;
		int tentativas = 0;
		try {
			while(!passou && tentativas < 3) {
				int randomInt = (int)(Math.random()) + 1;
				TimeUnit.SECONDS.sleep(randomInt);
				if(tentativas > 0) {
					logger.info("waitting 40 seconds before next try....");
					TimeUnit.SECONDS.sleep(40);
				}
				int numMessages = ++numMessagesSent;
				try {
					TelegramResponse<TelegramMessage> response = telegramClient.sendMessage(message);
					logger.debug("Message response: "+ response.toString());
					passou = true;
				}catch (Exception e) {
					logger.error(e.getLocalizedMessage());
					logger.info("Message sent #"+ numMessages);
					passou = false;
				}
				tentativas++;
			}
		}catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		}
//		TelegramResponse<TelegramUser> user = telegramClient.whoami();
//		logger.debug("Message user response: "+ user.toString());
	}
	
	public void sendBotMessage(String text) {
		sendSimpleMessage(NOME_GRUPO_BOT_TRIAGEM, text, true);
	}
}
