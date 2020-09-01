package dev.pje.bots.apoiadorrequisitante.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class SlackOAuthRequestInterceptor implements RequestInterceptor {

  private final String headerValue;

  public SlackOAuthRequestInterceptor(String token) {
	    this.headerValue = "Bearer " + token;
  }
  
  @Override
  public void apply(RequestTemplate template) {
    template.header("Authorization", headerValue);
  }
}

