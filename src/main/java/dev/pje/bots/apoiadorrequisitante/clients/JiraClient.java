package dev.pje.bots.apoiadorrequisitante.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name = "jira", url = "${clients.jira.url}", configuration = JiraClientConfiguration.class)
public interface JiraClient {
	@RequestMapping(method = RequestMethod.GET, value = "/rest/auth/1/session", consumes = "application/json")
	public Object obterUsuarioLogado();
}
