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

@Component
public class JiraEventHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(JiraEventHandler.class);

	@Autowired
	private JiraService jiraService;
	
	public void handle(JiraEventIssue jiraEventIssue) {
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
		}
		
		adicionarTribunalRequisitanteDemanda(jiraEventIssue.getIssue(), tribunalUsuario, usuarioAcao, JiraWebhookEventEnum.ISSUE_UPDATED);
	}
	
	private void adicionarTribunalRequisitanteDemanda(JiraIssue issue, String tribunal, JiraUser usuario, JiraWebhookEventEnum tipoInclusao) {
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
				JiraIssueTransition edicaoAvancada = jiraService.findTransicao(issue, JiraService.TRANSICION_DEFAULT_EDICAO_AVANCADA);
				JiraIssueTransitionUpdate issueTransitionUpdate = new JiraIssueTransitionUpdate(edicaoAvancada, updateFields);
				jiraService.updateIssue(issue, issueTransitionUpdate);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private boolean verificaSeRequisitouIssue(JiraIssueComment comment) {
		// TODO - verificar se todo comentário de alguém na issue indica que o seu tribunal está interessado, ou se é necessário avaliar alguma expressão específica
		return Boolean.TRUE;
	}
}