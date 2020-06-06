package dev.pje.bots.apoiadorrequisitante.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import feign.httpclient.ApacheHttpClient;

public class SlackClientConfiguration {

	@Bean
	public ApacheHttpClient client() {
		return new ApacheHttpClient();
	}

//	@Bean
//	public OkHttpClient client() {
//		return new OkHttpClient();
//	}

	@Bean
	@ConditionalOnProperty(name = { "clients.slack.token" })
	public OAuthRequestInterceptor oauth2FeignRequestInterceptor(@Value("${clients.slack.token}") String token) {
		return new OAuthRequestInterceptor(token);
	}
}