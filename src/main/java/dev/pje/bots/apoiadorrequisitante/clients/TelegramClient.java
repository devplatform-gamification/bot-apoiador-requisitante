package dev.pje.bots.apoiadorrequisitante.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.devplatform.model.telegram.TelegramMessage;
import com.devplatform.model.telegram.TelegramUser;
import com.devplatform.model.telegram.request.TelegramSendMessage;
import com.devplatform.model.telegram.response.TelegramResponse;

@FeignClient(name = "telegram", url = "${clients.telegram.url}", configuration = TelegramClientConfiguration.class)
public interface TelegramClient {
	@GetMapping(value = "/getMe", consumes = "application/json")
	public TelegramResponse<TelegramUser> whoami();
	
	@PostMapping(value="/sendMessage", consumes = "application/json")
	public TelegramResponse<TelegramMessage> sendMessage(@SpringQueryMap TelegramSendMessage options);
	
}
