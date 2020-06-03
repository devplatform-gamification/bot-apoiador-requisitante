package dev.pje.bots.apoiadorrequisitante.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import feign.auth.BasicAuthRequestInterceptor;
import feign.okhttp.OkHttpClient;

public class JiraClientConfiguration {

	@Bean
	public OkHttpClient client() {
		return new OkHttpClient();
	}

	@Bean
	@ConditionalOnProperty(name = { "clients.jira.user", "clients.jira.pass" })
	public BasicAuthRequestInterceptor basicAuthRequestInterceptor(
			@Value("${clients.jira.user}") String username,
			@Value("${clients.jira.pass}") String pass) {
		return new BasicAuthRequestInterceptor(username, pass);
	}
}