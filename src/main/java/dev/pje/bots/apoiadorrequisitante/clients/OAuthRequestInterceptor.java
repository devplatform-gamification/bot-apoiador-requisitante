package dev.pje.bots.apoiadorrequisitante.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class OAuthRequestInterceptor implements RequestInterceptor {

  private final String headerValue;

  public OAuthRequestInterceptor(String token) {
	    this.headerValue = "Bearer " + token;
  }
  
  @Override
  public void apply(RequestTemplate template) {
    template.header("Authorization", headerValue);
  }
}

