package dev.pje.bots.apoiadorrequisitante.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devplatform.model.slack.request.SlackSendMessage;
import com.devplatform.model.slack.request.SlackUserInfo;
import com.devplatform.model.slack.response.SlackMessageResponse;
import com.devplatform.model.slack.response.SlackUserResponse;

@FeignClient(name = "slackDebug", url = "https://webhook.site/0602e93d-4659-40eb-86ba-3ed09dab942f", configuration = SlackClientConfiguration.class)
public interface SlackClientDebug {
	@GetMapping(value = "users.info", consumes = "application/json")
	public SlackUserResponse whois(@SpringQueryMap SlackUserInfo user);
	
	@PostMapping(value = "chat.postMessage", consumes = "application/json")
	public SlackMessageResponse sendMessage(@RequestBody SlackSendMessage message);

}