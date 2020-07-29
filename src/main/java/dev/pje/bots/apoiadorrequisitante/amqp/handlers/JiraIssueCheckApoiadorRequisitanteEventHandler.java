package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueComment;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.event.JiraWebhookEventEnum;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;

@Component
public class JiraIssueCheckApoiadorRequisitanteEventHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(JiraIssueCheckApoiadorRequisitanteEventHandler.class);

	@Autowired
	private JiraService jiraService;

	@Autowired
	private TelegramService telegramService;

	public void handle(JiraEventIssue jiraEventIssue) {
		telegramService.sendBotMessage("[REQUISITANTE][JIRA] - " + jiraEventIssue.getIssue().getKey() + " - " + jiraEventIssue.getIssueEventTypeName().name());
		JiraUser reporter = jiraService.getIssueReporter(jiraEventIssue.getIssue());
		String tribunalUsuario = jiraService.getTribunalUsuario(reporter);
		adicionarTribunalRequisitanteDemanda(
				jiraEventIssue.getIssue(), tribunalUsuario, reporter, JiraWebhookEventEnum.ISSUE_CREATED);
		
		JiraUser usuarioAcao = null;
		if(jiraEventIssue.getWebhookEvent() == JiraWebhookEventEnum.ISSUE_UPDATED) {
			if(jiraEventIssue.getComment() != null) {
				if(this.verificaSeRequisitouIssue(jiraEventIssue.getComment())) {
					usuarioAcao = jiraService.getCommentAuthor(jiraEventIssue.getComment());
				}
			}
			if(usuarioAcao == null) {
				usuarioAcao = jiraService.getIssueAssignee(jiraEventIssue.getIssue());
			}
			String tribunalUsuarioAcao = jiraService.getTribunalUsuario(usuarioAcao);
			if(StringUtils.isNotBlank(tribunalUsuarioAcao)) {
				tribunalUsuario = tribunalUsuarioAcao;
			}
			adicionarTribunalRequisitanteDemanda(jiraEventIssue.getIssue(), tribunalUsuario, usuarioAcao, JiraWebhookEventEnum.ISSUE_UPDATED);
		}
	}
	
	private void adicionarTribunalRequisitanteDemanda(JiraIssue issue, String tribunal, JiraUser usuario, JiraWebhookEventEnum tipoInclusao) {
		if(tribunal != null) {
			Map<String, Object> updateFields = new HashMap<>();
			try {
				jiraService.adicionaTribunalRequisitante(issue, tribunal, updateFields);
				if(!updateFields.isEmpty()) {
					String linkTribunal = "[" + tribunal +"|" + jiraService.getJqlIssuesPendentesTribunalRequisitante(tribunal) + "]";
					String textoInclusao = "Incluindo "+ linkTribunal +" automaticamente como requisitante desta demanda.";
					if(tipoInclusao.equals(JiraWebhookEventEnum.ISSUE_UPDATED)) {
						textoInclusao = "Incluindo " + linkTribunal +" como requisitante desta demanda de acordo com a participação de: [~" + usuario.getName() + "]";
					}
					jiraService.adicionarComentario(issue,textoInclusao, updateFields);
					JiraIssueTransition edicaoAvancada = jiraService.findTransitionByName(issue, JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA);
					if(edicaoAvancada != null) {
						JiraIssueTransitionUpdate issueTransitionUpdate = new JiraIssueTransitionUpdate(edicaoAvancada, updateFields);
						jiraService.updateIssue(issue, issueTransitionUpdate);
						telegramService.sendBotMessage("[REQUISITANTE][" + issue.getKey() + "] Issue atualizada");
						logger.info("Issue atualizada");
					}else {
						telegramService.sendBotMessage("*[REQUISITANTE][" + issue.getKey() + "] Erro!!* \n Não há transição para realizar esta alteração");
						logger.error("Não há transição para realizar esta alteração");
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private boolean verificaSeRequisitouIssue(JiraIssueComment comment) {
		// TODO - verificar se todo comentário de alguém na issue indica que o seu tribunal está interessado, ou se é necessário avaliar alguma expressão específica
		// TODO - o ideal é treinar um modelo de ML com os comentários que significam solicitar interesse na demanda
		return comment.getBody().toLowerCase().contains("atribuir") || 
				comment.getBody().toLowerCase().contains("interesse");
	}
}