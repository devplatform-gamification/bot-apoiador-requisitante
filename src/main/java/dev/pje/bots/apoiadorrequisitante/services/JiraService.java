package dev.pje.bots.apoiadorrequisitante.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.devplatform.model.jira.JiraGroup;
import com.devplatform.model.jira.JiraGroups;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueComment;
import com.devplatform.model.jira.JiraIssueFieldOption;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraIssueTransitions;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;
import com.devplatform.model.jira.request.fields.JiraComment;
import com.devplatform.model.jira.request.fields.JiraMultiselect;
import com.fasterxml.jackson.core.JsonProcessingException;

import dev.pje.bots.apoiadorrequisitante.clients.JiraClient;

@Service
public class JiraService {

	private static final Logger logger = LoggerFactory.getLogger(JiraService.class);

	@Autowired
	private JiraClient jiraClient;

//	@Autowired
//	private JiraClientDebug jiraClientDebug;
	
	@Value("${clients.jira.url}") 
	private String jiraUrl;
	
	private static final String PREFIXO_GRUPO_TRIBUNAL = "PJE_TRIBUNAL_";
	public static final String TRANSICION_DEFAULT_EDICAO_AVANCADA = "Edição avançada";

//	public void teste() {
//		logger.info("Trying to get user information from service JIRA: ");
//		if (jiraClient != null) {
//			Object usuarioLogadoJira = jiraClient.whoami();
//			logger.info(usuarioLogadoJira.toString());
//
//			Map<String, String> options = new HashMap<>();
//			options.put("expand", "groups");
//			options.put("username", "zeniel.chaves");
//			JiraUser usuario = jiraClient.getUserDetails(options);
//			logger.info(usuario.toString());
//		} else {
//			logger.error("client feign not initialized");
//		}
//		new Exception("erro para não permitir retirar mensagem do rabbit");
//	}

	public JiraGroup getGrupoTribunalUsuario(JiraUser user) {
		JiraGroup tribunal = null;
		try {
			JiraGroups grupos = user.getGroups();
			if (grupos == null) {
				String username = user.getName();
				Map<String, String> options = new HashMap<>();
				options.put("expand", "groups");
				options.put("username", username);

				grupos = jiraClient.getUserDetails(options).getGroups();
			}
			for (JiraGroup grupoUsuario : grupos.getItems()) {
				if (grupoUsuario.getName().startsWith(PREFIXO_GRUPO_TRIBUNAL)) {
					tribunal = grupoUsuario;
					break;
				}
			}
		} catch (Exception e) {
			// ignore
		}

		return tribunal;
	}

	public String getTribunalUsuario(JiraUser user) {
		String tribunal = null;
		JiraGroup grupoTribunal = getGrupoTribunalUsuario(user);
		if (grupoTribunal != null) {
			tribunal = grupoTribunal.getName().replaceAll(PREFIXO_GRUPO_TRIBUNAL, "");
		}
		if(!StringUtils.isBlank(tribunal) && tribunal.equalsIgnoreCase("CNJ")) {
			tribunal = null;
		}
		return tribunal;
	}

