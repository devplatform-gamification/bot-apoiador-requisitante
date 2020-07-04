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

	@Bean
	@ConditionalOnProperty(name = { "clients.gitlab.token" })
	public OAuthPrivateTokenRequestInterceptor oauth2FeignRequestInterceptor(@Value("${clients.gitlab.token}") String token) {
		return new OAuthPrivateTokenRequestInterceptor(token);
	}
}