package dev.pje.bots.apoiadorrequisitante.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.pje.bots.apoiadorrequisitante.clients.JiraClient;

@Service
public class JiraService {

	private static final Logger logger = LoggerFactory.getLogger(JiraService.class);

	@Autowired
	private JiraClient jiraClient;

    public void teste() {
		logger.info("Trying to get user information from service JIRA: ");
		if(jiraClient != null) {
			
//			String jiraUrl = "http://www.cnj.jus.br/jira";
//		    String username = "zeniel.chaves";
//		    String password = "Rotaro#01";
//
//	        BasicAuthRequestInterceptor interceptor = new BasicAuthRequestInterceptor(username, password);
//	        JiraClient jiraClient = Feign.builder().encoder(new JacksonEncoder())
//	                .decoder(new JacksonDecoder()).requestInterceptor(interceptor)
//	                .target(JiraClient.class, jiraUrl);
	        
			Object usuarioLogadoJira = jiraClient.obterUsuarioLogado();
			logger.info(usuarioLogadoJira.toString());
		}else {
			logger.error("client feign not initialized");
		}
    }
}
