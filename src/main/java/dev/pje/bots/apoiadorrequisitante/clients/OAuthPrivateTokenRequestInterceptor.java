package dev.pje.bots.apoiadorrequisitante.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class OAuthPrivateTokenRequestInterceptor implements RequestInterceptor {

  private final String headerValue;

  public OAuthPrivateTokenRequestInterceptor(String token) {
	    this.headerValue = token;
  }
  
  @Override
  public void apply(RequestTemplate template) {
    template.header("PRIVATE-TOKEN", headerValue);
  }
}

