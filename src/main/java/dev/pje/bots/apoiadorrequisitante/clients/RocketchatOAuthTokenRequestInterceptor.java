package dev.pje.bots.apoiadorrequisitante.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class RocketchatOAuthTokenRequestInterceptor implements RequestInterceptor {

  private final String token;
  private final String userid;

  public RocketchatOAuthTokenRequestInterceptor(String userid, String token) {
	    this.userid = userid;
	    this.token = token;
  }
  
  @Override
  public void apply(RequestTemplate template) {
    template.header("X-Auth-Token", this.token);
    template.header("X-User-Id", this.userid);
  }
}

