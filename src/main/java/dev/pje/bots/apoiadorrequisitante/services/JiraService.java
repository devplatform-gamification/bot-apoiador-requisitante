package dev.pje.bots.apoiadorrequisitante.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.devplatform.model.jira.JiraGroup;
import com.devplatform.model.jira.JiraGroups;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueAttachment;
import com.devplatform.model.jira.JiraIssueComment;
import com.devplatform.model.jira.JiraIssueFieldOption;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraIssueTransitions;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraProject;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.custom.JiraCustomField;
import com.devplatform.model.jira.custom.JiraCustomFieldOption;
import com.devplatform.model.jira.request.JiraCustomFieldOptionsRequest;
import com.devplatform.model.jira.request.JiraIssueFields;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;
import com.devplatform.model.jira.request.fields.JiraComment;
import com.devplatform.model.jira.request.fields.JiraComments;
import com.devplatform.model.jira.request.fields.JiraDateTime;
import com.devplatform.model.jira.request.fields.JiraMultiselect;
import com.devplatform.model.jira.request.fields.JiraTextArea;
import com.devplatform.model.jira.response.JiraJQLSearchResponse;
import com.devplatform.model.jira.response.JiraPropertyResponse;
import com.fasterxml.jackson.core.JsonProcessingException;

import dev.pje.bots.apoiadorrequisitante.clients.JiraClient;
import dev.pje.bots.apoiadorrequisitante.clients.JiraClientDebug;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Service
public class JiraService {

	private static final Logger logger = LoggerFactory.getLogger(JiraService.class);

	@Autowired
	private JiraClient jiraClient;

	@Autowired
	private JiraClientDebug jiraClientDebug;
	
	@Autowired
	private SlackService slackService;

	@Autowired
	private TelegramService telegramService;

	@Value("${clients.jira.url}") 
	private String jiraUrl;
	
	public static final Integer ISSUE_TYPE_NEW_VERSION = 10200;
	public static final Integer STATUS_OPEN_ID = 1;
	public static final Integer STATUS_GERAR_RELEASE_CANDIDATE_ID = 10671;
	public static final Integer STATUS_PREPARAR_PROXIMA_VERSAO_ID = 10672;
	public static final Integer STATUS_GERAR_RELEASE_NOTES_ID = 8;
	public static final Integer STATUS_RELEASE_NOTES_ID = 10670;
	public static final Integer STATUS_RELEASE_NOTES_CONFIRMED_ID = 10572;

	public static final String GRUPO_LANCADORES_VERSAO = "PJE_LancadoresDeVersao";
	public static final String PREFIXO_GRUPO_TRIBUNAL = "PJE_TRIBUNAL_";
	public static final String TRANSICTION_DEFAULT_EDICAO_AVANCADA = "Edição avançada";

	public static final String RELEASE_NOTES_JSON_FILENAME = "release-notes.json";

	public static final String FIELD_PROJECT = "project";
	public static final String FIELD_SUMMARY = "summary";
	public static final String FIELD_ISSUETYPE = "issuetype";
	public static final String FIELD_STATUS = "status";
	public static final String FIELD_DESCRIPTION = "description";
	public static final String FIELD_AFFECTED_VERSION = "versions";
	public static final String FIELD_AFFECTED_VERSION_TO_JQL = "affectedVersion";
	public static final Integer TEXT_FIELD_CHARACTER_LIMIT = 327670;
	public static final String FIELD_COMMENT = "comment";
	public static final String FIELD_TRIBUNAL_REQUISITANTE = "customfield_11700";
	public static final String FIELD_SUPER_EPIC_THEME = "customfield_11800";
	public static final String FIELD_AREAS_CONHECIMENTO = "customfield_13921";
	public static final String FIELD_EPIC_THEME = "customfield_10201";
	public static final String FIELD_DATA_RELEASE_NOTES = "customfield_13910";
	public static final String FIELD_URL_RELEASE_NOTES = "customfield_13911";
	public static final String FIELD_DATA_TAG_GIT = "customfield_13912";
	public static final String FIELD_VERSAO_SER_LANCADA = "customfield_13913";
	public static final String FIELD_PROXIMA_VERSAO = "customfield_13917";
	public static final String FIELD_SPRINT_DO_GRUPO = "customfield_11401";
	public static final String FIELD_GERAR_RC_AUTOMATICAMENTE = "customfield_14002";
	public static final String FIELD_VERSION_TYPE = "customfield_13906";
	
	public static final String JIRA_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
	
