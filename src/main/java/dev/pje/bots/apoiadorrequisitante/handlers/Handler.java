package dev.pje.bots.apoiadorrequisitante.handlers;

import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.request.JiraIssueCreateAndUpdate;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.RocketchatService;
import dev.pje.bots.apoiadorrequisitante.services.SlackService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Component
public abstract class Handler<E> {

	abstract protected Logger getLogger();

	abstract public String getMessagePrefix();

	abstract public int getLogLevel();

	@Autowired
	protected JiraService jiraService;

	@Autowired
	protected GitlabService gitlabService;

	@Autowired
	protected TelegramService telegramService;

	@Autowired
	protected SlackService slackService;

	@Autowired
	protected RocketchatService rocketchatService;

	protected MessagesLogger messages;

	@PostConstruct
	public void init() {
		messages = new MessagesLogger();
		messages.setLogger(getLogger());
		messages.setMessagePrefix(getMessagePrefix());
		messages.setLogLevel(getLogLevel());
	}

	public abstract void handle(E event) throws Exception;

	/**
	 * 
	 * @param issue
	 * @param updateFields
	 * @param transictionIdOrNameOrPropertyKey - nome, id ou propriedade da transição
	 * @param usarEdicaoAvancada - se nào enccontrar a transição, tenta encontrar a transição de "Edição avançada"
	 * @param enviarComentario - se não enontrar nenhuma transição, envia as informações como um comentário
	 * @throws Exception
	 */
	protected void enviarAlteracaoJira(JiraIssue issue, Map<String, Object> updateFields, Map<String, Object> createFields, String transictionIdOrNameOrPropertyKey, 
			boolean usarEdicaoAvancada, boolean enviarComentario) throws Exception{

		JiraIssueTransition transition = null;
		JiraIssueCreateAndUpdate jiraIssueCreateAndUpdate = new JiraIssueCreateAndUpdate();
		if(createFields != null && !createFields.isEmpty()) {
			jiraIssueCreateAndUpdate.setFields(createFields);
		}
		if(updateFields != null && !updateFields.isEmpty()) {
			jiraIssueCreateAndUpdate.setUpdate(updateFields);
		}

		if(StringUtils.isNotBlank(transictionIdOrNameOrPropertyKey)) {
			transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, transictionIdOrNameOrPropertyKey);
			if(transition == null && usarEdicaoAvancada) {
				messages.info("Não foi identificada uma saída padrão desta issue. ");
				messages.info("Buscando transição '" + JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA + "'");
				transition = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA);
			}
			if(transition == null) {
				if(enviarComentario) {
					messages.info("Não foi identificada uma transição válida para registro da nova informação, os dados relacionados"
							+ " serão registrados na issue como comentário.");
				}else {
					messages.error("Não há transição para realizar esta alteração: " + transictionIdOrNameOrPropertyKey);
				}
			}
			if(transition != null) {
				jiraIssueCreateAndUpdate.setTransition(transition);
			}
		}
		if(transition == null && enviarComentario) {
			String comment = updateFields.toString();
			if(StringUtils.isNotBlank(comment)) {
				jiraService.sendTextAsComment(issue, comment);									
			}
		}else {
			String msg = "update string: " + Utils.convertObjectToJson(jiraIssueCreateAndUpdate);
			messages.debug(msg);
			getLogger().info(msg);
			try {
				jiraService.updateIssue(issue, jiraIssueCreateAndUpdate);
				messages.info("Issue atualizada");
			}catch (Exception e) {
				messages.error("Falhou ao tentar atualizar a issue: " + issue.getKey());
			}
		}
	}

	protected JiraIssue enviarCriacaoJiraIssue(Map<String, Object> createFields, Map<String, Object> updateFields) throws Exception{
		JiraIssue issue = null;
		if(createFields != null && !createFields.isEmpty()) {
			JiraIssueCreateAndUpdate jiraIssueCreateAndUpdate = new JiraIssueCreateAndUpdate();
			jiraIssueCreateAndUpdate.setFields(createFields);
			jiraIssueCreateAndUpdate.setUpdate(updateFields);
			String msg = "create issue string: " + Utils.convertObjectToJson(jiraIssueCreateAndUpdate);
			messages.debug(msg);
			getLogger().info(msg);
			issue = jiraService.createIssue(jiraIssueCreateAndUpdate);
		}
		if(issue != null) {
			messages.info("Issue criada: " + issue.getKey());
		}else {
			messages.error("Não foi criada a nova issue");
		}
		return issue;
	}

}