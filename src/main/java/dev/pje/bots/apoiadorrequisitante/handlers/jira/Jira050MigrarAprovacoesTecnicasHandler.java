package dev.pje.bots.apoiadorrequisitante.handlers.jira;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraUser;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;

@Component
public class Jira050MigrarAprovacoesTecnicasHandler extends Handler<String>{
	private static final Logger logger = LoggerFactory.getLogger(Jira050MigrarAprovacoesTecnicasHandler.class);
	
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
		return "|JIRA||050||MIGRAR-APROVACOES-MRs|";
	}
	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	private Map<String, JiraUser> usuariosRevisoresCache = new HashMap<>();

	private Map<String, String> tribunaisUsuariosCache = new HashMap<>();

	/**
	 * Buscar a lista de issues para migrar:
	 * - category in (PJE, PJE-CLOUD) AND project not in (EV, 10110, 11012) AND "Aprovado por" is not empty
	 * - buscar o campo "Aprovado por" e tentar identificar os usuários, com seus tribunais relacionados
	 * - armazenar nos campos corretos: "Tribunais responsáveis pela revisão" e "Responsáveis pela revisão"
	 * - recalcular o número de aprovações feitas
	 * - retirar os valores do campo "Aprovado por"
	 */
	@Override
	public void handle(String migrarDados) throws Exception {
		messages.clean();
		
		String jql = "category in (PJE, PJE-CLOUD) AND project not in (EV, 10110, 11012) AND \"Aprovado por\" is not empty";
		List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
		
		if(issues != null && !issues.isEmpty()) {
			for (JiraIssue issue : issues) {
				String aprovadoPor = issue.getFields().getAprovadoPor();
				String[] aprovadoPorArr = aprovadoPor.split("\n");
				
				List<JiraUser> responsaveisRevisao = new ArrayList<>();
				List<String> tribunaisRevisores = new ArrayList<>();
				if(aprovadoPorArr != null) {
					List<String> aprovadoPorList = Arrays.asList(aprovadoPorArr);
					for (String aprovador : aprovadoPorList) {
						JiraUser usuarioJira = identificaJiraUser(aprovador);
						if(usuarioJira != null) {
							if(!responsaveisRevisao.contains(usuarioJira)) {
								responsaveisRevisao.add(usuarioJira);

								// identifica o tribunal deste usuário
								String tribunalUsuario = identificaTribunal(aprovador, usuarioJira);
								if(tribunalUsuario != null && !tribunaisRevisores.contains(tribunalUsuario)) {
									tribunaisRevisores.add(tribunalUsuario);
								}
							}
						}else {
							messages.error("Não conseguiu encontrar o usuário: " + aprovador);
						}
					}
					// contabiliza a quantidade de aprovacoes realizadas
					atualizarDemanda(issue, responsaveisRevisao, tribunaisRevisores);
				}
			}
		}
	}
	
	private void atualizarDemanda(JiraIssue issue, List<JiraUser> responsaveisRevisao, List<String> tribunaisRevisores) throws Exception {
		Integer aprovacoesRealizadas = (tribunaisRevisores != null ? tribunaisRevisores.size() : 0);

		Map<String, Object> updateFields = new HashMap<>();

		// aprovacoes realizadas
		jiraService.atualizarAprovacoesRealizadas(issue, aprovacoesRealizadas, updateFields);
		// usuarios responsaveis aprovacoes
		jiraService.atualizarResponsaveisRevisao(issue, responsaveisRevisao, updateFields);
		// atualiza tribunais responsaveis aprovacoes
		jiraService.atualizarTribunaisRevisores(issue, tribunaisRevisores, updateFields);
		// apaga o campo: aprovado por
		jiraService.atualizarAprovadoPor(issue, "", updateFields);
		
		// adiciona os MR abertos
		String MRsAbertos = issue.getFields().getMrAbertos();
		String MrsAbertosConfirmados = gitlabService.checkMRsOpened(MRsAbertos);
		jiraService.atualizarMRsAbertos(issue, MrsAbertosConfirmados, updateFields, true);
		
		if(updateFields != null && !updateFields.isEmpty()) {
			enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_EDICAO_AVANCADA, false, false);
		}
	}

	private String identificaTribunal(String aprovador, JiraUser usuarioAprovador) {
		String tribunal = null;
		if(usuarioAprovador != null) {
			String username = usuarioAprovador.getName();
			String tribunalUsuario = null;
			if(tribunaisUsuariosCache.get(username) == null) {
				tribunalUsuario = jiraService.getTribunalUsuario(usuarioAprovador, false);
				if(StringUtils.isNotBlank(tribunalUsuario) && StringUtils.isNotBlank(aprovador) && !aprovador.contains(tribunalUsuario)) {
					String[] dadosAprovador = aprovador.split("\\(");
					if(dadosAprovador.length > 1) {
						String label = dadosAprovador[1].trim();
						label = label.replaceAll(GitlabService.PREFIXO_LABEL_APROVACAO_TRIBUNAL, "");
						label = label.replaceAll("\\)", "");
						label = label.trim();
						if(StringUtils.isNotBlank(label) && !label.equals("TRE")) {
							tribunalUsuario = label;
						}
					}
				}
				tribunaisUsuariosCache.put(username, tribunalUsuario);
			}
			tribunal = tribunaisUsuariosCache.get(username);
		}
		
		return tribunal;
	}
	
	private JiraUser identificaJiraUser(String aprovador) {
		JiraUser usuarioJira = null;
		String[] dadosAprovador = aprovador.split("\\(");
		if(dadosAprovador.length > 0) {
			String nomeUsuario = dadosAprovador[0].trim();
			if(StringUtils.isNotBlank(nomeUsuario) && nomeUsuario.length() > 2) {
				String username = limparUsername(dadosAprovador[0]);
				
				if(usuariosRevisoresCache.get(username) == null) {
					usuarioJira = jiraService.findUserByUserName(username);
					if(usuarioJira == null) {
						GitlabUser usuarioGitlab = gitlabService.findUserByEmail(username);
						if(usuarioGitlab != null) {
							usuarioJira = jiraService.getJiraUserFromGitlabUser(usuarioGitlab);
						}else {
							messages.error("Nao conseguiu encontrar o usuário: " + username);
						}
					}
					usuariosRevisoresCache.put(username, usuarioJira);
				}
				usuarioJira = usuariosRevisoresCache.get(username);
			}
		}
		
		return usuarioJira;
	}
	
	private String limparUsername(String usernameOrig) {
		String username = usernameOrig;
//		if(StringUtils.isNotBlank(username) && username.split(" ").length > 1) {
//			String[] usernameArr = username.split(" ");
//			username = usernameArr[0];
//		}
		
		if(username.endsWith(" ") || username.endsWith("\n")) {
			username = username.trim();
		}
		messages.info("Teste de nome: ["+username+"]");
		username = username.replaceAll("Aprovado por", "");
		username = username.replaceAll("\\r", "");
		username = username.replaceAll("\\n", "");
		username = username.replaceAll("\\]", "");
		username = username.replaceAll("\\[", "");
		username = username.replaceAll("\\~", "");
		
		return username;
	}
}