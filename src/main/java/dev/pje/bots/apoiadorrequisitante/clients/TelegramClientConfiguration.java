package dev.pje.bots.apoiadorrequisitante.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import feign.httpclient.ApacheHttpClient;

public class TelegramClientConfiguration {

	@Bean
	public ApacheHttpClient client() {
		return new ApacheHttpClient();
	}

//	@Bean
//	public OkHttpClient client() {
//		return new OkHttpClient();
//	}

	@Bean
	@ConditionalOnProperty(name = { "clients.telegram.token" })
	public PathVariableRequestInterceptor pathVariableAuthFeignRequestInterceptor(@Value("${clients.telegram.token}") String token) {
		return new PathVariableRequestInterceptor(token);
	}
}