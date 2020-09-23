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
import com.devplatform.model.rocketchat.RocketchatRoom;
import com.devplatform.model.rocketchat.RocketchatUser;
import com.devplatform.model.rocketchat.request.RocketchatPostMessageRequest;
import com.devplatform.model.rocketchat.response.RocketchatChannelsResponse;
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

	@Value("${project.rocketchat.channel.pje-dev-platform-id}") 
	private String GRUPO_PJE_DEV_PLATFORM;

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
		String idChannel = channel;
		if(StringUtils.isNotBlank(channel) && !channel.startsWith("@")) {
			RocketchatRoom channelObj = findChannel(channel);
			if(channelObj != null) {			
				idChannel = channelObj.getId();
			}
		}
		if(StringUtils.isNotBlank(idChannel)) {
			RocketchatPostMessageRequest message = new RocketchatPostMessageRequest(idChannel, text);
			RocketchatPostMessageResponse response = postMessage(message);
			if(response != null) {
				logger.debug("Message response: "+ response.toString());
			}
		}else {
			logger.error("Channel not founded :: " + channel);
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
	
	public void sendMessagePlataformaPJEDev(String text) {
		sendSimpleMessage(GRUPO_PJE_DEV_PLATFORM, text);
	}
	
	public void sendMessageGeral(String text) {
		sendSimpleMessage(GRUPO_GERAL, text);
	}

	public void sendMessageCanaisEspecificos(String text, List<String> canais) {
		if(canais != null && !canais.isEmpty()) {
			for (String canal : canais) {
				sendSimpleMessage(canal, text);
			}
		}
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
		String query = "{\"$or\": [{\"emails\": {\"$elemMatch\":{\"address\": \"" + userNameOrNameOrEmail + "\"}}}, {\"name\":\"" + userNameOrNameOrEmail + "\"}, {\"username\":\"" + userNameOrNameOrEmail + "\"}], \"active\":true}";
		
		RocketchatUser foundedUser = null;
		List<RocketchatUser> users = getUsersStringQuery(query);
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

	public List<RocketchatUser> getUsersStringQuery(String query){
		Map<String, Object> elementN0 = new HashMap<>();
		elementN0.put("query", query);
		
		return getUsers(elementN0);
	}

	public List<RocketchatUser> getUsersMapQuery(Map<String, Object> query){
		Map<String, Object> elementN0 = new HashMap<>();
		elementN0.put("query", query);
		
		return getUsers(elementN0);
	}	
	@Cacheable(cacheNames = "rocket-get-users")
	public List<RocketchatUser> getUsers(Map<String, Object> elementN0){
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
			String errorMsg = "Houve um problema ao recuperar a lista de usuários com a query: "+ elementN0.toString() + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMsg);
		}
		
		return users;
	}
	
	
	@Cacheable(cacheNames = "rocket-user-from-name")
	public RocketchatRoom findChannel(String nameOrId) {
		/**
			{
			  "t": "c",
			  "$or": [
			    {
			      "name": {
			        "$regex": "^TJMT$",
			        "$options": "i"
			      }
			    },
			    {
			      "_id": "tjmt"
			    }
			  ]
			}
		 */
		String query = "{\"t\":\"c\", \"$or\": [{\"name\":{ \"$regex\":\"^" + nameOrId + "$\", \"$options\":\"i\"}},{\"_id\":\"" + nameOrId + "\"}]}";
		
		RocketchatRoom foundedRoom = null;
		List<RocketchatRoom> rooms = getChannelsStringQuery(query);
		if(rooms != null && !rooms.isEmpty()) {
			if(rooms.size() > 1) {
				for (RocketchatRoom room : rooms) {
					if(Utils.compareAsciiIgnoreCase(room.getId(), nameOrId)) {
						foundedRoom = room;
						break;
					}
					if(Utils.compareAsciiIgnoreCase(room.getName(), nameOrId)) {
						foundedRoom = room;
						break;
					}
				}
			}
			if(foundedRoom == null) {
				foundedRoom = rooms.get(0);
			}
		}
		
		return foundedRoom;
	}

	public List<RocketchatRoom> getChannelsStringQuery(String query){
		Map<String, Object> elementN0 = new HashMap<>();
		elementN0.put("query", query);
		
		return getChannels(elementN0);
	}

	public List<RocketchatRoom> getChannelsMapQuery(Map<String, Object> query){
		Map<String, Object> elementN0 = new HashMap<>();
		elementN0.put("query", query);
		
		return getChannels(elementN0);
	}

	@Cacheable(cacheNames = "rocket-get-channels")
	public List<RocketchatRoom> getChannels(Map<String, Object> elementN0){
		List<RocketchatRoom> rooms = new ArrayList<>();
		Integer startAt = 0;
		boolean finalizado = false;
		try {
			while(!finalizado) {
				elementN0.put("offset", startAt.toString());
				RocketchatChannelsResponse response = rocketchatClient.getChannels(elementN0);
				
				if(response != null && response.getSuccess() && response.getTotal() > 0) {
					if((response.getCount() + response.getOffset()) <= response.getTotal() ) {
						startAt += (response.getCount() + response.getOffset());
						rooms.addAll(response.getChannels());
					}
				}
				if(response == null || !response.getSuccess() || response.getCount() == 0 || startAt >= response.getTotal()) {
					finalizado = true;
					break;
				}
			}
		}catch (Exception e) {
			String errorMsg = "Houve um problema ao recuperar a lista de canais com a query: "+ elementN0.toString() + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMsg);
		}
		
		return rooms;
	}

}
