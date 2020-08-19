package dev.pje.bots.apoiadorrequisitante.amqp.handlers.jira;

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
import com.devplatform.model.jira.request.JiraIssueCreateAndUpdate;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;

@Component
public class Jira02ApoiadorRequisitanteHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(Jira02ApoiadorRequisitanteHandler.class);

	@Autowired
	private JiraService jiraService;

	@Autowired
	private TelegramService telegramService;

	public void handle(JiraEventIssue jiraEventIssue) {
		telegramService.sendBotMessage("|JIRA||02||REQUISITANTE| - " + jiraEventIssue.getIssue().getKey() + " - " + jiraEventIssue.getIssueEventTypeName().name());
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
					JiraIssueTransition edicaoAvancada = jiraService.findTransitionByIdOrNameOrPropertyKey(issue, JiraService.TRANSICTION_DEFAULT_EDICAO_AVANCADA);
					if(edicaoAvancada != null) {
						JiraIssueCreateAndUpdate jiraIssueCreateAndUpdate = new JiraIssueCreateAndUpdate();
						jiraIssueCreateAndUpdate.setTransition(edicaoAvancada);
						jiraIssueCreateAndUpdate.setUpdate(updateFields);

						jiraService.updateIssue(issue, jiraIssueCreateAndUpdate);
						telegramService.sendBotMessage("|JIRA||02||REQUISITANTE|[" + issue.getKey() + "] Issue atualizada");
						logger.info("Issue atualizada");
					}else {
						telegramService.sendBotMessage("*|JIRA||02||REQUISITANTE|[" + issue.getKey() + "] Erro!!* \n Não há transição para realizar esta alteração");
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