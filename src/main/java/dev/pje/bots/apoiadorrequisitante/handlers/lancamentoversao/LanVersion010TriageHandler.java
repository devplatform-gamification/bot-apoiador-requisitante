package dev.pje.bots.apoiadorrequisitante.handlers.lancamentoversao;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.event.JiraEventIssue.IssueEventTypeNameEnum;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;

@Component
public class LanVersion010TriageHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion010TriageHandler.class);
	
	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||010||TRIAGE|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	@Value("${project.documentation.url}")
	private String DOCSURL;

	private static final String TRANSITION_PROPERTY_KEY_PREPARAR_VERSAO_ATUAL = "PREPARAR_VERSAO_ATUAL";
	private static final String TRANSITION_PROPERTY_KEY_GERAR_RELEASE_NOTES = "GERAR_RELEASE_NOTES";
		
	/**
	 * Este handler verifica:
	 * - se quem abriu a issue tinha essa permissão
	 * -- se nao tinha, fecha a issue 
	 * - se há versão indicada
	 * -- se não, fecha a issue
	 * - se a versão já foi lançada
	 * -- se não, não tramita a issue, apenas lança um comentário nela
	 * - se a flag: "preparar versão atual automaticamente" está marcada como true
	 * -- verifica se há versão lançada:
	 * --- se sim, tramita para geração do release notes
	 * --- se não, tramita para: "preparar versão atual"
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
									messages.info("Tramitando automaticamente para a geração da release candidate/preparação para a versão: " + versaoASerLancada + " como solicitado.");
								}
							}
						}
					}else {
						messages.error("Não foi identificada uma versão afetada, ou há mais de uma indicação.");
					}
				}else {
					messages.error("O usuário [~" + jiraEventIssue.getUser().getName() + "] não tem permissão para criar esta issue.");
				}
				
				// adicionar à issue a URL das releases do projeto em questão
				String urlReleaseNotes = DOCSURL;
				if(issue.getFields() != null && issue.getFields().getProject() != null && StringUtils.isNotBlank(issue.getFields().getProject().getKey())) {
					urlReleaseNotes = jiraService.getProjectDocumentationUrl(issue.getFields().getProject().getKey());					
				}
									
				if(messages.hasSomeError()) {
					// tramita para o encerramento, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_FINALIZAR_DEMANDA, true, true);
				}else if(gerarAutomaticamente) {
					// tramita automaticamente, enviando as mensagens nos comentários
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.atualizarURLPublicacao(issue, urlReleaseNotes, updateFields);
					jiraService.atualizarVersaoASerLancada(issue, versaoASerLancada, updateFields);
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, null, TRANSITION_PROPERTY_KEY_PREPARAR_VERSAO_ATUAL, true, true);
				}else if(versaoJaLancada) {
					// nesta situação - encaminhar para geração do release notes
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					jiraService.atualizarURLPublicacao(issue, urlReleaseNotes, updateFields);
					enviarAlteracaoJira(issue, updateFields, null, TRANSITION_PROPERTY_KEY_GERAR_RELEASE_NOTES, true, true);
				}
			}
		}
	}
}