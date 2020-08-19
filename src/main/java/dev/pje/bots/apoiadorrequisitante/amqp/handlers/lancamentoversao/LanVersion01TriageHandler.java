package dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.event.JiraEventIssue.IssueEventTypeNameEnum;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;

@Component
public class LanVersion01TriageHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion01TriageHandler.class);
	
	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||01||TRIAGE|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	private static final String TRANSITION_PROPERTY_KEY_GERAR_RELEASE_CANDIDATE = "GERAR_RELEASE_CANDIDATE";
		
	/**
	 * Este handler verifica:
	 * - se quem abriu a issue tinha essa permissão
	 * -- se nao tinha, fecha a issue 
	 * - se há versão indicada
	 * -- se não, fecha a issue
	 * - se a versão já foi lançada
	 * -- se não, não tramita a issue, apenas lança um comentário nela
	 * - se a flag: "versao RC automatica" está marcada como true
	 * -- se sim, tramita automaticamente para "gerando release candidate"
	 * TODO - só permitir executar se o projeto do git implementar o gitflow
	 */
	@Override
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_NEW_VERSION) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_OPEN_ID) &&
					(JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_OPEN_ID) 
						|| jiraEventIssue.getIssueEventTypeName().equals(IssueEventTypeNameEnum.ISSUE_CREATED))) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());
				boolean gerarAutomaticamente = false;
				boolean versaoJaLancada = false;
				String versaoASerLancada = null;
				if (jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
					// valida se foi indicada uma versão afetada - caso contrário fecha a issue
					String versaoAfetada = JiraUtils.getVersaoAfetada(issue.getFields().getVersions());
					if(StringUtils.isNotBlank(versaoAfetada)) {						
						versaoASerLancada = issue.getFields().getVersaoSeraLancada();
						if(StringUtils.isBlank(versaoASerLancada)){
							versaoASerLancada = versaoAfetada;
						}
						
						String gitlabProjectId = jiraService.getGitlabProjectFromIssue(issue);
						// verifica se a versão a ser lançada já não foi lançada - identifica isso no gitlab, tentando identificar uma tag relacionada
						if(StringUtils.isNotBlank(gitlabProjectId)) {
							GitlabTag tag = gitlabService.getVersionTag(gitlabProjectId, versaoASerLancada);
							if(tag != null && tag.getCommit() != null) {
								versaoJaLancada = true;
								messages.info("A versão: " + versaoASerLancada + " já foi lançada");
							}
						}
						if(!versaoJaLancada) {
							if(issue.getFields().getGerarVersaoAutomaticamente() != null 
									&& !issue.getFields().getGerarVersaoAutomaticamente().isEmpty()) {
								
								if(issue.getFields().getGerarVersaoAutomaticamente().get(0).getValue().equalsIgnoreCase("Sim")) {
									gerarAutomaticamente = true;
									messages.info("Tramitando automaticamente para a geração da release candidate da versão: " + versaoASerLancada + " como solicitado.");
								}
							}
						}
					}else {
						messages.error("Não foi identificada uma versão afetada, ou há mais de uma indicação.");
					}
				}else {
					messages.error("O usuário [~" + jiraEventIssue.getUser().getName() + "] não tem permissão para criar esta issue.");
				}
									
				if(messages.hasSomeError()) {
					// tramita para o encerramento, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, JiraService.TRANSITION_PROPERTY_KEY_FINALIZAR_DEMANDA, true, true);
				}else if(gerarAutomaticamente) {
					// tramita automaticamente, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.atualizarVersaoASerLancada(issue, versaoASerLancada, updateFields);
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, TRANSITION_PROPERTY_KEY_GERAR_RELEASE_CANDIDATE, true, true);
				}else if(versaoJaLancada) {
					jiraService.sendTextAsComment(issue, messages.getMessagesToJira());
				}
			}
		}
	}
}