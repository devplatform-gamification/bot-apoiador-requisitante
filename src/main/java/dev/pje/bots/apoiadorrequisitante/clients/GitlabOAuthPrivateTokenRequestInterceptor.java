package dev.pje.bots.apoiadorrequisitante.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class GitlabOAuthPrivateTokenRequestInterceptor implements RequestInterceptor {

  private final String headerValue;

  public GitlabOAuthPrivateTokenRequestInterceptor(String token) {
	    this.headerValue = token;
  }
  
  @Override
  public void apply(RequestTemplate template) {
    template.header("PRIVATE-TOKEN", headerValue);
  }
}