	public static final String PROJECT_PROPERTY_GITLAB_PROJECT_ID = "git.project.id";
	public static final String PROJECT_PROPERTY_JIRA_RELATED_PROJECTS_KEYS = "jira-related-projects.keys";
	
	public JiraGroups getGruposUsuario(JiraUser user) {
		JiraGroups groups = user.getGroups();
		if (groups == null) {
			String username = user.getName();
			Map<String, String> options = new HashMap<>();
			options.put("expand", "groups");
			options.put("username", username);

			groups = jiraClient.getUserDetails(options).getGroups();
		}
		return groups;
	}
	
	public boolean isLancadorVersao(JiraUser user) {
		boolean isLancadorVersao = false;
		try {
			JiraGroups grupos = getGruposUsuario(user);
			for (JiraGroup grupoUsuario : grupos.getItems()) {
				if (grupoUsuario.getName().equals(GRUPO_LANCADORES_VERSAO)) {
					isLancadorVersao = true;
					break;
				}
			}
		} catch (Exception e) {
			// ignore
		}

		return isLancadorVersao;
	}
	
	public JiraGroup getGrupoTribunalUsuario(JiraUser user) {
		JiraGroup tribunal = null;
		try {
			JiraGroups grupos = getGruposUsuario(user);
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
	
	public String getJqlIssuesFromFixVersion(String version, boolean onlyClosed, String projectKey) {
		String jql = "category in (PJE, PJE-CLOUD) AND fixVersion in (" + version + ")";
		if(onlyClosed) {
			jql += " AND status in (Fechado, Resolvido)";
		}
		if(StringUtils.isNotBlank(projectKey)) {
			jql += " AND project = " + projectKey;
		}
		jql += " ORDER BY priority DESC, key ASC";
		
		return jql;
	}
	
	public String getJqlIssuesLancamentoVersao(String version, String projectKey) {
		StringBuilder jqlsb = new StringBuilder("category in (PJE, PJE-CLOUD) ");
		jqlsb.append(" AND status not in (Fechado, Resolvido) ")
			.append(" AND ").append(FIELD_AFFECTED_VERSION_TO_JQL).append(" IN (").append(version).append(") ");
		if(StringUtils.isNotBlank(projectKey)) {
			jqlsb.append(" AND project in (").append(projectKey).append(") ");
		}
		
		return jqlsb.toString();
	}
	
	public List<JiraIssue> getIssuesFromJql(String jql) {
		List<JiraIssue> issues = new ArrayList<>();
		Map<String, String> options = new HashMap<>();
		if(StringUtils.isNotBlank(jql)) {
			options.put("jql", jql);

			Integer startAt = 0;
			boolean finalizado = false;
			while(!finalizado) {
				options.put("startAt", startAt.toString());
				JiraJQLSearchResponse response = jiraClient.searchIssuesWithJQL(options);
				if(response != null && response.getIssues().size() > 0) {
					issues.addAll(response.getIssues());
				}
				startAt += response.getMaxResults();
				if(response == null || response.getIssues().size() == 0 || startAt >= response.getTotal()) {
					finalizado = true;
					break;
				}
			}
		}
		
		return issues;
	}

	/**
	 * 1. recupera a issue do jira, para saber a informação atualizada do campo:
	 * tribunais requisitantes 2. identifica qual é a transição que deverá ser
	 * utilizada 3. monta o payload 4. encaminha o payload da alteraco
	 * @throws Exception 
	 */
	public void adicionaTribunalRequisitante(JiraIssue issue, String tribunalRequisitante, Map<String, Object> updateFields) throws Exception {
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
			String fieldName = FIELD_TRIBUNAL_REQUISITANTE;
			Map<String, Object> updateField = createUpdateObject(fieldName, listaTribunaisAtualizada, "ADD");
			if(updateField != null && updateField.get(fieldName) != null) {
				updateFields.put(fieldName, updateField.get(fieldName));
			}
		}
	}
	
	public void atualizarAreasConhecimento(JiraIssue issue, List<String> novasAreasConhecimento, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		List<JiraIssueFieldOption> areasConhecimentoAtual = issueDetalhada.getFields().getAreasConhecimento();

		boolean houveAlteracao = false;
		if(!(novasAreasConhecimento.isEmpty() && (areasConhecimentoAtual == null || areasConhecimentoAtual.isEmpty()))) {
			List<String> areasConhecimentoList = new ArrayList<>();
			if(areasConhecimentoAtual != null) {
				for (JiraIssueFieldOption areaConhecimento : areasConhecimentoAtual) {
					areasConhecimentoList.add(areaConhecimento.getValue());
				}
			}
			houveAlteracao = !Utils.compareListValues(novasAreasConhecimento, areasConhecimentoList);
		}
		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_AREAS_CONHECIMENTO, novasAreasConhecimento, "UPDATE");
			if(updateField != null && updateField.get(FIELD_AREAS_CONHECIMENTO) != null) {
				updateFields.put(FIELD_AREAS_CONHECIMENTO, updateField.get(FIELD_AREAS_CONHECIMENTO));
			}
		}
	}
	
	public void atualizarVersaoASerLancada(JiraIssue issue,  String versao, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(versao)) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
			
			boolean houveAlteracao = false;
			if(StringUtils.isBlank(issueDetalhada.getFields().getVersaoSeraLancada()) || !issueDetalhada.getFields().getVersaoSeraLancada().equals(versao)) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_VERSAO_SER_LANCADA, versao, "UPDATE");
				if(updateField != null && updateField.get(FIELD_VERSAO_SER_LANCADA) != null) {
					updateFields.put(FIELD_VERSAO_SER_LANCADA, updateField.get(FIELD_VERSAO_SER_LANCADA));
				}
			}
		}		
	}

	public void atualizarDataReleaseNotes(JiraIssue issue,  String dataReleaseNotes, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		
		boolean houveAlteracao = false;
		if(StringUtils.isBlank(issueDetalhada.getFields().getDtGeracaoReleaseNotes()) || !issueDetalhada.getFields().getDtGeracaoReleaseNotes().equals(dataReleaseNotes)) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_DATA_RELEASE_NOTES, dataReleaseNotes, "UPDATE");
			if(updateField != null && updateField.get(FIELD_DATA_RELEASE_NOTES) != null) {
				updateFields.put(FIELD_DATA_RELEASE_NOTES, updateField.get(FIELD_DATA_RELEASE_NOTES));
			}
		}
	}

	public void atualizarURLReleaseNotes(JiraIssue issue,  String urlReleaseNotes, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		
		boolean houveAlteracao = false;
		if(StringUtils.isBlank(issueDetalhada.getFields().getUrlReleaseNotes()) || !issueDetalhada.getFields().getUrlReleaseNotes().equalsIgnoreCase(urlReleaseNotes)) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_URL_RELEASE_NOTES, urlReleaseNotes, "UPDATE");
			if(updateField != null && updateField.get(FIELD_URL_RELEASE_NOTES) != null) {
				updateFields.put(FIELD_URL_RELEASE_NOTES, updateField.get(FIELD_URL_RELEASE_NOTES));
			}
		}
	}

	public void atualizarDataGeracaoTag(JiraIssue issue,  String dataGeracaoTag, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		
		boolean houveAlteracao = false;
		if(StringUtils.isBlank(issueDetalhada.getFields().getDtGeracaoCodigoFonte()) || !issueDetalhada.getFields().getDtGeracaoCodigoFonte().equals(dataGeracaoTag)) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_DATA_TAG_GIT, dataGeracaoTag, "UPDATE");
			if(updateField != null && updateField.get(FIELD_DATA_TAG_GIT) != null) {
				updateFields.put(FIELD_DATA_TAG_GIT, updateField.get(FIELD_DATA_TAG_GIT));
			}
		}
	}

	public void atualizarProximaVersao(JiraIssue issue,  String versao, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(versao)) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
			
			boolean houveAlteracao = false;
			if(StringUtils.isBlank(issueDetalhada.getFields().getProximaVersao()) || !issueDetalhada.getFields().getProximaVersao().equals(versao)) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_PROXIMA_VERSAO, versao, "UPDATE");
				if(updateField != null && updateField.get(FIELD_PROXIMA_VERSAO) != null) {
					updateFields.put(FIELD_PROXIMA_VERSAO, updateField.get(FIELD_PROXIMA_VERSAO));
				}
			}
		}		
	}

	public void atualizarDescricao(JiraIssue issue,  String texto, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(texto)) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
			
			boolean houveAlteracao = false;
			if(StringUtils.isBlank(issueDetalhada.getFields().getDescription()) || !issueDetalhada.getFields().getDescription().equals(texto)) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_DESCRIPTION, texto, "UPDATE");
				if(updateField != null && updateField.get(FIELD_DESCRIPTION) != null) {
					updateFields.put(FIELD_DESCRIPTION, updateField.get(FIELD_DESCRIPTION));
				}
			}
		}		
	}

	public void adicionarComentario(JiraIssue issue, String texto, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(texto)) {
			Map<String, Object> updateField = createUpdateObject(FIELD_COMMENT, texto, "ADD");
			if(updateField != null && updateField.get(FIELD_COMMENT) != null) {
				updateFields.put(FIELD_COMMENT, updateField.get(FIELD_COMMENT));
			}
		}
	}

	public void novaIssueCriarProject(String projectKey, Map<String, Object> issueFields) throws Exception {
		if (StringUtils.isNotBlank(projectKey)) {
			
			Map<String, Object> issueField = new HashMap<>();
			Map<String, String> projectMap = new HashMap<>();
			projectMap.put("key", projectKey);
			issueField.put(FIELD_PROJECT, projectMap);
			if(issueField != null && issueField.get(FIELD_PROJECT) != null) {
				issueFields.put(FIELD_PROJECT, issueField.get(FIELD_PROJECT));
			}
		}		
	}

	public void novaIssueCriarSummary(String summaryText, Map<String, Object> issueFields) throws Exception {
		if (StringUtils.isNotBlank(summaryText)) {
			
			Map<String, Object> issueField = new HashMap<>();
			issueField.put(FIELD_SUMMARY, summaryText);
			if(issueField != null && issueField.get(FIELD_SUMMARY) != null) {
				issueFields.put(FIELD_SUMMARY, issueField.get(FIELD_SUMMARY));
			}
		}		
	}


	public void novaIssueCriarIssueType(JiraIssuetype issueType, Map<String, Object> issueFields) throws Exception {
		if (issueType != null) {
			
			Map<String, Object> issueField = new HashMap<>();
			issueField.put(FIELD_ISSUETYPE, issueType);
			if(issueField != null && issueField.get(FIELD_ISSUETYPE) != null) {
				issueFields.put(FIELD_ISSUETYPE, issueField.get(FIELD_ISSUETYPE));
			}
		}		
	}

	public void novaIssueCriarAffectedVersion(JiraVersion issueVersion, Map<String, Object> issueFields) throws Exception {
		if (issueVersion != null) {
			List<JiraVersion> affectedVersion = new ArrayList<>();
			affectedVersion.add(issueVersion);
			Map<String, Object> issueField = new HashMap<>();
			issueField.put(FIELD_AFFECTED_VERSION, affectedVersion);
			if(issueField != null && issueField.get(FIELD_AFFECTED_VERSION) != null) {
				issueFields.put(FIELD_AFFECTED_VERSION, issueField.get(FIELD_AFFECTED_VERSION));
			}
		}		
	}

	public void novaIssueCriarIndicacaoGeracaoReleaseCandidateAutomaticamente(Map<String, Object> issueFields) throws Exception {
		String textoGeracaoAutomatica = "Sim";
		Map<String, String> opcaoGeracaoAutomatica = new HashMap<>();
		opcaoGeracaoAutomatica.put("value", textoGeracaoAutomatica);
		List<Map<String, String>> listOpcoes = new ArrayList<>();
		listOpcoes.add(opcaoGeracaoAutomatica);
		
		Map<String, List<Map<String, String>>> issueField = new HashMap<>();
		issueField.put(FIELD_GERAR_RC_AUTOMATICAMENTE, listOpcoes);
		if(issueField != null && issueField.get(FIELD_GERAR_RC_AUTOMATICAMENTE) != null) {
			issueFields.put(FIELD_GERAR_RC_AUTOMATICAMENTE, issueField.get(FIELD_GERAR_RC_AUTOMATICAMENTE));
		}
	}

	public void novaIssueCriarTipoVersao(String tipoVersao, Map<String, Object> issueFields) throws Exception {
		Map<String, String> opcaoTipoVersao = new HashMap<>();
		opcaoTipoVersao.put("value", tipoVersao);
		
		Map<String, Map<String, String>> issueField = new HashMap<>();
		issueField.put(FIELD_VERSION_TYPE, opcaoTipoVersao);
		if(issueField != null && issueField.get(FIELD_VERSION_TYPE) != null) {
			issueFields.put(FIELD_VERSION_TYPE, issueField.get(FIELD_VERSION_TYPE));
		}
	}

	// TODO - alterar para saber como deve ser montado o objeto de acordo com a especificação do campo no serviço
	// http://www.cnj.jus.br/jira/rest/api/2/field - utilizando o schema
	@SuppressWarnings("unchecked")
	private Map<String, Object> createUpdateObject(String fieldName, Object valueToUpdate, String operation) throws Exception{
		Map<String, Object> objectToUpdate = new HashMap<>();
		
		if(valueToUpdate != null
				&& (
					FIELD_VERSAO_SER_LANCADA.equals(fieldName) ||
					FIELD_PROXIMA_VERSAO.equals(fieldName) ||
					FIELD_URL_RELEASE_NOTES.equals(fieldName) ||
					FIELD_DESCRIPTION.equals(fieldName)
					)) {
			String text = (String)valueToUpdate;
			if(text.length() > TEXT_FIELD_CHARACTER_LIMIT && TEXT_FIELD_CHARACTER_LIMIT != 0) {
				throw new Exception("Campo texto com número de caractéres acima do limite, enviado: " + text.length() + " - limite: "+ TEXT_FIELD_CHARACTER_LIMIT);
			}

			boolean identificouCampo = false;
			if(valueToUpdate instanceof String) {
				JiraTextArea description = new JiraTextArea((String) valueToUpdate);
				objectToUpdate.put(fieldName, description.getBody());
				identificouCampo = true;
			}
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser String, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if(FIELD_DATA_RELEASE_NOTES.equals(fieldName) || FIELD_DATA_TAG_GIT.equals(fieldName)) {
			String dateTimeStr = null;
			boolean identificouCampo = false;
			if(valueToUpdate != null) {
				dateTimeStr = (String) valueToUpdate;
				if(valueToUpdate instanceof String) {
					try {
						Utils.stringToDate(dateTimeStr, JIRA_DATETIME_PATTERN);
						identificouCampo = true;
					}catch (Exception e) {
						logger.error("A string indicada: "+ dateTimeStr + " está fora do padrão de datetime do jira.");
						throw new Exception("A string indicada: "+ dateTimeStr + " está fora do padrão de datetime do jira.");
					}
				}
			}else {
				identificouCampo = true;
			}

			if(identificouCampo) {
				JiraDateTime text = new JiraDateTime(dateTimeStr);
				objectToUpdate.put(fieldName, text.getBody());
			}else {
				throw new Exception("Valor para update fora do padrão - deveria ter o formato: " + JIRA_DATETIME_PATTERN + ", recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if(FIELD_TRIBUNAL_REQUISITANTE.equals(fieldName) || FIELD_AREAS_CONHECIMENTO.equals(fieldName)) {
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
		}else if(FIELD_COMMENT.equals(fieldName) && valueToUpdate != null) {
			String text = (String)valueToUpdate;
			if(text.length() > TEXT_FIELD_CHARACTER_LIMIT && TEXT_FIELD_CHARACTER_LIMIT != 0) {
				throw new Exception("Campo texto com número de caractéres acima do limite, enviado: " + text.length() + " - limite: "+ TEXT_FIELD_CHARACTER_LIMIT);
			}

			boolean identificouCampo = false;
			if(valueToUpdate instanceof String) {
				JiraComments comment = new JiraComments((String) valueToUpdate);
				objectToUpdate.put(fieldName, comment.getComment());
				identificouCampo = true;
			}
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser String, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}
		
		return objectToUpdate;
	}
	
	@Cacheable(cacheNames = "custom-field-detail")
	public JiraCustomField getCustomFieldDetailed (String customFieldName, String searchItems, String onlyEnabled) { 
		Map<String, String> options = new HashMap<>();
		if(searchItems != null) {
			options.put("search", searchItems);
		}
		if(onlyEnabled == null) {
			onlyEnabled = Boolean.FALSE.toString();
		}
		options.put("onlyEnabled", onlyEnabled);
		
		JiraCustomField customFieldOptions = null;
		try {
			customFieldOptions = jiraClient.getCustomFieldOptions(customFieldName, options);
		}catch (Exception e) {
			String errorMesasge = "Erro ao buscar customfield: "+ customFieldName + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}

		return customFieldOptions;
	}

	public List<String> findSuperEpicTheme(List<String> epicThemeListToSearch) {
		List<String> superEpicThemes = new ArrayList<>();
		if(epicThemeListToSearch != null && !epicThemeListToSearch.isEmpty()) {
			JiraCustomField customFieldDetails = getCustomFieldDetailed(FIELD_SUPER_EPIC_THEME, null, null);
			if(customFieldDetails != null && !customFieldDetails.getOptions().isEmpty()) {
				for (String epicThemeSearched : epicThemeListToSearch) {
					boolean encontrou = false;
					for (JiraCustomFieldOption option : customFieldDetails.getOptions()) {
						String superEpicTheme = option.getValue();
						if(!option.getCascadingOptions().isEmpty()) {
							for (JiraCustomFieldOption cascadeOption : option.getCascadingOptions()) {
								String epicTheme = cascadeOption.getValue();
								if(Utils.compareAsciiIgnoreCase(epicTheme, epicThemeSearched)) {
									encontrou = true;
									if(!superEpicThemes.contains(superEpicTheme)) {
										superEpicThemes.add(superEpicTheme);
										break;
									}
								}
							}
						}
						if(encontrou) {
							break;
						}
					}
					if(!encontrou) {
						String errorMesasge = "Não foi possível encontrar um super Epic/Theme para o Epic/Theme: [" + epicThemeSearched + "]";
						logger.error(errorMesasge);
						slackService.sendBotMessage(errorMesasge);
						telegramService.sendBotMessage(errorMesasge);
					}
				}				
			}
		}
		return superEpicThemes;
	}

	public JiraIssueTransition findTransitionByName(JiraIssue issue, String transitionName) {
		JiraIssueTransition founded = null;
		JiraIssueTransitions transitions = this.recuperarTransicoesIssue(issue);
		for (JiraIssueTransition transition : transitions.getTransitions()) {
			if(transition.getName().equalsIgnoreCase(transitionName)) {
				founded = transition;
				break;
			}
		}
		
		return founded;
	}
	
	public JiraIssueTransition findTransitionById(JiraIssue issue, String transitionId) {
		JiraIssueTransition founded = null;
		JiraIssueTransitions transitions = this.recuperarTransicoesIssue(issue);
		for (JiraIssueTransition transition : transitions.getTransitions()) {
			if(transition.getId().equalsIgnoreCase(transitionId)) {
				founded = transition;
				break;
			}
		}
		
		return founded;
	}
	
	public String getJqlIssuesPendentesTribunalRequisitante(String tribunal) {
		String jql = jiraUrl + "/issues/?jql=cf%5B11700%5D%20in%20("+tribunal+")%20AND%20status%20not%20in%20(Fechado%2C%20Resolvido)";
		return jql;
	}
	
	public JiraIssue createIssue(JiraIssueFields newIssue) {
		return jiraClient.createIssue(newIssue);
	}

	public void updateIssue(JiraIssue issue, JiraIssueTransitionUpdate issueUpdate) throws JsonProcessingException {
		String issueKey = issue.getKey();
		jiraClient.changeIssueWithTransition(issueKey, issueUpdate);
	}

	public void updateIssueDebug(JiraIssue issue, JiraIssueTransitionUpdate issueUpdate) throws JsonProcessingException {
		String issueKey = issue.getKey();
		jiraClientDebug.changeIssueWithTransition(issueKey, issueUpdate);
	}
	
	private void removeAttachmentsWithFileName(JiraIssue issue, String filename) {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		if(issueDetalhada.getFields().getAttachment() != null && !issueDetalhada.getFields().getAttachment().isEmpty()) {
			for (JiraIssueAttachment attachment : issueDetalhada.getFields().getAttachment()) {
				if(attachment.getFilename().equals(filename)) {
					jiraClient.removeAttachment(attachment.getId().toString());
				}
			}
		}
	}

	public void sendTextAsComment(JiraIssue issue, String text) {
		JiraComment comment = new JiraComment(text);
		try {
			jiraClient.sendComment(issue.getKey(), comment);
		}catch (Exception e) {
			String errorMesasge = "Erro ao tentar enviar o comentário: [" + text + "] - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
	}

	public void sendTextAsAttachment(JiraIssue issue, String filename, String fileContent) {
		removeAttachmentsWithFileName(issue, filename);
		MultipartFile file = 
				new MockMultipartFile(filename, filename, "text/plain", fileContent.getBytes());
		
		try {
			jiraClient.sendAttachment(issue.getKey(), file);
		}catch (Exception e) {
			String errorMesasge = "Erro ao tentar enviar o anexo: [" + filename + "] - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
	}

	public String getAttachmentContent(JiraIssue issue, String filename) {
		String attachmentContent = null;
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		if(issueDetalhada.getFields().getAttachment() != null && !issueDetalhada.getFields().getAttachment().isEmpty()) {
			for (JiraIssueAttachment attachment : issueDetalhada.getFields().getAttachment()) {
				if(attachment.getFilename().equals(filename)) {
					attachmentContent = jiraClient.getAttachmentContent(attachment.getId().toString(), attachment.getFilename());
					break;
				}
			}
		}
		return attachmentContent;
	}

	public JiraIssueTransitions recuperarTransicoesIssue(JiraIssue issue) {
		String issueKey = issue.getKey();
		return jiraClient.getIssueTransitions(issueKey);
	}
	
	/**
	 * Dada uma issue, verifica se ela tem a marcacao de qual é o serviço da issue, se não tiver, utiliza a informação da propriedade do 
	 * projeto jira para o id do projeto no gitlab: "jira.project.id"
	 * @param issue
	 * @return
	 */
	public String getGitlabProjectFromIssue(JiraIssue issue) {
		String gitlabProjectId = null;
		if(issue != null && issue.getFields() != null) {
			if(issue.getFields().getServico() != null && issue.getFields().getServico().getId() != null) {
				gitlabProjectId = mapeamentoServicoJiraProjetoGitlab(issue.getFields().getServico().getId().toString());
			}
			if(gitlabProjectId == null && issue.getFields().getProject() != null) {
				gitlabProjectId = getGitlabProjectFromJiraProjectKey(issue.getFields().getProject().getKey());
			}
		}
		return gitlabProjectId;
	}
	
	/**
	 * Busca o projeto do gitlab a partir da chave do jira, propriedade: "jira.project.id"
	 * 
	 * @param projectKey
	 * @return
	 */
	public String getGitlabProjectFromJiraProjectKey(String projectKey) {
		return getPropertyFromJiraProject(projectKey, PROJECT_PROPERTY_GITLAB_PROJECT_ID);
	}
	
	@Cacheable
	public String getPropertyFromJiraProject(String projectKey, String propertyKey) {
		String propertyValue = null;
		if(projectKey != null) {
			JiraPropertyResponse projectProperty = null;
			try {
				projectProperty = jiraClient.getProjectProperty(projectKey, propertyKey);
			}catch (Exception e) {
				logger.info("Propriedade não encontrada!!");
				logger.info(e.getLocalizedMessage());
			}
			if(projectProperty != null 
					&& projectProperty.getValue() != null) {
				if(projectProperty.getValue() instanceof String) {
					propertyValue = (String) projectProperty.getValue();
				}
			}
		}
		return propertyValue;
	}

	public JiraIssue recuperaIssueDetalhada(JiraIssue issue) {
		String issueKey = issue.getKey();
		return recuperaIssueDetalhada(issueKey);
	}
	public JiraIssue recuperaIssueDetalhada(String issueKey) {
		Map<String, String> options = new HashMap<>();
		options.put("expand", "operations");
		return recuperaIssue(issueKey, options);
	}
	
	@Cacheable
	public JiraIssue recuperaIssue(String issueKey, Map<String, String> options) {
		return jiraClient.getIssueDetails(issueKey, options);
	}
	
	@Cacheable(cacheNames = "user-from-name")
	public JiraUser getUserFromUserName(String userName) {
		Map<String, String> options = new HashMap<>();
		options.put("username", userName);
		List<JiraUser> usuarios = jiraClient.findUser(options);
		return (usuarios != null && !usuarios.isEmpty()) ? usuarios.get(0) : null;
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
	
	@Cacheable(cacheNames = "jira-to-git-project")
	public static String mapeamentoServicoJiraProjetoGitlab(String servicoJiraId) {
		String gitlabProjectId = null;

		int servicoJiraInt = Integer.valueOf(servicoJiraId);
		switch(servicoJiraInt) {
		case 14114: // CEMAN
			gitlabProjectId = "529";
			break;
		case 13601: // Criminal
			gitlabProjectId = "220";
			break;
		case 14284: //"Navegador PJE (site)"
			gitlabProjectId = "205";
			break;
		case 13600: // PJE-Legacy
			gitlabProjectId = "7";
			break;
		case 14113: // PJE-Mobile (frontend)
			gitlabProjectId = "503";
			break;
		case 14274: // PJE-Office
			gitlabProjectId = "80";
			break;
		case 14112: // PJE2-WEB (frontend)
			gitlabProjectId = "180";
			break;
		case 13602: // RPV
			gitlabProjectId = "223";
			break;
		case 14371: // Sessão de julgamento
			gitlabProjectId = "604";
			break;
		}
		
		return gitlabProjectId;
	}
	
	public void createVersionInRelatedProjects(String projectKey, String newVersion) {
		List<String> relatedProjectKeys = getProjetosJiraRelacionados(projectKey);
		if(relatedProjectKeys != null) {
			for (String relatedProjectKey : relatedProjectKeys) {
				String versionDescription = "Contempla as demandas relacionadas à versão: " + newVersion;
				// cria o número da próxima versao em cada um dos projetos do jira relacionados
				JiraVersion jiraVersion = createProjectVersionIfNotExists(relatedProjectKey, newVersion, versionDescription, null);
				if(jiraVersion != null) {
					logger.info("Criada a versão: " + newVersion + " no projeto do jira: " + relatedProjectKey);
				}else {
					logger.error("Falhou ao tentar criar a versão: " + newVersion + " no projeto do jira: " + relatedProjectKey);
				}
			}
		}
	}
	
	public List<String> getProjetosJiraRelacionados(String projectKey){
		List<String> projetosRelacionados = new ArrayList<>();
		if(projectKey != null) {
			boolean encontrouRelacionados = false;
			String projectKeyValue = getPropertyFromJiraProject(projectKey, PROJECT_PROPERTY_JIRA_RELATED_PROJECTS_KEYS);
			if(StringUtils.isNotBlank(projectKeyValue)) {
				String[] projectKeys = projectKeyValue.split(",");
				if(projectKeys != null && projectKeys.length > 0) {
					projetosRelacionados.addAll(Arrays.asList(projectKeys));
					encontrouRelacionados = true;
				}
			}
			if(!encontrouRelacionados) {
				projetosRelacionados.add(projectKey);
			}
		}
		return projetosRelacionados;
	}

	@Cacheable(cacheNames = "project-versions")
	public List<JiraVersion> getProjectVersions(String projectKey) {
		List<JiraVersion> projectVersions = null;
		try {
			Map<String, String> options = new HashMap<>();
			projectVersions = jiraClient.getAllVersions(projectKey, options);
		} catch (Exception e) {
			// ignora
		}
		return projectVersions;
	}
	
	public JiraVersion findProjecVersion(String projectKey, String versionName) {
		JiraVersion version = null;
		List<JiraVersion> projectVersions = getProjectVersions(projectKey);
		if(projectVersions != null && !projectVersions.isEmpty()) {
			for (JiraVersion jiraVersion : projectVersions) {
				if(jiraVersion.getName().equalsIgnoreCase(versionName)) {
					version = jiraVersion;
					break;
				}
			}
		}
		
		return version;
	}
	
	public JiraVersion createProjectVersionIfNotExists(String projectKey, String versionName, String description, String releaseDate) {
		JiraVersion version = findProjecVersion(projectKey, versionName);
		if(version == null) {
			version = createProjectVersion(projectKey, versionName, description, releaseDate);
		}
		
		return version;
	}
	
	public JiraVersion createProjectVersion(String projectKey, String versionName, String description, String releaseDate) {
		JiraVersion version = null;
		JiraProject project;
		try {
			project = getProject(projectKey);
			if(project != null) {
				JiraVersion jiraVersion = new JiraVersion();
				jiraVersion.setName(versionName);
				jiraVersion.setDescription(description);
				jiraVersion.setProjectId(project.getId());
				if(StringUtils.isNotBlank(releaseDate)) {
					Date releaseDateTime = Utils.stringToDate(releaseDate, Utils.DATE_SIMPLE_PATTERN);
					jiraVersion.setReleaseDate(Utils.dateToStringPattern(releaseDateTime, Utils.DATE_SIMPLE_PATTERN));
					jiraVersion.setReleased(true);
				}else {
					jiraVersion.setReleased(false);
				}
				version = jiraClient.createVersion(jiraVersion);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return version;
	}

	@Cacheable(cacheNames = "project-detail")
	public JiraProject getProject(String projectKey) throws Exception {
		JiraProject project = null;
		try {
			Map<String, String> options = new HashMap<>();
			project = jiraClient.getProjectDetails(projectKey, options);
		} catch (Exception e) {
			throw new Exception(e.getLocalizedMessage());
		}
		
		return project;
	}
	
	public JiraCustomField getSprintsDoGrupo(String searchItems, String onlyEnabled) {
		return getCustomFieldDetailed(FIELD_SPRINT_DO_GRUPO, searchItems, onlyEnabled);
	}
	
	public void createSprintDoGrupoOption(String optionName) {
		JiraCustomField sprintDoGrupo = getSprintsDoGrupo(optionName, Boolean.TRUE.toString());
		if(sprintDoGrupo != null) {
			boolean optionExists = false;
			if(!sprintDoGrupo.getOptions().isEmpty()) {
				for (JiraCustomFieldOption sprintOption : sprintDoGrupo.getOptions()) {
					if(Utils.compareAsciiIgnoreCase(sprintOption.getValue(), optionName)) {
						optionExists = true;
						break;
					}
				}
			}
			if(!optionExists) {
				JiraCustomFieldOption newOption = new JiraCustomFieldOption();
				newOption.setValue(optionName);

				JiraCustomFieldOptionsRequest optionsRequest = new JiraCustomFieldOptionsRequest(newOption);
				jiraClient.addCustomFieldOptions(sprintDoGrupo.getId().toString(), optionsRequest);
			}
		}
	}
}