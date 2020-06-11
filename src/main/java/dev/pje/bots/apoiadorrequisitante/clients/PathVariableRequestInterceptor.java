package dev.pje.bots.apoiadorrequisitante.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class PathVariableRequestInterceptor implements RequestInterceptor {

  private final String token;

  public PathVariableRequestInterceptor(String token) {
	    this.token = token;
  }
  
  @Override
  public void apply(RequestTemplate template) {
	  String url = template.url();
	  String urlWithPathToken = "/" + token + url;
	  template.uri(urlWithPathToken, false);
  }
}

