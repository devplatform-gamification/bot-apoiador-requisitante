package dev.pje.bots.apoiadorrequisitante.clients;

import feign.RequestInterceptor;
import feign.RequestTemplate;

public class JiraAttachmentRequestInterceptor implements RequestInterceptor {

  private final String atlassianToken;

  public JiraAttachmentRequestInterceptor() {
	    this.atlassianToken = "no-check";
  }
  
  @Override
  public void apply(RequestTemplate template) {
    template.header("X-Atlassian-Token", atlassianToken);
  }
}