	/**
	 * 1. recupera a issue do jira, para saber a informação atualizada do campo:
	 * tribunais requisitantes 2. identifica qual é a transição que deverá ser
	 * utilizada 3. monta o payload 4. encaminha o payload da alteraco
	 * @throws Exception 
	 */
	public void adicionaTribunalRequisitante(JiraIssue issue, String tribunalRequisitante, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(tribunalRequisitante)) {
			List<String> listaTribunaisAtualizada = new ArrayList<>();

			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
			List<JiraIssueFieldOption> tribunaisRequisitantes = getTribunaisRequisitantes(issueDetalhada);
			listaTribunaisAtualizada.add(tribunalRequisitante);
			boolean tribunalConstaComoRequisitante = false;
			if (tribunaisRequisitantes != null) {
				for (JiraIssueFieldOption tribunal : tribunaisRequisitantes) {
					listaTribunaisAtualizada.add(tribunal.getValue());
					if (tribunal.getValue().equals(tribunalRequisitante)) {
						tribunalConstaComoRequisitante = true;
						break;
					}
				}
			}
			if (!tribunalConstaComoRequisitante) {
				String fieldName = "customfield_11700";
				Map<String, Object> updateField = createUpdateObject(fieldName, listaTribunaisAtualizada, "ADD");
				if(updateField != null && updateField.get(fieldName) != null) {
					updateFields.put(fieldName, updateField.get(fieldName));
				}
			}
		}
	}
	
	public void adicionarComentario(JiraIssue issue, String texto, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(texto)) {
			String fieldName = "comment";
			Map<String, Object> updateField = createUpdateObject(fieldName, texto, "ADD");
			if(updateField != null && updateField.get(fieldName) != null) {
				updateFields.put(fieldName, updateField.get(fieldName));
			}
		}
	}
	
	// TODO - alterar para saber como deve ser montado o objeto de acordo com a especificação do campo no serviço
	// http://www.cnj.jus.br/jira/rest/api/2/field - utilizando o schema
	@SuppressWarnings("unchecked")
	private Map<String, Object> createUpdateObject(String fieldName, Object valueToUpdate, String operation) throws Exception{
		Map<String, Object> objectToUpdate = new HashMap<>();
		
		if("customfield_11700".equals(fieldName)) {
			boolean identificouCampo = false;
			if(valueToUpdate instanceof List<?>) {
				List<?> valueToUpdateList = (List<?>) valueToUpdate;
				if(valueToUpdateList.isEmpty() || valueToUpdateList.get(0) instanceof String) {
					identificouCampo = true;
					List<JiraMultiselect> items = new ArrayList<JiraMultiselect>();
					JiraMultiselect item;
					if(valueToUpdateList.isEmpty()) {
						item = new JiraMultiselect(null);
					}else {
						item = new JiraMultiselect((List<String>) valueToUpdate);
					}
					items.add(item);
					
					objectToUpdate.put(fieldName, items);
				}
			}
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser List<String>, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if("comment".equals(fieldName) && valueToUpdate != null) {
			boolean identificouCampo = false;
			if(valueToUpdate instanceof String) {
				identificouCampo = true;
				JiraComment comment = new JiraComment((String) valueToUpdate);
				objectToUpdate.put(fieldName, comment.getComment());
			}
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser String, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}
		
		return objectToUpdate;
	}
	
	public JiraIssueTransition findTransicao(JiraIssue issue, String nomeTransicao) {
		JiraIssueTransition transicaoEncontrada = null;
		JiraIssueTransitions transicoes = this.recuperarTransicoesIssue(issue);
		for (JiraIssueTransition transicao : transicoes.getTransitions()) {
			if(transicao.getName().equalsIgnoreCase(nomeTransicao)) {
				transicaoEncontrada = transicao;
				break;
			}
		}
		
		return transicaoEncontrada;
	}
	
	public String getJqlIssuesPendentesTribunalRequisitante(String tribunal) {
		String jql = jiraUrl + "/issues/?jql=cf%5B11700%5D%20in%20("+tribunal+")%20AND%20status%20not%20in%20(Fechado%2C%20Resolvido)";
		return jql;
	}
	
	public void updateIssue(JiraIssue issue, JiraIssueTransitionUpdate issueUpdate) throws JsonProcessingException {
		String issueKey = issue.getKey();
		jiraClient.changeIssueWithTransition(issueKey, issueUpdate);
	}
	
	public JiraIssueTransitions recuperarTransicoesIssue(JiraIssue issue) {
		String issueKey = issue.getKey();
		return jiraClient.getIssueTransitions(issueKey);
	}

	@Cacheable("issue")
	public JiraIssue recuperaIssueDetalhada(JiraIssue issue) {
		String issueKey = issue.getKey();
		Map<String, String> options = new HashMap<>();
		options.put("expand", "operations");
		return jiraClient.getIssueDetails(issueKey, options);
	}

	public JiraUser getCommentAuthor(JiraIssueComment comment) {
		JiraUser author = null;
		try {
			author = comment.getAuthor();
		} catch (Exception e) {
			// ignora
		}
		return author;
	}

	public List<JiraIssueFieldOption> getTribunaisRequisitantes(JiraIssue issue) {
		List<JiraIssueFieldOption> tribunaisRequisitantes = null;
		try {
			tribunaisRequisitantes = issue.getFields().getTribunalRequisitante();
		} catch (Exception e) {
			// ignora
		}
		return tribunaisRequisitantes;
	}

	public JiraUser getIssueReporter(JiraIssue issue) {
		JiraUser reporter = null;
		try {
			reporter = issue.getFields().getReporter();
		} catch (Exception e) {
			// ignora
		}
		return reporter;
	}

	public JiraUser getIssueAssignee(JiraIssue issue) {
		JiraUser assignee = null;
		try {
			assignee = issue.getFields().getAssignee();
		} catch (Exception e) {
			// ignora
		}
		return assignee;
	}
}