package dev.pje.bots.apoiadorrequisitante.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.devplatform.model.rocketchat.RocketchatEmail;
import com.devplatform.model.rocketchat.RocketchatUser;
import com.devplatform.model.rocketchat.request.RocketchatPostMessageRequest;
import com.devplatform.model.rocketchat.response.RocketchatPostMessageResponse;
import com.devplatform.model.rocketchat.response.RocketchatUsersResponse;

import dev.pje.bots.apoiadorrequisitante.clients.RocketchatClient;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Service
public class RocketchatService {
	private static final Logger logger = LoggerFactory.getLogger(RocketchatService.class);

	@Autowired
	private RocketchatClient rocketchatClient;

	@Value("${project.rocketchat.channel.triage-bot-id}") 
	private String GRUPO_BOT_TRIAGEM;

	@Value("${project.rocketchat.channel.grupo-revisor-id}") 
	private String GRUPO_REVISOR_TECNICO;

	@Value("${project.rocketchat.channel.grupo-negocial-id}") 
	private String GRUPO_NEGOCIAL;

	@Value("${project.rocketchat.channel.pje-news-id}") 
	private String GRUPO_PJE_NEWS;

	@Value("${project.rocketchat.channel.geral}") 
	private String GRUPO_GERAL;
	
	public void whoami() {
		RocketchatUser response = rocketchatClient.whoami();
		if(response != null) {
			logger.info("User: " +response.toString());
		}
	}
	
	public void sendMessageToUsername(String username, String text) {
		String channel = null;
		if(StringUtils.isNotBlank(username)) {
			channel = "@" + username;
			sendSimpleMessage(channel, text);
		}
	}
	
	public void sendSimpleMessage(String channel, String text) {
		RocketchatPostMessageRequest message = new RocketchatPostMessageRequest(channel, text);
		RocketchatPostMessageResponse response = postMessage(message);
		if(response != null) {
			logger.debug("Message response: "+ response.toString());
		}
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
	
	public RocketchatPostMessageResponse postMessage(RocketchatPostMessageRequest message) {
		RocketchatPostMessageResponse response = null;
		try {
			response = rocketchatClient.postMessage(message);
		}catch (Exception e) {
			String errorMsg = "Error to postMessage into rocketchat:" + message.toString() + " - error: " + e.getLocalizedMessage();
			logger.error(errorMsg);
		}
		return response;
	}
	
	@Cacheable(cacheNames = "rocket-user-from-name")
	public RocketchatUser findUser(String userNameOrNameOrEmail) {
		/**
		{
		  "active": true,
		  "$or": [
		    {
		      "username": "<username>"
		    },
		    {
		      "emails": {
		        "$elemMatch": {
		          "address": "<email>"
		        }
		      }
		    },
		    {
		      "name": "<fullname>"
		    }
		  ]
		}		
		 */
		Map<String, Object> elementN4 = new HashMap<>();
		elementN4.put("address", userNameOrNameOrEmail);
		
		Map<String, Object> elementN3 = new HashMap<>();
		elementN3.put("$elemMatch", elementN4);

		Map<String, Object> elementN2 = new HashMap<>();
		elementN2.put("emails", elementN3);
		elementN2.put("name", userNameOrNameOrEmail);
		elementN2.put("username", userNameOrNameOrEmail);

		Map<String, Object> elementN1 = new HashMap<>();
		elementN1.put("active", true);
		elementN1.put("$or", elementN2);

		RocketchatUser foundedUser = null;
		List<RocketchatUser> users = getUsers(elementN1);
		if(users != null && !users.isEmpty()) {
			foundedUser = users.get(0);
			if(users.size() > 1) {
				boolean encontrado = false;
				for (RocketchatUser user : users) {
					if(user.getEmails() != null && !user.getEmails().isEmpty()) {
						for (RocketchatEmail rocketchatEmail : user.getEmails()) {
							if(StringUtils.isNotBlank(rocketchatEmail.getAddress()) && 
									Utils.compareAsciiIgnoreCase(rocketchatEmail.getAddress(), userNameOrNameOrEmail)) {
								foundedUser = user;
								encontrado = true;
								break;
							}
						}
						if(encontrado) {
							break;
						}
					}
					if(StringUtils.isNotBlank(user.getUsername())) {
						if(Utils.compareAsciiIgnoreCase(user.getUsername(), userNameOrNameOrEmail)) {
							foundedUser = user;
							encontrado = true;
							break;
						}
					}
				}
			}
		}
		
		return foundedUser;
	}
	
	public List<RocketchatUser> getUsers(Map<String, Object> query){
		Map<String, Object> elementN0 = new HashMap<>();
		elementN0.put("query", query);

		List<RocketchatUser> users = new ArrayList<>();
		Integer startAt = 0;
		boolean finalizado = false;
		try {
			while(!finalizado) {
				elementN0.put("offset", startAt.toString());
				RocketchatUsersResponse response = rocketchatClient.getUsers(elementN0);
				
				if(response != null && response.getSuccess() && response.getTotal() > 0) {
					if((response.getCount() + response.getOffset()) <= response.getTotal() ) {
						startAt += (response.getCount() + response.getOffset());
						users.addAll(response.getUsers());
					}
				}
				if(response == null || !response.getSuccess() || response.getCount() == 0 || startAt >= response.getTotal()) {
					finalizado = true;
					break;
				}
			}
		}catch (Exception e) {
			String errorMsg = "Houve um problema ao recuperar a lista de usuários com a query: "+ query.toString() + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMsg);
		}
		
		return users;
	}
}
