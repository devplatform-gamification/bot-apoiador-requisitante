package dev.pje.bots.apoiadorrequisitante.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import feign.httpclient.ApacheHttpClient;

public class RocketchatClientConfiguration {

	@Bean
	public ApacheHttpClient client() {
		return new ApacheHttpClient();
	}

	@Bean
	@ConditionalOnProperty(name = { "clients.rocketchat.userid", "clients.rocketchat.token" })
	public RocketchatOAuthTokenRequestInterceptor basicAuthRequestInterceptor(
			@Value("${clients.rocketchat.userid}") String userid,
			@Value("${clients.rocketchat.token}") String token) {
		return new RocketchatOAuthTokenRequestInterceptor(userid, token);
	}
}