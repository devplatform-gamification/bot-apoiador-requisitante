package dev.pje.bots.apoiadorrequisitante.clients;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devplatform.model.rocketchat.RocketchatMessage;
import com.devplatform.model.rocketchat.RocketchatUser;
import com.devplatform.model.rocketchat.request.RocketchatDirectMessageRequest;
import com.devplatform.model.rocketchat.request.RocketchatPostMessageRequest;
import com.devplatform.model.rocketchat.response.RocketchatChannelsResponse;
import com.devplatform.model.rocketchat.response.RocketchatPostMessageResponse;
import com.devplatform.model.rocketchat.response.RocketchatRoomResponse;
import com.devplatform.model.rocketchat.response.RocketchatSendMessageResponse;
import com.devplatform.model.rocketchat.response.RocketchatUsersResponse;

@FeignClient(name = "roketchat", url = "${clients.rocketchat.url}", configuration = RocketchatClientConfiguration.class)
public interface RocketchatClient {
	@GetMapping(value = "/api/v1/me", consumes = MediaType.APPLICATION_JSON_VALUE)
	public RocketchatUser whoami();

	@GetMapping(value = "/api/v1/channels.list?{options}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public RocketchatChannelsResponse getChannels(
			@SpringQueryMap Map<String, Object> options);

	@GetMapping(value = "/api/v1/users.list?{options}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public RocketchatUsersResponse getUsers(
			@SpringQueryMap Map<String, Object> options);

	@PostMapping(value = "/api/v1/dm.create", consumes = MediaType.APPLICATION_JSON_VALUE)
	public RocketchatRoomResponse createDirectMessageSession(
			@RequestBody RocketchatDirectMessageRequest directMessageRequest);
	
	@PostMapping(value = "/api/v1/chat.sendMessage", consumes = MediaType.APPLICATION_JSON_VALUE)
	public RocketchatSendMessageResponse sendMessage(
			@RequestBody RocketchatMessage message);

	@PostMapping(value = "/api/v1/chat.postMessage", consumes = MediaType.APPLICATION_JSON_VALUE)
	public RocketchatPostMessageResponse postMessage(
			@RequestBody RocketchatPostMessageRequest message);
		

}
