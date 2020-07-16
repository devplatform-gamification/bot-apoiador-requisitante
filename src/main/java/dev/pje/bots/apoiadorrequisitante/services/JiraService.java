package dev.pje.bots.apoiadorrequisitante.services;

import java.util.ArrayList;
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
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.custom.JiraCustomField;
import com.devplatform.model.jira.custom.JiraCustomFieldOption;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;
import com.devplatform.model.jira.request.fields.JiraComment;
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
	public static final Integer STATUS_RELEASE_NOTES_ID = 10670;
	public static final Integer STATUS_RELEASE_NOTES_CONFIRMED_ID = 10572;

	public static final String GRUPO_LANCADORES_VERSAO = "PJE_LancadoresDeVersao";
	public static final String PREFIXO_GRUPO_TRIBUNAL = "PJE_TRIBUNAL_";
	public static final String TRANSICTION_DEFAULT_EDICAO_AVANCADA = "Edição avançada";
	
	public static final String FIELD_STATUS = "status";
	public static final String FIELD_DESCRIPTION = "description";
	public static final Integer TEXT_FIELD_CHARACTER_LIMIT = 327670;
	public static final String FIELD_COMMENT = "comment";
	public static final String FIELD_TRIBUNAL_REQUISITANTE = "customfield_11700";
	public static final String FIELD_SUPER_EPIC_THEME = "customfield_11800";
	public static final String FIELD_AREAS_CONHECIMENTO = "customfield_13921";
	public static final String FIELD_EPIC_THEME = "customfield_10201";
	public static final String FIELD_VERSAO_SER_LANCADA = "customfield_13913";
	public static final String FIELD_DATA_RELEASE_NOTES = "customfield_13910";
	public static final String FIELD_PROXIMA_VERSAO = "customfield_13917";
	
	public static final String JIRA_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";
	
	public static final String PROJECT_PROPERTY_GITLAB_PROJECT_ID = "jira.project.id";
	
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
	
	public String getJqlIssuesFromFixVersion(String version, boolean onlyClosed) {
		String jql = "category in (PJE, PJE-CLOUD) AND fixVersion in (\"" + version + "\")";
		if(onlyClosed) {
			jql += " AND status in (Fechado, Resolvido)";
		}
		jql += " ORDER BY priority DESC, key ASC";
		
		return jql;
	}
	
	public List<JiraIssue> getIssuesFromJql(String jql) {
		Map<String, String> options = new HashMap<>();
		if(jql != null && !jql.isEmpty()) {
			options.put("jql", jql);
		}
		List<JiraIssue> issues = new ArrayList<>();
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
	
	// TODO - alterar para saber como deve ser montado o objeto de acordo com a especificação do campo no serviço
	// http://www.cnj.jus.br/jira/rest/api/2/field - utilizando o schema
	@SuppressWarnings("unchecked")
	private Map<String, Object> createUpdateObject(String fieldName, Object valueToUpdate, String operation) throws Exception{
		Map<String, Object> objectToUpdate = new HashMap<>();
		
		if(valueToUpdate != null
				&& (
					FIELD_VERSAO_SER_LANCADA.equals(fieldName) ||
					FIELD_PROXIMA_VERSAO.equals(fieldName) ||
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
		}else if(FIELD_DATA_RELEASE_NOTES.equals(fieldName)) {
			String dateTimeStr = null;
			boolean identificouCampo = false;
			if(valueToUpdate != null) {
				dateTimeStr = (String) valueToUpdate;
				if(valueToUpdate instanceof String) {
					try {
						Date dateTime = Utils.stringToDate(dateTimeStr, JIRA_DATETIME_PATTERN);
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
				JiraComment comment = new JiraComment((String) valueToUpdate);
				objectToUpdate.put(fieldName, comment.getComment());
				identificouCampo = true;
			}
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser String, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}
		
		return objectToUpdate;
	}
	
	@Cacheable
	public JiraCustomField getCustomFieldDetailed (String customFieldName, String searchItems, String onlyEnabled) { 
		Map<String, String> options = new HashMap<>();
		if(searchItems != null) {
			options.put("search", searchItems);
		}
		if(onlyEnabled == null) {
			onlyEnabled = Boolean.FALSE.toString();
		}
		options.put("onlyEnabled", onlyEnabled);

		return jiraClient.getCustomFieldOptions(customFieldName, options);
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
	
	public void sendTextAsAttachment(JiraIssue issue, String filename, String fileContent) {
		removeAttachmentsWithFileName(issue, filename);
		MultipartFile file = 
				new MockMultipartFile(filename, filename, "text/plain", fileContent.getBytes());
		
		List<JiraIssueAttachment> attachmentsSent = jiraClient.sendAttachment(issue.getKey(), file);
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
		String gitlabProjectId = null;
		if(projectKey != null) {
			JiraPropertyResponse projectProperty = null;
			try {
				projectProperty = jiraClient.getProjectProperty(projectKey, PROJECT_PROPERTY_GITLAB_PROJECT_ID);
			}catch (Exception e) {
				logger.info("Propriedade não encontrada!!");
				logger.info(e.getLocalizedMessage());
			}
			if(projectProperty != null 
					&& projectProperty.getValue() != null) {
				if(projectProperty.getValue() instanceof String) {
					gitlabProjectId = (String) projectProperty.getValue();
				}
			}
		}
		return gitlabProjectId;
	}

	public JiraIssue recuperaIssueDetalhada(JiraIssue issue) {
		String issueKey = issue.getKey();
		Map<String, String> options = new HashMap<>();
		options.put("expand", "operations");
		return recuperaIssue(issueKey, options);
	}
	
	@Cacheable
	public JiraIssue recuperaIssue(String issueKey, Map<String, String> options) {
		return jiraClient.getIssueDetails(issueKey, options);
	}
	
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
}