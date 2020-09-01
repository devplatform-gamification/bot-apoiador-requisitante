package dev.pje.bots.apoiadorrequisitante.clients;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;

import dev.pje.bots.apoiadorrequisitante.annotations.DecodeSlash;
import feign.MethodMetadata;
import feign.httpclient.ApacheHttpClient;

public class GitlabClientConfiguration {
	
	/***
	 * Method to allow @PathVariable with filepath and slashs
	 * @return
	 */
	@Bean
	public SpringMvcContract decodeSlashContract() {
		return new SpringMvcContract() {
			@Override
			protected void processAnnotationOnMethod(MethodMetadata data, Annotation methodAnnotation, Method method) {
				super.processAnnotationOnMethod(data, methodAnnotation, method);
				if (DecodeSlash.class.isInstance(methodAnnotation)) {
					DecodeSlash decodeSlash = (DecodeSlash) methodAnnotation;
					data.template().decodeSlash(decodeSlash.value());
				}
			}
		};
	}

	@Bean
	public ApacheHttpClient client() {
		return new ApacheHttpClient();
	}

	@Bean
	@ConditionalOnProperty(name = { "clients.gitlab.token" })
	public GitlabOAuthPrivateTokenRequestInterceptor oauth2FeignRequestInterceptor(
			@Value("${clients.gitlab.token}") String token) {
		return new GitlabOAuthPrivateTokenRequestInterceptor(token);
	}
}