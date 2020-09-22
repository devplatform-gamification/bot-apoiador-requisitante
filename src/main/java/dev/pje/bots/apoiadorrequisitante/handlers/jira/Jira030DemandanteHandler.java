package dev.pje.bots.apoiadorrequisitante.handlers.jira;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueFieldOption;
import com.devplatform.model.jira.JiraProject;
import com.devplatform.model.jira.JiraProperty;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;

@Component
public class Jira030DemandanteHandler extends Handler<JiraEventIssue>{
	private static final Logger logger = LoggerFactory.getLogger(Jira030DemandanteHandler.class);
	
	@Value("${clients.gitlab.url}")
	private String gitlabUrl;
	
	@Value("${clients.jira.user}")
	private String jiraBotUser;
	
	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|JIRA||030||DEMANDANTE|";
	}
	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	/**
	 * ok - Verifica se a issue está em um status de "demandante"
	 * ok - Verifica se quem fez a ação é alguém de um tribunal que esteja na lista de "Tribunais requisitantes"
	 * ok - ou verifica se quem fez a ação é quem abriu a issue
	 * ok- em caso positivo: busca uma transição com a propriedade: RESPONDIDO + altera o responsável pela issue para o usuário responsável pelo projeto
	 * 
	 */
	@Override
	public void handle(JiraEventIssue issueEvent) throws Exception {
		messages.clean();
		if(issueEvent != null && issueEvent.getIssue() != null && issueEvent.getIssue().getFields() != null 
				&& issueEvent.getIssue().getFields().getStatus() != null && issueEvent.getIssue().getFields().getStatus().getId() != null 
				&& issueEvent.getUser() != null) {
			JiraIssue issue = issueEvent.getIssue();
			String statusId = issue.getFields().getStatus().getId().toString();
			JiraUser usuarioAcao = issueEvent.getUser();
			// verifica se a issue está no status do demandante
			JiraProperty propriedadeStatusDemandante 
				= jiraService.getIssueStatusProperty(issue.getKey(), statusId, JiraService.STATUS_PROPERTY_KEY_DEMANDANTE);
			if(propriedadeStatusDemandante != null) {
				issue = jiraService.recuperaIssueDetalhada(issue.getKey());
				boolean execcutorEhDemandante = false;
				// verifica se o usuário que fez a ação é quem criou a issue
				if (issue.getFields().getReporter() != null && StringUtils.isNotBlank(issue.getFields().getReporter().getKey()) && 
						 issue.getFields().getReporter().getKey().equals(usuarioAcao.getKey())) {
					execcutorEhDemandante = true;
				}else {
					// verifica se o usuário que execcutou a ação é de um dos tribunais listados em "tribunais requisitantes"
					String tribunalUsuarioAcao = jiraService.getTribunalUsuario(usuarioAcao, false);
					if(StringUtils.isNotBlank(tribunalUsuarioAcao)) {
						// verifica se esse tribunal está na lista de tribunais da issue
						List<JiraIssueFieldOption> tribunaisRequisitantes = jiraService.getTribunaisRequisitantes(issue);

						if (tribunaisRequisitantes != null) {
							for (JiraIssueFieldOption tribunal : tribunaisRequisitantes) {
								if (tribunal.getValue().equals(tribunalUsuarioAcao)) {
									execcutorEhDemandante = true;
									break;
								}
							}
						}
					}
				}
				if(execcutorEhDemandante) {
					Map<String, Object> updateFields = new HashMap<>();

					// identifica qual é o usuário lider do projeto e devolve a issue para ele
					String projectKey = issue.getFields().getProject().getKey();
					JiraProject project = jiraService.getProject(projectKey);
					if(project != null && project.getLead() != null) {
						JiraUser liderProjeto = project.getLead();						
						jiraService.atualizarResponsavelIssue(issue, liderProjeto, updateFields);
					}
					
					enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_RESPONDIDO, false, false);
				}
			}
		}
	}
}