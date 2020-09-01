package dev.pje.bots.apoiadorrequisitante.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class TelegramPathVariableRequestInterceptor implements RequestInterceptor {

  private final String token;

  public TelegramPathVariableRequestInterceptor(String token) {
	    this.token = token;
  }
  
  @Override
  public void apply(RequestTemplate template) {
	  String url = template.url();
	  String urlWithPathToken = "/" + token + url;
	  template.uri(urlWithPathToken, false);
  }
}

