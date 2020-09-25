package dev.pje.bots.apoiadorrequisitante.services;

import java.io.IOException;
import java.math.BigDecimal;
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

import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.gitlab.GitlabUserIdentity;
import com.devplatform.model.jira.JiraFilter;
import com.devplatform.model.jira.JiraGroup;
import com.devplatform.model.jira.JiraGroups;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueAttachment;
import com.devplatform.model.jira.JiraIssueComment;
import com.devplatform.model.jira.JiraIssueFieldOption;
import com.devplatform.model.jira.JiraIssueTipoVersaoEnum;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraIssueTransitions;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraProject;
import com.devplatform.model.jira.JiraProperty;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.custom.JiraCustomField;
import com.devplatform.model.jira.custom.JiraCustomFieldOption;
import com.devplatform.model.jira.custom.JiraCustomFieldOptionRequest;
import com.devplatform.model.jira.custom.JiraWorkflow;
import com.devplatform.model.jira.request.JiraCustomFieldOptionsRequest;
import com.devplatform.model.jira.request.JiraIssueCreateAndUpdate;
import com.devplatform.model.jira.request.JiraIssueLinkRequest;
import com.devplatform.model.jira.request.JiraIssueLinkTypeRequest;
import com.devplatform.model.jira.request.fields.JiraComment;
import com.devplatform.model.jira.request.fields.JiraComments;
import com.devplatform.model.jira.request.fields.JiraDateTime;
import com.devplatform.model.jira.request.fields.JiraMultiselect;
import com.devplatform.model.jira.request.fields.JiraTextArea;
import com.devplatform.model.jira.response.JiraJQLSearchResponse;
import com.devplatform.model.jira.response.JiraPropertyResponse;

import dev.pje.bots.apoiadorrequisitante.clients.JiraClient;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Service
public class JiraService {

	private static final Logger logger = LoggerFactory.getLogger(JiraService.class);

	@Autowired
	private JiraClient jiraClient;

	@Autowired
	private SlackService slackService;

	@Autowired
	private RocketchatService rocketchatService;

	@Autowired
	private TelegramService telegramService;

	@Value("${clients.jira.url}") 
	private String jiraUrl;
	
	@Value("${project.jira.filters.pendentes-geral}")
	private String filterIssuesPendentesGeralId;

	public static final String ISSUE_TYPE_NEW_VERSION = "10200";
	public static final String ISSUE_TYPE_DOCUMENTATION = "10301";
	public static final String ISSUE_TYPE_RELEASE_NOTES = "10302";

	public static final String ISSUE_TYPE_HOTFIX = "10202";
	public static final String ISSUE_TYPE_BUGFIX = "10201";
	public static final String ISSUE_TYPE_BUG = "1";
	public static final String ISSUE_TYPE_IMPROVEMENT = "4";
	public static final String ISSUE_TYPE_NEWFEATURE = "2";
	public static final String ISSUE_TYPE_QUESTION = "23";

	public static final String ISSUE_REPORTER_PRIORITY_PANIC = "1";
	public static final String ISSUE_REPORTER_PRIORITY_CRITICAL = "2";
	public static final String ISSUE_REPORTER_PRIORITY_NORMAL = "3";
	public static final String ISSUE_REPORTER_PRIORITY_MINOR = "4";

	public static final String PROJECTKEY_PJEDOC = "PJEDOC";

	public static final String STATUS_OPEN_ID = "1";
	public static final String STATUS_PREPARAR_VERSAO_ATUAL_ID = "10677";
	public static final String STATUS_GERAR_RELEASE_CANDIDATE_ID = "10671";
	public static final String STATUS_PREPARAR_PROXIMA_VERSAO_ID = "10672";
	public static final String STATUS_GERAR_RELEASE_NOTES_ID = "8";
	public static final String STATUS_RELEASE_NOTES_ID = "10670";
	public static final String STATUS_RELEASE_NOTES_CONFIRMED_ID = "10572";
	public static final String STATUS_RELEASE_NOTES_FINISHPROCESSING_ID = "10673";

	public static final String STATUS_DOCUMENTATION_PROCESSING_ID = "10674";
	public static final String STATUS_DOCUMENTATION_HOMOLOGATION_ID = "10480";
	public static final String STATUS_DOCUMENTATION_MANUAL_INTEGRATION_ID = "10043";
	public static final String STATUS_DOCUMENTATION_FINISH_HOMOLOGATION_ID = "10054";

	public static final Integer ISSUELINKTYPE_DOCUMENTATION_ID = 10410;
	public static final String ISSUELINKTYPE_DOCUMENTATION_INWARDNAME = "documents";
	public static final String ISSUELINKTYPE_DOCUMENTATION_OUTWARDNAME = "is documented by";

	public static final Integer ISSUELINKTYPE_RELATES_ID = 10003;
	public static final String ISSUELINKTYPE_RELATES_INWARDNAME = "relates to";
	public static final String ISSUELINKTYPE_RELATES_OUTWARDNAME = "relates to";

	public static final Integer ISSUELINKTYPE_DUPLICATES_ID = 10002;
	public static final String ISSUELINKTYPE_DUPLICATES_INWARDNAME = "duplicates";
	public static final String ISSUELINKTYPE_DUPLICATES_OUTWARDNAME = "is duplicated by";

	public static final String GRUPO_LANCADORES_VERSAO = "PJE_LancadoresDeVersao";
	public static final String GRUPO_SERVICOS = "PJE_Servicos";
	public static final String GRUPO_LIDERES_PROJETO = "PJE_Lideres";
	public static final String GRUPO_FABRICA_DESENVOLVIMENTO = "PJE_FabricasDesenvolvimento";
	public static final String PREFIXO_GRUPO_DESENVOLVEDORES = "PJE_Desenvolvedores";
	public static final String GRUPO_REVISORES_CODIGO = "PJE_RevisoresDeCodigo";
	public static final String GRUPO_TESTES = "PJE_Testes";
	public static final String PREFIXO_GRUPO_TRIBUNAL = "PJE_TRIBUNAL_";
	public static final String PREFIXO_GRUPO_DESENVOLVEDORES_FABRICA = "PJE_GRUPO_Desenvolvedores_";
	public static final String PREFIXO_USUARIO_FABRICA_DESENVOLVIMENTO = "desenvolvimento.";
	
	public static final String TRANSICTION_DEFAULT_EDICAO_AVANCADA = "Edição avançada";
	public static final String TRANSITION_PROPERTY_KEY_EDICAO_AVANCADA = "EDICAO_AVANCADA";
	public static final String TRANSITION_PROPERTY_KEY_FINALIZAR_DEMANDA = "FECHAR";
	public static final String TRANSITION_PROPERTY_KEY_TRIAGEM_CONFIRMADA = "TRIAGEM_CONFIRMADA";
	public static final String TRANSITION_PROPERTY_KEY_DEMANDANTE = "SOLICITAR_DEMANDANTE";
	public static final String TRANSITION_PROPERTY_KEY_FABRICA_DESENVOLVIMENTO = "FABRICA_DESENVOLVIMENTO";
	public static final String TRANSITION_PROPERTY_KEY_RESPONDIDO = "RESPONDIDO";
	public static final String TRANSITION_PROPERTY_KEY_IMPEDIMENTO = "IMPEDIMENTO";
	public static final String TRANSITION_PROPERTY_KEY_SOLICITAR_HOMOLOGACAO = "SOLICITAR_HOMOLOGACAO";
	public static final String TRANSITION_PROPERTY_KEY_APROVADA = "APROVADO";
	public static final String TRANSITION_PROPERTY_KEY_REPROVADA = "REPROVADO";
	public static final String TRANSITION_PROPERTY_KEY_SAIDA_PADRAO = "SAIDA_PADRAO";

	public static final String STATUS_PROPERTY_KEY_DEMANDANTE = "DEMANDANTE";
	public static final String STATUS_PROPERTY_KEY_FLUXO_RAIA_ID = "FLUXO_RAIA_ID";

	public static final String FLUXO_RAIA_ID_DEMANDANTE = "14597";
	public static final String FLUXO_RAIA_ID_TRIAGEM = "14598";
	public static final String FLUXO_RAIA_ID_EQUIPE_NEGOCIAL = "14599";
	public static final String FLUXO_RAIA_ID_ANALISE_DESIGN = "14608";
	public static final String FLUXO_RAIA_ID_FABRICA_DESENVOLVIMENTO = "14600";
	public static final String FLUXO_RAIA_ID_DESENVOLVEDOR = "14601";
	public static final String FLUXO_RAIA_ID_GRUPO_REVISOR_TECNICO = "14602";
	public static final String FLUXO_RAIA_ID_TESTES = "14603";
	public static final String FLUXO_RAIA_ID_AUTOMATIZACAO = "14604";
	public static final String FLUXO_RAIA_ID_DOCUMENTACAO = "14605";
	public static final String FLUXO_RAIA_ID_INFRA_SEGURANCA = "14607";
	public static final String FLUXO_RAIA_ID_FINALIZADA = "14606";
	
	public static final String USUARIO_DESENVOLVEDOR_ANONIMO = "desenvolvedor.anonimo";
	public static final String FLUXO_RAIA_RESPONSAVEL_DEFAULT = "suporte.pje";
	public static final String FLUXO_RAIA_RESPONSAVEL_TRIAGEM = "triagem.pje";
//	public static final String FLUXO_RAIA_RESPONSAVEL_DEMANDANTE = ""; // usuário dependerá dos outros dados da issue
//	public static final String FLUXO_RAIA_RESPONSAVEL_FABRICA_DESENVOLVIMENTO = ""; // usuário dependerá dos outros dados da issue
//	public static final String FLUXO_RAIA_RESPONSAVEL_DESENVOLVEDOR = ""; // usuário dependerá dos outros dados da issue
	public static final String FLUXO_RAIA_RESPONSAVEL_EQUIPE_NEGOCIAL = "negocios.pje";
	public static final String FLUXO_RAIA_RESPONSAVEL_ANALISE_DESIGN = "analise.design.pje";
	public static final String FLUXO_RAIA_RESPONSAVEL_GRUPO_REVISOR_TECNICO = "revisao.tecnia.pje";
	public static final String FLUXO_RAIA_RESPONSAVEL_TESTES = "testes.pje";
	public static final String FLUXO_RAIA_RESPONSAVEL_DOCUMENTACAO = "documentacao.pje";
	public static final String FLUXO_RAIA_RESPONSAVEL_AUTOMATIZACAO = "bot-fluxo-pje";
	public static final String FLUXO_RAIA_RESPONSAVEL_INFRA_SEGURANCA = "infra.seguranca.pje";
	
	public static final String RELEASE_NOTES_JSON_FILENAME = "release-notes.json";
	public static final String RELEASE_NOTES_FILENAME_PREFIX = "release-notes_";
	public static final String FILENAME_SUFFIX_ADOC = ".adoc";
	public static final String FILENAME_SUFFIX_HTML = ".html";
	public static final String DOCUMENTATION_ASSETS_DIR = "assets";

	public static final String FIELD_PROJECT = "project";
	public static final String FIELD_SUMMARY = "summary";
	public static final String FIELD_ISSUETYPE = "issuetype";
	public static final String FIELD_STATUS = "status";
	public static final String FIELD_DESCRIPTION = "description";
	public static final String FIELD_ISSUELINKS = "issuelinks";

	public static final String FIELD_AFFECTED_VERSION = "versions";
	public static final String FIELD_AFFECTED_VERSION_TO_JQL = "affectedVersion";
	public static final String FIELD_FIX_VERSION = "fixVersions";
	public static final String FIELD_COMMENT = "comment";
	public static final String FIELD_TRIBUNAIS_REQUISITANTES = "customfield_11700";
	public static final String FIELD_APROVADO_POR = "customfield_13836";
	public static final String FIELD_APROVACOES_REALIZADAS = "customfield_13835";
	public static final String FIELD_TRIBUNAIS_RESPONSAVEIS_REVISAO = "customfield_14018";
	public static final String FIELD_USUARIOS_RESPONSAVEIS_REVISAO = "customfield_14017";
	public static final String FIELD_CTRL_PONTUACAO_AREA_CONHECIMENTO = "customfield_14016";
	public static final String CTRL_PONTUACAO_AREA_CONHECIMENTO_DATA_ATUALIZACAO_OPTION = "atualizacao";
	public static final String FIELD_SUPER_EPIC_THEME = "customfield_11800";
	public static final String FIELD_AREAS_CONHECIMENTO = "customfield_13921";
	public static final String FIELD_EPIC_THEME = "customfield_10201";
	public static final String FIELD_BUSSINESS_VALUE = "customfield_10204";
	
	public static final String FIELD_DATA_RELEASE_NOTES = "customfield_13910";
	public static final String FIELD_URL_RELEASE_NOTES = "customfield_13911";
	public static final String FIELD_DATA_TAG_GIT = "customfield_13912";
	public static final String FIELD_VERSAO_SER_LANCADA = "customfield_13913";
	public static final String FIELD_PROXIMA_VERSAO = "customfield_13917";
	public static final String FIELD_SPRINT_DO_GRUPO = "customfield_11401";
	public static final String FIELD_GRUPO_RESPONSAVEL_ATRIBUICAO = "customfield_12800";
	public static final String FIELD_FABRICA_DESENVOLVIMENTO = "customfield_11900";
	public static final String FIELD_FABRICA_TESTES = "customfield_11901";
	public static final String FIELD_GERAR_RC_AUTOMATICAMENTE = "customfield_14002";
	public static final String FIELD_PUBLICAR_DOCUMENTACAO_AUTOMATICAMENTE = "customfield_14009";
	public static final String FIELD_VERSION_TYPE = "customfield_13906";
	public static final String FIELD_ESTRUTURA_DOCUMENTACAO = "customfield_14004";
	
	public static final String FIELD_RAIA_FLUXO = "customfield_14014";
	
	public static final String FIELD_MENSAGEM_SLACK = "customfield_14013";
	public static final String FIELD_MENSAGEM_ROCKETCHAT = "customfield_13908";
	public static final String FIELD_MENSAGEM_TELEGRAM = "customfield_13916";

	public static final String FIELD_BRANCHES_RELACIONADOS = "customfield_13005";
	public static final String FIELD_INTEGRADO_NOS_BRANCHES = "customfield_12101";
	public static final String FIELD_MRS_ABERTOS = "customfield_12509";
	public static final String FIELD_MRS_ACEITOS = "customfield_12505";

	public static final String FIELD_RESPONSAVEL_REVISAO = "customfield_12200";
	public static final String FIELD_RESPONSAVEL_CODIFICACAO = "customfield_12303";
	public static final String FIELD_RESPONSAVEL_ISSUE = "assignee";

	public static final Integer TEXT_FIELD_CHARACTER_LIMIT = 327670;

	public static final String JIRA_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";

	public static final String STEP_PROPERTY_STATUS_ID = "jira.status.id";
	public static final String PROJECT_PROPERTY_GITLAB_PROJECT_ID = "git.project.id";
	public static final String PROJECT_PROPERTY_JIRA_RELATED_PROJECTS_KEYS = "jira-related-projects.keys";
	public static final String PROJECT_PROPERTY_DOCUMENTATION_FIELDVALUE_DOC_STRUCTURE = "fieldvalue.estrutura-documentacao.id";
	public static final String PROJECT_PROPERTY_DOCUMENTATION_RELEASE_NOTES_URL = "documentation.release-notes.url";

	public static final String OAUTH_IDENTITY_PROVIDER_PJE_CLOUD = "oauth2_generic";

	// armazena o json do de-para do campo: estrutura-documentacao para o nome do path TODO - criar script para manter isso atualizado
	public static final String PROJECT_PROPERTY_DOCUMENTATION_STRUCTURE_TO_PATH = "documentacao.structure_to_pathname.json";

	public JiraGroups getGruposUsuario(JiraUser user) {
		JiraGroups groups = user.getGroups();
		if (groups == null) {
			JiraUser userDetailed = getUserWithGroups(user.getName());
			if(userDetailed != null) {
				groups = userDetailed.getGroups();
			}
		}
		return groups;
	}

	public boolean isLancadorVersao(JiraUser user) {
		return isUsuarioGrupo(user, GRUPO_LANCADORES_VERSAO, false);
	}

	public boolean isServico(JiraUser user) {
		return isUsuarioGrupo(user, GRUPO_SERVICOS, false);
	}
	
	public boolean isFabricaDesenvolvimento(JiraUser user) {
		return isUsuarioGrupo(user, GRUPO_FABRICA_DESENVOLVIMENTO, false);
	}
	
	public boolean isDesenvolvedor(JiraUser user) {
		return isUsuarioGrupo(user, PREFIXO_GRUPO_DESENVOLVEDORES, true);
	}

	public boolean isLiderProjeto(JiraUser user) {
		return isUsuarioGrupo(user, GRUPO_LIDERES_PROJETO, false);
	}

	public boolean isRevisorCodigo(JiraUser user) {
		return isUsuarioGrupo(user, GRUPO_REVISORES_CODIGO, false);
	}
	
	public boolean isUsuarioGrupo(JiraUser user, String nomeGrupo, Boolean checkPrefix) {
		boolean isUsuarioGrupo = false;
		try {
			JiraGroups grupos = getGruposUsuario(user);
			for (JiraGroup grupoUsuario : grupos.getItems()) {
				if ((!checkPrefix && grupoUsuario.getName().equalsIgnoreCase(nomeGrupo))
						|| (checkPrefix && grupoUsuario.getName().startsWith(nomeGrupo))) {
					isUsuarioGrupo = true;
					break;
				}
			}
		} catch (Exception e) {
			// ignore
		}

		return isUsuarioGrupo;
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

	public JiraGroup getGrupoDesenvolvimentoDeUsuario(JiraUser user) {
		JiraGroup grupoDesenvolvimento = null;
		try {
			JiraGroups grupos = getGruposUsuario(user);
			for (JiraGroup grupoUsuario : grupos.getItems()) {
				if (grupoUsuario.getName().startsWith(PREFIXO_GRUPO_DESENVOLVEDORES_FABRICA)) {
					grupoDesenvolvimento = grupoUsuario;
					break;
				}
			}
		} catch (Exception e) {
			// ignore
		}

		return grupoDesenvolvimento;
	}

	public String getTribunalUsuario(JiraUser user, boolean ignoreCNJ) {
		String tribunal = null;
		JiraGroup grupoTribunal = getGrupoTribunalUsuario(user);
		if (grupoTribunal != null) {
			tribunal = grupoTribunal.getName().replaceAll(PREFIXO_GRUPO_TRIBUNAL, "");
		}
		if(ignoreCNJ && !StringUtils.isBlank(tribunal) && tribunal.equalsIgnoreCase("CNJ")) {
			tribunal = null;
		}
		return tribunal;
	}
	
	public JiraCustomFieldOption getFabricaDesenvolvimentoDeSiglaTribunal(String siglaTribunal) {
		JiraCustomFieldOption option = null;
		if(StringUtils.isNotBlank(siglaTribunal)) {
			JiraCustomField customFieldDetails = getCustomFieldDetailed(FIELD_FABRICA_DESENVOLVIMENTO, siglaTribunal, true);
			option = getCustomFieldOption(siglaTribunal, null, customFieldDetails);
		}
		
		return option;
	}
	
	public JiraCustomFieldOption getRaiaFluxo(String raiaId) {
		JiraCustomFieldOption option = null;
		if(StringUtils.isNotBlank(raiaId)) {
			JiraCustomField customFieldDetails = getCustomFieldDetailed(FIELD_RAIA_FLUXO, null, true);
			option = getCustomFieldOption(raiaId, null, customFieldDetails);
		}		
		return option;
	}
	
	public JiraUser getUsuarioFabricaDesenvolvimentoDeSiglaTribunal(String siglaTribunal) {
		JiraUser usuarioFabricaTribunal = null;
		if(StringUtils.isNotBlank(siglaTribunal)) {
			siglaTribunal = JiraUtils.getSiglaTribunal(siglaTribunal);
			String nomeProvavelFabricaDesenvolvimento = PREFIXO_USUARIO_FABRICA_DESENVOLVIMENTO + siglaTribunal;
			usuarioFabricaTribunal = getUserWithGroups(nomeProvavelFabricaDesenvolvimento);
			if(usuarioFabricaTribunal == null) {
				String nomeProvavelUsuarioGeralTribunal = siglaTribunal;
				JiraUser usuarioGeralTribunal = getUserWithGroups(nomeProvavelUsuarioGeralTribunal);
				if(isFabricaDesenvolvimento(usuarioGeralTribunal)) {
					usuarioFabricaTribunal = usuarioGeralTribunal;
				}
			}
		}
		
		return usuarioFabricaTribunal;
	}

	public String getLabelAprovacaoCodigoDeUsuario(JiraUser user) {
		return getTribunalUsuario(user, false);
	}
	
	public String getGrupoDesenvolvimentoDeUsuario(String username) {
		String nomeGrupoDesenvolvimento = null;
		if(StringUtils.isNotBlank(username)) {
			JiraUser usuarioDesenvolvimento = getUserWithGroups(username);
			JiraGroup grupoDesenvolvimento = getGrupoDesenvolvimentoDeUsuario(usuarioDesenvolvimento);
			if(grupoDesenvolvimento != null && StringUtils.isNotBlank(grupoDesenvolvimento.getName())) {
				nomeGrupoDesenvolvimento = grupoDesenvolvimento.getName();
			}
		}
				
		return nomeGrupoDesenvolvimento;
	}
	
	public String getNomeFabricaDesenvolvimentoDeUsuario(String username) {
		String nomeFabricaDesenvolvimento = null;
		String nomeGrupoDesenvolvimento = getGrupoDesenvolvimentoDeUsuario(username);
		if(StringUtils.isNotBlank(nomeGrupoDesenvolvimento)) {
			nomeFabricaDesenvolvimento = "";
			nomeFabricaDesenvolvimento = nomeGrupoDesenvolvimento.replaceAll(PREFIXO_GRUPO_DESENVOLVEDORES_FABRICA, "");
			if(StringUtils.isNotBlank(nomeFabricaDesenvolvimento) && nomeFabricaDesenvolvimento.equalsIgnoreCase("CNJ_Basis")) {
				nomeFabricaDesenvolvimento = "CNJ-Terceiros";
			}
		}
		return nomeFabricaDesenvolvimento;
	}

	public String getJqlIssuesFromFixVersion(String version, boolean onlyClosed, String projectKey) {
		List<String> projectKeys = new ArrayList<String>();
		if(projectKey != null) {
			projectKeys.add(projectKey);
		}
		return getJqlIssuesFromFixVersion(version, onlyClosed, projectKeys);
	}

	public String getJqlIssuesFromFixVersion(String version, boolean onlyClosed, List<String> projectKeys) {
		return getJqlIssuesFromFixVersionWithIssueTypes(version, onlyClosed, projectKeys, null, null);
	}
	
	public String getJqlIssuesFromFixVersionWithIssueTypes(String version, boolean onlyClosed, List<String> projectKeys, List<String> issueTypesIds, Boolean includeIssueTypes) {
		String jql = "category in (PJE, PJE-CLOUD) AND fixVersion in (" + version + ")";
		if(onlyClosed) {
			jql += " AND status in (Fechado, Resolvido, 'Aguardando Fechamento da Versão')";
		}
		if(projectKeys != null && !projectKeys.isEmpty()) {
			jql += " AND project IN (" + String.join(", ", projectKeys) + ") ";
		}
		if(issueTypesIds != null && !issueTypesIds.isEmpty()) {
			jql += " AND issueType ";
			if(includeIssueTypes != null && !includeIssueTypes) {
				jql += " NOT ";
			}
			jql += " IN (" + String.join(", ", issueTypesIds) + ")";
		}
		jql += " ORDER BY project ASC, status ASC, priority DESC, key ASC";

		return jql;
	}

	public String getJqlIssuesLancamentoVersao(String version, String projectKeys, boolean onlyOpen) {
		StringBuilder jqlsb = new StringBuilder("");
		jqlsb.append(" issueType IN (").append(ISSUE_TYPE_NEW_VERSION).append(") ");
		if(onlyOpen) {
			jqlsb.append(" AND status NOT IN (Fechado, Resolvido) ");
		}
		jqlsb.append(" AND ").append(FIELD_AFFECTED_VERSION_TO_JQL).append(" IN (").append(version).append(") ");
		if(StringUtils.isNotBlank(projectKeys)) {
			jqlsb.append(" AND project IN (").append(projectKeys).append(") ");
		}else {
			jqlsb.append(" AND category IN (PJE, PJE-CLOUD) ");
		}

		return jqlsb.toString();
	}

	public String getJqlIssuesDocumentacaoReleaseNotes(String version, String opcaoEstruturaDocumentacaoId) {
		StringBuilder jqlsb = new StringBuilder("");
		jqlsb.append(" status not in (Fechado, Resolvido) ")
		.append(" AND issueType IN (").append(ISSUE_TYPE_RELEASE_NOTES).append(") ")
		.append(" AND ").append(JiraUtils.getFieldNameToJQL(FIELD_VERSAO_SER_LANCADA)).append(" ~ ").append(version).append(" ")
		.append(" AND ").append(JiraUtils.getFieldNameToJQL(FIELD_ESTRUTURA_DOCUMENTACAO)).append(" = ").append(opcaoEstruturaDocumentacaoId).append(" ")
		.append(" AND project in (").append(PROJECTKEY_PJEDOC).append(") ");

		return jqlsb.toString();
	}	

	public Integer countIssuesFromJql(String jql) {
		Integer totalIssues = 0;
		Map<String, String> options = new HashMap<>();
		if(StringUtils.isNotBlank(jql)) {
			options.put("jql", jql);

			JiraJQLSearchResponse response = searchIssuesWithJQL(options);
			if(response != null) {
				totalIssues = response.getTotal();
			}
		}
		return totalIssues;
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
				JiraJQLSearchResponse response = searchIssuesWithJQL(options);
				if(response != null && response.getIssues().size() > 0) {
					issues.addAll(response.getIssues());
					startAt += response.getMaxResults();
				}
				if(response == null || response.getIssues().size() == 0 || startAt >= response.getTotal()) {
					finalizado = true;
					break;
				}
			}
		}

		return issues;
	}

	@Cacheable(cacheNames = "filter")
	public JiraFilter getFilter(String filterId) {
		JiraFilter response = null;
		try {
			response = jiraClient.getFilter(filterId);
		}catch (Exception e) {
			String errorMesasge = "Erro ao buscar o filtro: "+ filterId + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
		
		return response;
	}

	public JiraJQLSearchResponse searchIssuesWithJQL(Map<String, String> options) {
		JiraJQLSearchResponse response = null;
		try {
			response = jiraClient.searchIssuesWithJQL(options);
		}catch (Exception e) {
			String errorMesasge = "Erro ao buscar issues do JQL: "+ options.toString() + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
		
		return response;
	}

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
			String fieldName = FIELD_TRIBUNAIS_REQUISITANTES;
			Map<String, Object> updateField = createUpdateObject(fieldName, listaTribunaisAtualizada, "ADD");
			if(updateField != null && updateField.get(fieldName) != null) {
				updateFields.put(fieldName, updateField.get(fieldName));
			}
		}
	}

	public void atualizarTribunaisRevisores(JiraIssue issue, List<String> novosTribunaisRevisores, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		List<String> tribunaisRevisores = JiraUtils.translateFieldOptionsToValueList(getTribunaisRevisores(issueDetalhada));
		boolean houveAlteracao = false;
		if (tribunaisRevisores != null && novosTribunaisRevisores != null) {
			houveAlteracao = !(tribunaisRevisores.containsAll(novosTribunaisRevisores) && novosTribunaisRevisores.containsAll(tribunaisRevisores));
		}else if(!(tribunaisRevisores == null && novosTribunaisRevisores == null)) {
			houveAlteracao = true;
		}
		if (houveAlteracao) {
			String fieldName = FIELD_TRIBUNAIS_RESPONSAVEIS_REVISAO;
			Map<String, Object> updateField = createUpdateObject(fieldName, novosTribunaisRevisores, "UPDATE");
			if(updateField != null && updateField.get(fieldName) != null) {
				updateFields.put(fieldName, updateField.get(fieldName));
			}
		}
	}

	public void atualizarResponsaveisRevisao(JiraIssue issue, List<JiraUser> novosUsuariosRevisores, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		List<JiraUser> usuariosRevisores = issueDetalhada.getFields().getResponsaveisRevisao();
		boolean houveAlteracao = false;
		if (usuariosRevisores != null && novosUsuariosRevisores != null) {
			houveAlteracao = !(usuariosRevisores.containsAll(novosUsuariosRevisores) && novosUsuariosRevisores.containsAll(usuariosRevisores));
		}else if(!(usuariosRevisores == null && novosUsuariosRevisores == null)) {
			houveAlteracao = true;
		}
		if (houveAlteracao) {
			String fieldName = FIELD_USUARIOS_RESPONSAVEIS_REVISAO;
			Map<String, Object> updateField = createUpdateObject(fieldName, novosUsuariosRevisores, "UPDATE");
			if(updateField != null && updateField.get(fieldName) != null) {
				updateFields.put(fieldName, updateField.get(fieldName));
			}
		}
	}

	public void atualizarAreasConhecimento(JiraIssue issue, List<String> novasAreasConhecimento, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		if(issueDetalhada != null && issueDetalhada.getFields() != null) {
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

	private JiraVersion getProjectVersion(JiraIssue issue, String versionName, boolean createIfDoesNotExists) {
		JiraVersion version = null;
		String projectKey = null;
		if(issue != null && issue.getFields() != null && issue.getFields().getProject() != null && issue.getFields().getProject().getKey() != null) {
			projectKey = issue.getFields().getProject().getKey();
		}else {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
			if(issueDetalhada != null && issueDetalhada.getFields() != null && issueDetalhada.getFields().getProject() != null && issueDetalhada.getFields().getProject().getKey() != null) {
				projectKey = issue.getFields().getProject().getKey();
			}
		}
		version = findProjecVersion(projectKey, versionName);
		if(version == null && createIfDoesNotExists) {
			version = createProjectVersion(projectKey, versionName, null, null);
		}
		return version;
	}

	public void atualizarFixVersion(JiraIssue issue, String fixVersion, Map<String, Object> updateFields, boolean replaceValue) throws Exception {
		JiraVersion version = getProjectVersion(issue, fixVersion, true);
		atualizarFixVersion(issue, version, updateFields, replaceValue);
	}

	public void atualizarFixVersion(JiraIssue issue,  JiraVersion fixVersion, Map<String, Object> updateFields, boolean replaceValue) throws Exception {
		if (fixVersion != null) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

			boolean houveAlteracao = false;
			if(issueDetalhada.getFields().getFixVersions() == null || issueDetalhada.getFields().getFixVersions().isEmpty() 
					|| !JiraUtils.containsVersion(issueDetalhada.getFields().getFixVersions(), fixVersion)) {
				houveAlteracao = true;
			}

			if(houveAlteracao || replaceValue) {
				List<JiraVersion> fixVersions = issueDetalhada.getFields().getFixVersions();
				if(fixVersions == null || replaceValue) {
					fixVersions = new ArrayList<>();
				}
				fixVersions.add(fixVersion);

				Map<String, Object> updateField = createUpdateObject(FIELD_FIX_VERSION, fixVersions, "UPDATE");
				if(updateField != null && updateField.get(FIELD_FIX_VERSION) != null) {
					updateFields.put(FIELD_FIX_VERSION, updateField.get(FIELD_FIX_VERSION));
				}
			}
		}		
	}

	public void atualizarIntegradoNosBranches(JiraIssue issue, String branchName, Map<String, Object> updateFields, boolean replaceValue) throws Exception {
		JiraVersion branch = getProjectVersion(issue, branchName, true);
		atualizarIntegradoNosBranches(issue, branch, updateFields, replaceValue);
	}

	public void atualizarIntegradoNosBranches(JiraIssue issue,  JiraVersion branch, Map<String, Object> updateFields, boolean replaceValue) throws Exception {
		if (branch != null) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

			boolean houveAlteracao = false;
			if(issueDetalhada.getFields().getIntegradoNosBranches() == null || issueDetalhada.getFields().getIntegradoNosBranches().isEmpty() 
					|| !JiraUtils.containsVersion(issueDetalhada.getFields().getIntegradoNosBranches(), branch)) {
				houveAlteracao = true;
			}

			if(houveAlteracao || replaceValue) {
				List<JiraVersion> branches = issueDetalhada.getFields().getIntegradoNosBranches();
				if(branches == null || replaceValue) {
					branches = new ArrayList<>();
				}
				branches.add(branch);

				Map<String, Object> updateField = createUpdateObject(FIELD_INTEGRADO_NOS_BRANCHES, branches, "UPDATE");
				if(updateField != null && updateField.get(FIELD_INTEGRADO_NOS_BRANCHES) != null) {
					updateFields.put(FIELD_INTEGRADO_NOS_BRANCHES, updateField.get(FIELD_INTEGRADO_NOS_BRANCHES));
				}
			}
		}		
	}

	public void atualizarResponsavelIssue(JiraIssue issue,  JiraUser usuario, Map<String, Object> updateFields) throws Exception {
		if (usuario != null) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

			boolean houveAlteracao = false;
			if(issueDetalhada.getFields().getAssignee() == null || !issueDetalhada.getFields().getAssignee().getKey().equals(usuario.getKey())) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_RESPONSAVEL_ISSUE, usuario, "UPDATE");
				if(updateField != null && updateField.get(FIELD_RESPONSAVEL_ISSUE) != null) {
					updateFields.put(FIELD_RESPONSAVEL_ISSUE, updateField.get(FIELD_RESPONSAVEL_ISSUE));
				}
			}
		}		
	}

	public void atualizarResponsavelRevisao(JiraIssue issue,  JiraUser usuario, Map<String, Object> updateFields) throws Exception {
		if (usuario != null) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

			boolean houveAlteracao = false;
			if(issueDetalhada.getFields().getResponsavelRevisao() == null || !issueDetalhada.getFields().getResponsavelRevisao().getKey().equals(usuario.getKey())) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_RESPONSAVEL_REVISAO, usuario, "UPDATE");
				if(updateField != null && updateField.get(FIELD_RESPONSAVEL_REVISAO) != null) {
					updateFields.put(FIELD_RESPONSAVEL_REVISAO, updateField.get(FIELD_RESPONSAVEL_REVISAO));
				}
			}
		}		
	}

	public void atualizarResponsavelCodificacao(JiraIssue issue,  JiraUser usuario, Map<String, Object> updateFields) throws Exception {
		if (usuario != null) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

			boolean houveAlteracao = false;
			if(issueDetalhada.getFields().getResponsavelCodificacao() == null || !issueDetalhada.getFields().getResponsavelCodificacao().getKey().equals(usuario.getKey())) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_RESPONSAVEL_CODIFICACAO, usuario, "UPDATE");
				if(updateField != null && updateField.get(FIELD_RESPONSAVEL_CODIFICACAO) != null) {
					updateFields.put(FIELD_RESPONSAVEL_CODIFICACAO, updateField.get(FIELD_RESPONSAVEL_CODIFICACAO));
				}
			}
		}		
	}

	public void atualizarBussinessValue(JiraIssue issue,  Integer bussinessValue, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if((issueDetalhada.getFields().getBusinessValue() == null) || !issueDetalhada.getFields().getBusinessValue().equals(bussinessValue)) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_BUSSINESS_VALUE, bussinessValue, "UPDATE");
			if(updateField != null && updateField.get(FIELD_BUSSINESS_VALUE) != null) {
				updateFields.put(FIELD_BUSSINESS_VALUE, updateField.get(FIELD_BUSSINESS_VALUE));
			}
		}
	}

	public void atualizarAprovadoPor(JiraIssue issue,  String textoAprovadoPor, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if((issueDetalhada.getFields().getAprovadoPor() == null) || !issueDetalhada.getFields().getAprovadoPor().equals(textoAprovadoPor)) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_APROVADO_POR, textoAprovadoPor, "UPDATE");
			if(updateField != null && updateField.get(FIELD_APROVADO_POR) != null) {
				updateFields.put(FIELD_APROVADO_POR, updateField.get(FIELD_APROVADO_POR));
			}
		}
	}
	
	public void atualizarAprovacoesRealizadas(JiraIssue issue,  Integer aprovacoesRealizadas, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if((issueDetalhada.getFields().getAprovacoesRealizadas() == null) || !issueDetalhada.getFields().getAprovacoesRealizadas().equals(aprovacoesRealizadas)) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_APROVACOES_REALIZADAS, aprovacoesRealizadas, "UPDATE");
			if(updateField != null && updateField.get(FIELD_APROVACOES_REALIZADAS) != null) {
				updateFields.put(FIELD_APROVACOES_REALIZADAS, updateField.get(FIELD_APROVACOES_REALIZADAS));
			}
		}
	}

	public void atualizarFabricaDesenvolvimento(JiraIssue issue,  String nomeFabrica, Map<String, Object> updateFields) throws Exception {
		JiraCustomFieldOption fabricaDesenvolvimento = getFabricaDesenvolvimentoDeSiglaTribunal(nomeFabrica);
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if((issueDetalhada.getFields().getFabricaDesenvolvimento() == null && fabricaDesenvolvimento != null)
				|| (issueDetalhada.getFields().getFabricaDesenvolvimento() != null && fabricaDesenvolvimento == null)
				|| ((issueDetalhada.getFields().getFabricaDesenvolvimento() != null && fabricaDesenvolvimento != null)
						&& !issueDetalhada.getFields().getFabricaDesenvolvimento().getId().equals(fabricaDesenvolvimento.getId()))) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_FABRICA_DESENVOLVIMENTO, fabricaDesenvolvimento, "UPDATE");
			if(updateField != null) {
				updateFields.put(FIELD_FABRICA_DESENVOLVIMENTO, updateField.get(FIELD_FABRICA_DESENVOLVIMENTO));
			}
		}
	}
	
	public void atualizarRaiaFluxo(JiraIssue issue,  String raiaId, Map<String, Object> updateFields) throws Exception {
		JiraCustomFieldOption raiaFluxo = getRaiaFluxo(raiaId);
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if((issueDetalhada.getFields().getRaiaDoFluxo() == null && raiaFluxo != null)
				|| (issueDetalhada.getFields().getRaiaDoFluxo() != null && raiaFluxo == null)
				|| ((issueDetalhada.getFields().getRaiaDoFluxo() != null && raiaFluxo != null)
						&& !issueDetalhada.getFields().getRaiaDoFluxo().getId().equals(raiaFluxo.getId()))) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_RAIA_FLUXO, raiaFluxo, "UPDATE");
			if(updateField != null) {
				updateFields.put(FIELD_RAIA_FLUXO, updateField.get(FIELD_RAIA_FLUXO));
			}
		}
	}
	
	public void atualizarGrupoResponsavelAtribuicao(JiraIssue issue,  String nomeGrupo, Map<String, Object> updateFields) throws Exception {
		JiraGroup grupo = getGroup(nomeGrupo, null);
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if((issueDetalhada.getFields().getGrupoAtribuicao() == null && grupo != null)
				|| (issueDetalhada.getFields().getGrupoAtribuicao() != null && grupo == null)
				|| ((issueDetalhada.getFields().getGrupoAtribuicao() != null && grupo != null)
						&& !issueDetalhada.getFields().getGrupoAtribuicao().getName().equals(grupo.getName()))) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_GRUPO_RESPONSAVEL_ATRIBUICAO, grupo, "UPDATE");
			if(updateField != null) {
				updateFields.put(FIELD_GRUPO_RESPONSAVEL_ATRIBUICAO, updateField.get(FIELD_GRUPO_RESPONSAVEL_ATRIBUICAO));
			}
		}
	}

	public void atualizarDataReleaseNotes(JiraIssue issue,  String dataReleaseNotes, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if(StringUtils.isBlank(issueDetalhada.getFields().getDtDisponibilizacaoDocumentacao()) || !issueDetalhada.getFields().getDtDisponibilizacaoDocumentacao().equals(dataReleaseNotes)) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_DATA_RELEASE_NOTES, dataReleaseNotes, "UPDATE");
			if(updateField != null && updateField.get(FIELD_DATA_RELEASE_NOTES) != null) {
				updateFields.put(FIELD_DATA_RELEASE_NOTES, updateField.get(FIELD_DATA_RELEASE_NOTES));
			}
		}
	}

	public void atualizarURLPublicacao(JiraIssue issue,  String urlReleaseNotes, Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if(StringUtils.isBlank(issueDetalhada.getFields().getUrlPublicacaoDocumentacao()) || !issueDetalhada.getFields().getUrlPublicacaoDocumentacao().equalsIgnoreCase(urlReleaseNotes)) {
			houveAlteracao = true;
		}

		if(houveAlteracao) {
			Map<String, Object> updateField = createUpdateObject(FIELD_URL_RELEASE_NOTES, urlReleaseNotes, "UPDATE");
			if(updateField != null && updateField.get(FIELD_URL_RELEASE_NOTES) != null) {
				updateFields.put(FIELD_URL_RELEASE_NOTES, updateField.get(FIELD_URL_RELEASE_NOTES));
			}
		}
	}

	public void atualizarBranchRelacionado(JiraIssue issue,  String branchName, Map<String, Object> updateFields, boolean replaceValue) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if(StringUtils.isBlank(issueDetalhada.getFields().getBranchesRelacionados()) || !issueDetalhada.getFields().getBranchesRelacionados().contains(branchName)) {
			houveAlteracao = true;
		}

		if(houveAlteracao || replaceValue) {
			String branchesRelacionados = null;
			if(replaceValue) {
				branchesRelacionados = branchName;
			}else {
				branchesRelacionados = Utils.addOption(issueDetalhada.getFields().getBranchesRelacionados(), branchName);
			}
			Map<String, Object> updateField = createUpdateObject(FIELD_BRANCHES_RELACIONADOS, branchesRelacionados, "UPDATE");
			if(updateField != null && updateField.get(FIELD_BRANCHES_RELACIONADOS) != null) {
				updateFields.put(FIELD_BRANCHES_RELACIONADOS, updateField.get(FIELD_BRANCHES_RELACIONADOS));
			}
		}
	}

	public void atualizarMensagemRocketchat(JiraIssue issue,  String texto, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(texto)) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

			boolean houveAlteracao = false;
			if(StringUtils.isBlank(issueDetalhada.getFields().getMensagemRocketchat()) || !issueDetalhada.getFields().getMensagemRocketchat().equals(texto)) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_MENSAGEM_ROCKETCHAT, texto, "UPDATE");
				if(updateField != null && updateField.get(FIELD_MENSAGEM_ROCKETCHAT) != null) {
					updateFields.put(FIELD_MENSAGEM_ROCKETCHAT, updateField.get(FIELD_MENSAGEM_ROCKETCHAT));
				}
			}
		}		
	}

	public void atualizarMensagemSlack(JiraIssue issue,  String texto, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(texto)) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

			boolean houveAlteracao = false;
			if(StringUtils.isBlank(issueDetalhada.getFields().getMensagemRocketchat()) || !issueDetalhada.getFields().getMensagemRocketchat().equals(texto)) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_MENSAGEM_SLACK, texto, "UPDATE");
				if(updateField != null && updateField.get(FIELD_MENSAGEM_SLACK) != null) {
					updateFields.put(FIELD_MENSAGEM_SLACK, updateField.get(FIELD_MENSAGEM_SLACK));
				}
			}
		}		
	}

	public void atualizarMensagemTelegram(JiraIssue issue,  String texto, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(texto)) {
			JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

			boolean houveAlteracao = false;
			if(StringUtils.isBlank(issueDetalhada.getFields().getMensagemRocketchat()) || !issueDetalhada.getFields().getMensagemRocketchat().equals(texto)) {
				houveAlteracao = true;
			}

			if(houveAlteracao) {
				Map<String, Object> updateField = createUpdateObject(FIELD_MENSAGEM_TELEGRAM, texto, "UPDATE");
				if(updateField != null && updateField.get(FIELD_MENSAGEM_TELEGRAM) != null) {
					updateFields.put(FIELD_MENSAGEM_TELEGRAM, updateField.get(FIELD_MENSAGEM_TELEGRAM));
				}
			}
		}		
	}
	public void atualizarMRsAbertos(JiraIssue issue,  String mrAberto, Map<String, Object> updateFields, boolean replaceValue) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean alterarCampo = false;
		if(StringUtils.isBlank(issueDetalhada.getFields().getMrAbertos()) || !issueDetalhada.getFields().getMrAbertos().contains(mrAberto)) {
			alterarCampo = true;
		}else if(replaceValue && !issueDetalhada.getFields().getMrAbertos().equals(mrAberto)) {
			alterarCampo = true;
		}
		
		if(alterarCampo) {
			String mrsAbertos = null;
			if(replaceValue) {
				mrsAbertos = mrAberto;
			}else {
				mrsAbertos = Utils.addOption(issueDetalhada.getFields().getMrAbertos(), mrAberto);
			}

			Map<String, Object> updateField = createUpdateObject(FIELD_MRS_ABERTOS, mrsAbertos, "UPDATE");
			if(updateField != null && updateField.get(FIELD_MRS_ABERTOS) != null) {
				updateFields.put(FIELD_MRS_ABERTOS, updateField.get(FIELD_MRS_ABERTOS));
			}
		}
	}

	public void atualizarMRsAceitos(JiraIssue issue,  String mrAceito, Map<String, Object> updateFields, boolean replaceValue) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);

		boolean houveAlteracao = false;
		if(StringUtils.isBlank(issueDetalhada.getFields().getMrAceitos()) || !issueDetalhada.getFields().getMrAceitos().contains(mrAceito)) {
			houveAlteracao = true;
		}

		if(houveAlteracao || replaceValue) {
			String mrsAceitos = null;
			if(replaceValue) {
				mrsAceitos = mrAceito;
			}else {
				mrsAceitos = Utils.addOption(issueDetalhada.getFields().getMrAceitos(), mrAceito);
			}
			Map<String, Object> updateField = createUpdateObject(FIELD_MRS_ACEITOS, mrsAceitos, "UPDATE");
			if(updateField != null && updateField.get(FIELD_MRS_ACEITOS) != null) {
				updateFields.put(FIELD_MRS_ACEITOS, updateField.get(FIELD_MRS_ACEITOS));
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

	public void removerPublicarDocumentacaoAutomaticamente(JiraIssue issue,  Map<String, Object> updateFields) throws Exception {
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		if(issueDetalhada.getFields().getPublicarDocumentacaoAutomaticamente() != null && !issueDetalhada.getFields().getPublicarDocumentacaoAutomaticamente().isEmpty()) {
			updateFields.put(FIELD_PUBLICAR_DOCUMENTACAO_AUTOMATICAMENTE, new ArrayList<>());
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

	public void criarNovoLink(JiraIssue issue, String destinationIssueKey, 
			String issuelinkTypeId, String issueLinkName, boolean isOutward, Map<String, Object> updateFields) throws Exception {
		if (StringUtils.isNotBlank(destinationIssueKey) && StringUtils.isNotBlank(issuelinkTypeId)) {
			JiraIssue issueDetalhada = null;
			if(issue != null) {
				issueDetalhada = recuperaIssueDetalhada(issue);
			}
			JiraIssue issueDestino = recuperaIssueDetalhada(destinationIssueKey);

			if(issueDestino != null && issueDetalhada != null) {
				boolean houveAlteracao = true;
				if(issueDetalhada.getFields() != null && issueDetalhada.getFields().getIssuelinks() != null) {
					for (JiraIssueLinkRequest issueLink : issueDetalhada.getFields().getIssuelinks()) {
						if(issueLink.getIssueLinkType() != null && issueLink.getIssueLinkType().getId() != null 
								&& !issueLink.getIssueLinkType().getId().toString().equals(issuelinkTypeId)) {
							if(issueLink.getOutwardIssue() != null && issueLink.getOutwardIssue().getId().equals(issueDestino.getId())) {
								houveAlteracao = false;
							}else if(issueLink.getInwardIssue() != null && issueLink.getInwardIssue().getId().equals(issueDestino.getId())) {
								houveAlteracao = false;
							}
						}
					}
				}

				if(houveAlteracao || issue == null) {
					JiraIssueLinkTypeRequest issueLinkType = new JiraIssueLinkTypeRequest();
					if(isOutward) {
						issueLinkType.setOutwardName(issueLinkName);
					}else {
						issueLinkType.setInwardName(issueLinkName);
					}
					issueLinkType.setId(issuelinkTypeId);

					JiraIssueLinkRequest issueLink = new JiraIssueLinkRequest();
					JiraIssue relatedIssue = new JiraIssue();
					relatedIssue.setKey(issueDestino.getKey());
					if(isOutward) {
						issueLink.setOutwardIssue(relatedIssue);
					}else {
						issueLink.setInwardIssue(relatedIssue);
					}
					issueLink.setIssueLinkType(issueLinkType);
					Map<String, Object> updateField = createUpdateObject(FIELD_ISSUELINKS, issueLink, "ADD");
					if(updateField != null && updateField.get(FIELD_ISSUELINKS) != null) {
						updateFields.put(FIELD_ISSUELINKS, updateField.get(FIELD_ISSUELINKS));
					}
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

	public void novaIssueCriarDescription(String descriptionText, Map<String, Object> issueFields) throws Exception {
		if (StringUtils.isNotBlank(descriptionText)) {

			Map<String, Object> issueField = new HashMap<>();
			issueField.put(FIELD_DESCRIPTION, descriptionText);
			if(issueField != null && issueField.get(FIELD_DESCRIPTION) != null) {
				issueFields.put(FIELD_DESCRIPTION, issueField.get(FIELD_DESCRIPTION));
			}
		}		
	}

	public void novaIssueCriarVersaoASerLancada(String versaoASerLancada, Map<String, Object> issueFields) throws Exception {
		if (StringUtils.isNotBlank(versaoASerLancada)) {

			Map<String, Object> issueField = new HashMap<>();
			issueField.put(FIELD_VERSAO_SER_LANCADA, versaoASerLancada);
			if(issueField != null && issueField.get(FIELD_VERSAO_SER_LANCADA) != null) {
				issueFields.put(FIELD_VERSAO_SER_LANCADA, issueField.get(FIELD_VERSAO_SER_LANCADA));
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

	public void novaIssueCriarIssueTypeId(String issueTypeId, Map<String, Object> issueFields) throws Exception {
		if (issueTypeId != null) {

			Map<String, String> issueType = new HashMap<>();
			issueType.put("id", issueTypeId);

			Map<String, Map<String, String>> issueField = new HashMap<>();
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

	public void novaIssueCriarIndicacaoPrepararVersaoAutomaticamente(Map<String, Object> issueFields) throws Exception {
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

	public void novaIssueCriarIndicacaoPublicarDocumentacaoAutomaticamente(Map<String, Object> issueFields) throws Exception {
		String textoGeracaoAutomatica = "Sim";
		Map<String, String> opcaoGeracaoAutomatica = new HashMap<>();
		opcaoGeracaoAutomatica.put("value", textoGeracaoAutomatica);
		List<Map<String, String>> listOpcoes = new ArrayList<>();
		listOpcoes.add(opcaoGeracaoAutomatica);

		Map<String, List<Map<String, String>>> issueField = new HashMap<>();
		issueField.put(FIELD_PUBLICAR_DOCUMENTACAO_AUTOMATICAMENTE, listOpcoes);
		if(issueField != null && issueField.get(FIELD_PUBLICAR_DOCUMENTACAO_AUTOMATICAMENTE) != null) {
			issueFields.put(FIELD_PUBLICAR_DOCUMENTACAO_AUTOMATICAMENTE, issueField.get(FIELD_PUBLICAR_DOCUMENTACAO_AUTOMATICAMENTE));
		}
	}

	public void novaIssueCriarEstruturaDocumentacao(String identificacaoEstruturaDocumentacao, Map<String, Object> issueFields) throws Exception {
		if (StringUtils.isNotBlank(identificacaoEstruturaDocumentacao)) {
			String[] options = identificacaoEstruturaDocumentacao.split(":");

			String parentOptionId = null;
			String cascadeOptionId = null;
			if(options != null && options.length > 0) {
				parentOptionId = options[0];
				if(options.length > 1) {
					cascadeOptionId = options[1];
				}
			}
			Map<String, Object> customFieldOption = new HashMap<>();
			customFieldOption.put("id", parentOptionId);
			if(StringUtils.isNotBlank(cascadeOptionId)) {
				Map<String, String> customFieldChildOption = new HashMap<>();
				customFieldChildOption.put("id", cascadeOptionId);
				customFieldOption.put("child", customFieldChildOption);
			}

			Map<String, Object> issueField = new HashMap<>();
			issueField.put(FIELD_ESTRUTURA_DOCUMENTACAO, customFieldOption);
			if(issueField != null && issueField.get(FIELD_ESTRUTURA_DOCUMENTACAO) != null) {
				issueFields.put(FIELD_ESTRUTURA_DOCUMENTACAO, issueField.get(FIELD_ESTRUTURA_DOCUMENTACAO));
			}
		}		
	}

	public void novaIssueCriarTipoVersao(JiraIssueTipoVersaoEnum tipoVersao, Map<String, Object> issueFields) throws Exception {
		if(tipoVersao != null) {
			Map<String, String> opcaoTipoVersao = new HashMap<>();
			opcaoTipoVersao.put("value", tipoVersao.toString());
			
			Map<String, Map<String, String>> issueField = new HashMap<>();
			issueField.put(FIELD_VERSION_TYPE, opcaoTipoVersao);
			if(issueField != null && issueField.get(FIELD_VERSION_TYPE) != null) {
				issueFields.put(FIELD_VERSION_TYPE, issueField.get(FIELD_VERSION_TYPE));
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
						FIELD_URL_RELEASE_NOTES.equals(fieldName) ||
						FIELD_MRS_ACEITOS.equals(fieldName) ||
						FIELD_MRS_ABERTOS.equals(fieldName) ||
						FIELD_BRANCHES_RELACIONADOS.equals(fieldName) ||
						FIELD_APROVADO_POR.equals(fieldName) || 
						
						FIELD_MENSAGEM_ROCKETCHAT.equals(fieldName) ||
						FIELD_MENSAGEM_SLACK.equals(fieldName) ||
						FIELD_MENSAGEM_TELEGRAM.equals(fieldName) ||

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
						Utils.getDateFromString(dateTimeStr);
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
		}else if(FIELD_TRIBUNAIS_REQUISITANTES.equals(fieldName) || FIELD_AREAS_CONHECIMENTO.equals(fieldName)
				|| FIELD_TRIBUNAIS_RESPONSAVEIS_REVISAO.equals(fieldName)) {
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
		}else if(FIELD_ISSUELINKS.equals(fieldName) && valueToUpdate != null) {
			boolean identificouCampo = false;
			if(valueToUpdate instanceof JiraIssueLinkRequest) {
				JiraIssueLinkRequest isuseLink = (JiraIssueLinkRequest)valueToUpdate;
				Map<String, Object> newIssueLink = new HashMap<>();
				newIssueLink.put("add", isuseLink);
				List<Map<String, Object>> issueLinkArray = new ArrayList<>();
				issueLinkArray.add(newIssueLink);
				objectToUpdate.put(fieldName, issueLinkArray);
				identificouCampo = true;
			}
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser JiraIssueLinkRequest, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if(valueToUpdate != null &&
				(
						FIELD_INTEGRADO_NOS_BRANCHES.equals(fieldName)
						|| FIELD_FIX_VERSION.equals(fieldName)
						)) {
			boolean identificouCampo = false;
			if(valueToUpdate instanceof List) {
				@SuppressWarnings("rawtypes")
				List<?> list = (List) valueToUpdate;
				List<Map<String, Object>> jiraVersionArray = new ArrayList<>();

				int numConvertedElements = 0;
				Map<String, Object> newJiraVersions = new HashMap<>();
				List<Map<String, Object>> versionsArray = new ArrayList<Map<String,Object>>();
				for (Object object : list) {
					if(object instanceof JiraVersion) {
						JiraVersion version = (JiraVersion) object;
						Map<String, Object> versionObj = new HashMap<>();
						versionObj.put("id", version.getId().toString());
						versionObj.put("name", version.getName());
						versionObj.put("projectId", version.getProjectId());

						versionsArray.add(versionObj);
					}
				}
				numConvertedElements = versionsArray.size();
				newJiraVersions.put("set", versionsArray);
				jiraVersionArray.add(newJiraVersions);

				if(numConvertedElements != list.size()) {
					throw new Exception("Algum dos valores para a identificação de versão não pode ser identificado.");
				}
				objectToUpdate.put(fieldName, jiraVersionArray);
				identificouCampo = true;
			}
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser List<JiraVersion>, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if(
				FIELD_RESPONSAVEL_REVISAO.equals(fieldName)
				|| FIELD_RESPONSAVEL_CODIFICACAO.equals(fieldName)
				|| FIELD_RESPONSAVEL_ISSUE.equals(fieldName)
						) {
			boolean identificouCampo = false;
			identificouCampo = (valueToUpdate == null || valueToUpdate instanceof JiraUser);
			Map<String, Object> userObj = new HashMap<>();
			if(valueToUpdate != null && valueToUpdate instanceof JiraUser) {
				JiraUser user = (JiraUser)valueToUpdate;
				userObj.put("key", user.getKey());
				userObj.put("name", user.getName());
			}
			Map<String, Object> newJiraUser = new HashMap<>();
			newJiraUser.put("set", userObj);

			List<Map<String, Object>> jiraUserArray = new ArrayList<>();
			jiraUserArray.add(newJiraUser);

			objectToUpdate.put(fieldName, jiraUserArray);
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser JiraUser, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if(FIELD_USUARIOS_RESPONSAVEIS_REVISAO.equals(fieldName)) {
			boolean identificouCampo = false;
			
			if(valueToUpdate instanceof List) {
				@SuppressWarnings("rawtypes")
				List<?> list = (List) valueToUpdate;
				List<Map<String, Object>> jiraUsersArray = new ArrayList<>();

				int numConvertedElements = 0;
				Map<String, Object> newJiraUsers = new HashMap<>();
				List<Map<String, Object>> usersArray = new ArrayList<Map<String,Object>>();
				for (Object object : list) {
					if(object instanceof JiraUser) {
						JiraUser user = (JiraUser) object;
						Map<String, Object> userObj = new HashMap<>();
						userObj.put("key", user.getKey());
						userObj.put("name", user.getName());

						usersArray.add(userObj);
					}
				}
				numConvertedElements = usersArray.size();
				newJiraUsers.put("set", usersArray);
				jiraUsersArray.add(newJiraUsers);

				if(numConvertedElements != list.size()) {
					throw new Exception("Algum dos valores para a identificação de usuários não pôde ser identificado.");
				}
				objectToUpdate.put(fieldName, jiraUsersArray);
				identificouCampo = true;
			}
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser List<JiraVersion>, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if((
						FIELD_GRUPO_RESPONSAVEL_ATRIBUICAO.equals(fieldName)
				)) {
			boolean identificouCampo = false;
			identificouCampo = (valueToUpdate == null || valueToUpdate instanceof JiraGroup);
			Map<String, Object> groupObj = new HashMap<>();
			if(valueToUpdate != null && valueToUpdate instanceof JiraGroup) {
				JiraGroup group = (JiraGroup)valueToUpdate;
				groupObj.put("name", group.getName());
			}

			Map<String, Object> newJiraGroup = new HashMap<>();
			newJiraGroup.put("set", groupObj);

			List<Map<String, Object>> jiraGroupArray = new ArrayList<>();
			jiraGroupArray.add(newJiraGroup);

			objectToUpdate.put(fieldName, jiraGroupArray);

			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser JiraGroup, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if(
						FIELD_FABRICA_DESENVOLVIMENTO.equals(fieldName)
						|| FIELD_RAIA_FLUXO.equals(fieldName)) {
			boolean identificouCampo = false;
			identificouCampo = (valueToUpdate == null || valueToUpdate instanceof JiraCustomFieldOption);
			Map<String, Object> newOption = new HashMap<>();
			Map<String, String> optionObj = new HashMap<>();
			List<Map<String, Object>> jiraOptionArray = new ArrayList<>();
			if(valueToUpdate != null && valueToUpdate instanceof JiraCustomFieldOption) {
				JiraCustomFieldOption customFieldOption = (JiraCustomFieldOption)valueToUpdate;
				optionObj.put("id", customFieldOption.getId().toString());
				newOption.put("set", optionObj);
				jiraOptionArray.add(newOption);
			}

			objectToUpdate.put(fieldName, jiraOptionArray);
			
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser JiraCustomFieldOption, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}else if(
				FIELD_BUSSINESS_VALUE.equals(fieldName)
				|| FIELD_APROVACOES_REALIZADAS.equals(fieldName)) {

			boolean identificouCampo = false;
			identificouCampo = (valueToUpdate == null || valueToUpdate instanceof Integer);
			Map<String, Object> newOption = new HashMap<>();
			newOption.put("set", (Integer) valueToUpdate);
			List<Map<String, Object>> jiraOptionArray = new ArrayList<>();
			jiraOptionArray.add(newOption);
			objectToUpdate.put(fieldName, jiraOptionArray);
			
			if(!identificouCampo) {
				throw new Exception("Valor para update fora do padrão - deveria ser Integer, recebeu: " +  valueToUpdate.getClass().getTypeName());
			}
		}
		return objectToUpdate;
	}

	@Cacheable(cacheNames = "issue-type")
	public JiraIssuetype getIssueType(String issueTypeId) { 
		JiraIssuetype issueType = null;
		try {
			issueType = jiraClient.getIssueType(issueTypeId);
		}catch (Exception e) {
			String errorMesasge = "Erro ao buscar issue-type: "+ issueTypeId + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}

		return issueType;
	}
	
	public JiraIssuetype getIssueTypeLancamentoVersao() {
		return getIssueType(ISSUE_TYPE_NEW_VERSION);
	}
	
	@Cacheable(cacheNames = "custom-field-detail")
	public JiraCustomField getCustomFieldDetailed (String customFieldName, String searchItems, Boolean onlyEnabled) { 
		Map<String, String> options = new HashMap<>();
		if(searchItems != null) {
			options.put("search", searchItems);
		}
		if(onlyEnabled == null) {
			onlyEnabled = Boolean.FALSE;
		}
		if(onlyEnabled) {
			options.put("onlyEnabled", onlyEnabled.toString());
		}

		JiraCustomField customFieldOptions = null;
		try {
			customFieldOptions = jiraClient.getCustomFieldOptions(customFieldName, options);
		}catch (Exception e) {
			String errorMesasge = "Erro ao buscar customfield: "+ customFieldName + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}

		return customFieldOptions;
	}

	public JiraCustomFieldOption findSprintDoGrupo(String sprintDoGrupo, boolean onlyEnabled) {
		JiraCustomFieldOption option = null;
		if(StringUtils.isNotBlank(sprintDoGrupo)) {
			JiraCustomField customFieldDetails = getCustomFieldDetailed(FIELD_SPRINT_DO_GRUPO, sprintDoGrupo, onlyEnabled);
			option = getCustomFieldOption(sprintDoGrupo, null, customFieldDetails);
		}
		
		return option;
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
						rocketchatService.sendBotMessage(errorMesasge);
						telegramService.sendBotMessage(errorMesasge);
					}
				}				
			}
		}
		return superEpicThemes;
	}

	@Cacheable(cacheNames = "issue-workflow")
	public JiraWorkflow getIssueWorkflow(String issueKey) {
		JiraWorkflow workflow = null;
		try {
			workflow = jiraClient.getIssueWorkflow(issueKey);
		}catch (Exception e) {
			String errorMesasge = "Erro ao recuperar o workflow da issue: " + issueKey + "erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}

		return workflow;
	}

	@Cacheable(cacheNames = "issue-transition-properties")
	public List<JiraProperty> getIssueTransitionProperties(String issueKey, String transitionId){
		List<JiraProperty> properties = null;
		try {
			properties = jiraClient.getIssueTransitionProperties(issueKey, transitionId);
		}catch (Exception e) {
			String errorMesasge = "Erro ao recuperar as propriedades da transição: " + transitionId + " da issue: " + issueKey + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
		return properties;
	}

	public JiraProperty getIssueTransitionProperty(String issueKey, String transitionId, String propertyKey) {
		JiraProperty property = null;
		List<JiraProperty> issueProperties = getIssueTransitionProperties(issueKey, transitionId);
		if(issueProperties != null && !issueProperties.isEmpty()) {
			for (JiraProperty issueProperty : issueProperties) {
				if(issueProperty.getKey().equalsIgnoreCase(propertyKey)) {
					property = issueProperty;
					break;
				}
			}
		}
		return property;
	}

	@Cacheable(cacheNames = "issue-status-properties")
	public List<JiraProperty> getIssueStatusProperties(String issueKey, String statusId){
		List<JiraProperty> properties = null;
		try {
			List<JiraProperty> probableProperties = jiraClient.getIssueStatusProperties(issueKey);
			if(StringUtils.isNotBlank(statusId) && probableProperties != null && !probableProperties.isEmpty()) {
				for (JiraProperty property : probableProperties) {
					if(STEP_PROPERTY_STATUS_ID.equals(property.getKey())) {
						properties = probableProperties;
						break;
					}
				}
			}
		}catch (Exception e) {
			String errorMesasge = "Erro ao recuperar as propriedades do status atual da issue: " + issueKey + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
		return properties;
	}

	public JiraProperty getIssueStatusProperty(String issueKey, String statusId, String propertyKey) {
		JiraProperty property = null;
		List<JiraProperty> issueProperties = getIssueStatusProperties(issueKey, statusId);
		if(issueProperties != null && !issueProperties.isEmpty()) {
			for (JiraProperty issueProperty : issueProperties) {
				if(issueProperty.getKey().equalsIgnoreCase(propertyKey)) {
					property = issueProperty;
					break;
				}
			}
		}
		return property;
	}

	/**
	 * Busca 
	 * @param issue
	 * @param transitionIdOrNameOrPropertyKey
	 * @return
	 */
	@Cacheable(cacheNames = "issue-transition")
	public JiraIssueTransition findTransitionByIdOrNameOrPropertyKey(JiraIssue issue, String transitionIdOrNameOrPropertyKey) {
		JiraIssueTransition founded = findTransitionById(issue, transitionIdOrNameOrPropertyKey);
		if(founded == null) {
			founded = findTransitionByName(issue, transitionIdOrNameOrPropertyKey);
		}
		if(founded == null) {
			founded = findTransitionByPropertyKey(issue, transitionIdOrNameOrPropertyKey);
		}

		return founded;
	}

	public JiraIssueTransition findTransitionByPropertyKey(JiraIssue issue, String transitionPropertyKey) {
		JiraIssueTransition founded = null;
		JiraIssueTransitions transitions = this.recuperarTransicoesIssue(issue);
		for (JiraIssueTransition transition : transitions.getTransitions()) {
			if(issue != null && getIssueTransitionProperty(issue.getKey(), transition.getId(), transitionPropertyKey) != null) {
				founded = transition;
				break;
			}
		}

		return founded;
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

	public String getJqlIssuesPendentesGeral() {
		String jql = null;
		if(StringUtils.isNotBlank(filterIssuesPendentesGeralId)) {
			JiraFilter filterIssuesPendentesGeral = getFilter(filterIssuesPendentesGeralId);
			if(filterIssuesPendentesGeral != null && StringUtils.isNotBlank(filterIssuesPendentesGeral.getJql())) {
				jql = filterIssuesPendentesGeral.getJql();
			}
		}
		return jql;
	}
	
	public String getJqlIssuesPendentesGeralPorAreaConhecimento(String areaConhecimento) {
		StringBuilder jql = new StringBuilder();
		String jqlBase = getJqlIssuesPendentesGeral();
		if(StringUtils.isNotBlank(areaConhecimento) && StringUtils.isNotBlank(jqlBase)) {
			jql.append(JiraUtils.getFieldNameToJQL(FIELD_AREAS_CONHECIMENTO)).append(" in (\"")
				.append(areaConhecimento).append("\") AND ").append(jqlBase);
		}
		
		return jql.toString();
	}
	
	public String getJqlIssuesPendentesTribunalRequisitante(String tribunal) {
		String jql = jiraUrl + "/issues/?jql=cf%5B11700%5D%20in%20("+tribunal+")%20AND%20status%20not%20in%20(Fechado%2C%20Resolvido)";
		return jql;
	}

	public JiraIssue createIssue(JiraIssueCreateAndUpdate newIssue) throws Exception {
		JiraIssue novaIssue = null;
		try {
			novaIssue = jiraClient.createIssue(newIssue);
		}catch (Exception e) {
			String errorMesasge = "Falhou ao tentar cadastrar uma nova issue, erro: " + e.getLocalizedMessage() 
			+ "\nValores utilizados" + Utils.convertObjectToJson(newIssue);
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);

			throw new Exception(errorMesasge);
		}
		return novaIssue;
	}

	public void updateIssue(JiraIssue issue, JiraIssueCreateAndUpdate issueUpdate) throws Exception {
		String issueKey = issue.getKey();
		try {
			if(issueUpdate != null) {
				if(issueUpdate.getTransition() !=null && !issueUpdate.getTransition().isEmpty()) {
					jiraClient.changeIssueWithTransition(issueKey, issueUpdate);
				}else {
					jiraClient.updateIssue(issueKey, issueUpdate);
				}
			}
		}catch (Exception e) {
			String errorMesasge = "Falhou ao tentar atualizar a issue: " + issueKey + ", erro: " + e.getLocalizedMessage() 
			+ "\nValores utilizados" + Utils.convertObjectToJson(issueUpdate);
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);

			throw new Exception(errorMesasge);
		}
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
		if(StringUtils.isNotBlank(text)) {
			try {
				jiraClient.sendComment(issue.getKey(), comment);
			}catch (Exception e) {
				String errorMesasge = "Erro ao tentar enviar o comentário: [" + text + "] - erro: " + e.getLocalizedMessage();
				logger.error(errorMesasge);
				slackService.sendBotMessage(errorMesasge);
				rocketchatService.sendBotMessage(errorMesasge);
				telegramService.sendBotMessage(errorMesasge);
			}
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
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
	}

	public byte[] getAttachmentContent(JiraIssue issue, String filename) throws IOException {
		byte[] attachmentContent = null;
		JiraIssue issueDetalhada = recuperaIssueDetalhada(issue);
		if(issueDetalhada.getFields().getAttachment() != null && !issueDetalhada.getFields().getAttachment().isEmpty()) {
			for (JiraIssueAttachment attachment : issueDetalhada.getFields().getAttachment()) {
				if(attachment.getFilename().equals(filename)) {
					String mimeType = attachment.getMimeType();
					if(StringUtils.isNotBlank(mimeType) && mimeType.contains("text")) {
						attachmentContent = jiraClient.getAttachmentContent(attachment.getId().toString(), attachment.getFilename());
					}else {
						attachmentContent = jiraClient.getAttachmentContentBinary(attachment.getId().toString(), attachment.getFilename());
					}

					break;
				}
			}
		}

		return attachmentContent;
	}

	@Cacheable(cacheNames = "issue-transitions")
	public JiraIssueTransitions recuperarTransicoesIssue(JiraIssue issue) {
		String issueKey = issue.getKey();
		JiraIssueTransitions transitions = null;
		try {
			transitions = jiraClient.getIssueTransitions(issueKey);
		}catch (Exception e) {
			String errorMesasge = "Erro ao recuperar as transições da issue: " + issueKey + "erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
		return transitions;
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
			if(gitlabProjectId == null && issue.getFields().getProject() != null) {
				gitlabProjectId = getGitlabProjectFromJiraProjectKey(issue.getFields().getProject().getKey());
			}
			if(gitlabProjectId == null && issue.getFields().getServico() != null && issue.getFields().getServico().getId() != null) {
				gitlabProjectId = mapeamentoServicoJiraProjetoGitlab(issue.getFields().getServico().getId().toString());
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

	public String getProjectDocumentationUrl(String projectKey) {
		return getPropertyFromJiraProject(projectKey, PROJECT_PROPERTY_DOCUMENTATION_RELEASE_NOTES_URL);
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

	public void changePropertyFromJiraProject(String projectKey, String propertyKey, String value) {
		if(StringUtils.isNotBlank(projectKey) && StringUtils.isNotBlank(propertyKey)) {
			try {
				jiraClient.changeProjectProperty(projectKey, propertyKey, value);
			}catch (Exception e) {
				logger.info("Erro ao atualizar propriedade: " + propertyKey +" do projeto: " + projectKey);
				logger.info(e.getLocalizedMessage());
			}
		}
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

	@Cacheable(cacheNames = "issue-details")
	public JiraIssue recuperaIssue(String issueKey, Map<String, String> options) {
		JiraIssue issueDetails = null;
		try {
			issueDetails = jiraClient.getIssueDetails(issueKey, options);
		}catch (Exception e) {
			String errorMesasge = "Erro ao recuperar os detalhes da issue: " + issueKey + "erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
		return issueDetails;
	}

	@Cacheable(cacheNames = "user-from-name")
	public JiraUser findUserByUserName(String userName) {
		JiraUser user = null;
		Map<String, String> options = new HashMap<>();
		options.put("username", userName);
		try {
			List<JiraUser> usuarios = jiraClient.findUser(options);
			user = (usuarios != null && !usuarios.isEmpty()) ? usuarios.get(0) : null;
		}catch (Exception e) {
		}
		return user;
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

	public List<JiraIssueFieldOption> getTribunaisRevisores(JiraIssue issue) {
		List<JiraIssueFieldOption> tribunaisRevisores = null;
		try {
			tribunaisRevisores = issue.getFields().getTribunaisResponsaveisRevisao();
		} catch (Exception e) {
			// ignora
		}
		return tribunaisRevisores;
	}

	public JiraUser getJiraUserFromGitlabUser(GitlabUser gitlabUser) {
		JiraUser jiraUser = null;
		if(gitlabUser != null) {
			if(StringUtils.isNotBlank(gitlabUser.getEmail())) {
				jiraUser = findUserByUserName(gitlabUser.getEmail());
			}
			if(jiraUser == null && gitlabUser.getIdentities() != null && !gitlabUser.getIdentities().isEmpty()) {
				for (GitlabUserIdentity identity : gitlabUser.getIdentities()) {
					if(OAUTH_IDENTITY_PROVIDER_PJE_CLOUD.equalsIgnoreCase(identity.getProvider())) {
						jiraUser = findUserByUserName(identity.getExternUid());
						break;
					}
				}
			}
		}
		return jiraUser;
	}

	public JiraUser getUserWithGroups(String username) {
		Map<String, String> options = new HashMap<>();
		options.put("expand", "groups");
		return getUser(username, options);
	}
	
	@Cacheable(cacheNames = "jira-user-details")
	public JiraUser getUser(String username, Map<String, String> options) {
		JiraUser user = null;
		try {
			if(options == null) {
				options = new HashMap<>();
			}
			options.put("username", username);
			user = jiraClient.getUserDetails(options);
		}catch (Exception e) {
			String errorMesasge = "Erro ao tentar recuperar o usuário: |" + username + "| - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}

		return user;
	}

	@Cacheable(cacheNames = "jira-group-details")
	public JiraGroup getGroup(String groupName, Map<String, String> options) {
		JiraGroup group = null;
		if(StringUtils.isNotBlank(groupName)) {
			try {
				if(options == null) {
					options = new HashMap<>();
				}
				options.put("groupname", groupName);
				group = jiraClient.getGroupDetails(options);
			}catch (Exception e) {
				String errorMesasge = "Erro ao tentar recuperar o grupo: |" + groupName + "| - erro: " + e.getLocalizedMessage();
				logger.error(errorMesasge);
				slackService.sendBotMessage(errorMesasge);
				rocketchatService.sendBotMessage(errorMesasge);
				telegramService.sendBotMessage(errorMesasge);
			}
		}

		return group;
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
		List<String> relatedProjectKeys = getJiraRelatedProjects(projectKey);
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

	public void releaseVersionInRelatedProjects(String projectKey, String actualVersion, String newVersion, String releaseDate, String versionDescription) {
		List<String> relatedProjectKeys = getJiraRelatedProjects(projectKey);
		if(relatedProjectKeys != null) {
			for (String relatedProjectKey : relatedProjectKeys) {
				// busca a versao, se ela não existir cria
				JiraVersion jiraVersion = createProjectVersionIfNotExists(relatedProjectKey, newVersion, versionDescription, null);
				if(jiraVersion == null) {
					logger.error("Falhou ao tentar encontrar a versão: " + newVersion + " no projeto do jira: " + relatedProjectKey);
				}
				// atualiza dados de release da versao
				try {
					jiraVersion.setDescription(versionDescription);
					jiraVersion.setName(newVersion);
					if(StringUtils.isNotBlank(releaseDate)) {
						Date releaseDateTime;
						releaseDateTime = Utils.getDateFromString(releaseDate);
						jiraVersion.setReleaseDate(Utils.dateToStringPattern(releaseDateTime, Utils.DATE_PATTERN_SIMPLE));
						jiraVersion.setReleased(true);
					}else {
						jiraVersion.setReleased(false);
					}
					updateProjectVersion(jiraVersion);

				} catch (Exception e) {
					String errorMesasge = "Erro ao tentar atualizar versao: |" + newVersion + "| - erro: " + e.getLocalizedMessage();
					logger.error(errorMesasge);
					slackService.sendBotMessage(errorMesasge);
					rocketchatService.sendBotMessage(errorMesasge);
					telegramService.sendBotMessage(errorMesasge);
				}
			}
		}
	}

	public JiraVersion updateProjectVersion(JiraVersion version) throws Exception {
		JiraVersion versionUpdated = null;
		try {
			String versionId = version.getId().toString();
			if(StringUtils.isNotBlank(version.getStartDate())) {
				version.setUserStartDate(null); // apenas uma das opcoes: userStartDate ou startDate pode ser enviada, no caso a preferencia é para a startDate
			}
			if(StringUtils.isNotBlank(version.getReleaseDate())) {
				version.setUserReleaseDate(null); // apenas uma das opcoes: userReleaseDate ou releaseDate pode ser enviada, no caso a preferencia é para a releaseDate
			}
			versionUpdated = jiraClient.updateVersion(versionId, version);
		}catch (Exception e) {
			throw new Exception(e.getLocalizedMessage());
		}

		return versionUpdated;
	}

	public List<String> getJiraRelatedProjects(String projectKey){
		List<String> projetosRelacionados = new ArrayList<>();
		projetosRelacionados.add(projectKey);
		if(projectKey != null) {
			String projectKeyValue = getPropertyFromJiraProject(projectKey, PROJECT_PROPERTY_JIRA_RELATED_PROJECTS_KEYS);
			if(StringUtils.isNotBlank(projectKeyValue)) {
				String[] projectKeys = projectKeyValue.split(",");
				if(projectKeys != null && projectKeys.length > 0) {
					for (String pKey : projectKeys) {
						if(!projetosRelacionados.contains(pKey)) {
							projetosRelacionados.add(pKey);
						}
					}
				}
			}
		}
		return projetosRelacionados;
	}

	/* obtem o objeto customFieldOption correspondente ao projeto na estrutura de documentacoes dado nas propriedades do projeto atual */
	/**
	 * Recupera os ids da estrutura de documentacao do projeto dado, o valor terá o formato:
	 * <id-opcao-pai>:<id-opcao-filha>
	 * 
	 * @param projectKey
	 * @return
	 */
	public String getEstruturaDocumentacao(String projectKey) {
		String identificacaoEstruturaDocumentacao = null;
		if(projectKey != null) {
			boolean encontrouIdentificacao = false;
			identificacaoEstruturaDocumentacao = getPropertyFromJiraProject(projectKey, PROJECT_PROPERTY_DOCUMENTATION_FIELDVALUE_DOC_STRUCTURE);
			if(StringUtils.isNotBlank(identificacaoEstruturaDocumentacao)) {
				encontrouIdentificacao = true;
			}
			if(!encontrouIdentificacao) {
				// se não encontrou o valor na propriedade - recupera com base no nome do projeto
				JiraProject project = jiraClient.getProjectDetails(projectKey, new HashMap<String, String>());
				if(project != null) {
					identificacaoEstruturaDocumentacao = getEstruturaDocumentacaoOption(project.getName());
					if(identificacaoEstruturaDocumentacao != null) {
						// grava o valor na propriedade - para evitar essa outra consulta onerosa
						changePropertyFromJiraProject(projectKey, PROJECT_PROPERTY_DOCUMENTATION_FIELDVALUE_DOC_STRUCTURE, identificacaoEstruturaDocumentacao);
					}
				}
			}
		}

		return identificacaoEstruturaDocumentacao;
	}

	public String getEstruturaDocumentacaoOption(String searchItems) {
		List<String> identificacaoEstruturaDocumentacao = new ArrayList<>();
		JiraCustomField customField = getCustomFieldDetailed(FIELD_ESTRUTURA_DOCUMENTACAO, searchItems, Boolean.TRUE);
		if(customField != null && customField.getOptions() != null && !customField.getOptions().isEmpty()) {
			for (JiraCustomFieldOption customOption : customField.getOptions()) {
				identificacaoEstruturaDocumentacao = new ArrayList<>();
				identificacaoEstruturaDocumentacao.add(customOption.getId().toString());
				if(customOption.getCascadingOptions() != null && !customOption.getCascadingOptions().isEmpty()) {
					identificacaoEstruturaDocumentacao.add(customOption.getCascadingOptions().get(0).getId().toString());
					break;
				}
			}
		}
		return String.join(":", identificacaoEstruturaDocumentacao);
	}
	
	public JiraCustomField getCtrlPontuacaoAreasConhecimento() {
		return getCustomFieldDetailed(FIELD_CTRL_PONTUACAO_AREA_CONHECIMENTO, null, Boolean.TRUE);
	}

	public JiraCustomField getAreasConhecimento() {
		return getCustomFieldDetailed(FIELD_AREAS_CONHECIMENTO, null, Boolean.TRUE);
	}

	/**
	 * Recupera a data da última atualizacao dos valores do campo
	 * - a data fica na opção "filha" da opção de controle: "atualizacao"
	 * @return
	 */
	public String getDataAtualizacaoCtrlPontuacaoAreasConhecimento() {
		String dataAtualizacao = null;
		JiraCustomField ctrlPontuacaoAreasConhecimento = getCtrlPontuacaoAreasConhecimento();
		if(ctrlPontuacaoAreasConhecimento != null && ctrlPontuacaoAreasConhecimento.getOptions() != null && !ctrlPontuacaoAreasConhecimento.getOptions().isEmpty()) {
			for (JiraCustomFieldOption customOption : ctrlPontuacaoAreasConhecimento.getOptions()) {
				if(CTRL_PONTUACAO_AREA_CONHECIMENTO_DATA_ATUALIZACAO_OPTION.equalsIgnoreCase(customOption.getValue())) {
					if(customOption.getCascadingOptions() != null && !customOption.getCascadingOptions().isEmpty()) {
						dataAtualizacao = customOption.getCascadingOptions().get(0).getValue();
						break;
					}
					break;
				}

			}
		}
		return dataAtualizacao;
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
				jiraVersion.setStartDate(Utils.dateToStringPattern(new Date(), Utils.DATE_PATTERN_SIMPLE)); // assumi que iniciará a versão na criacao do registro
				if(StringUtils.isNotBlank(releaseDate)) {
					Date releaseDateTime = Utils.getDateFromString(releaseDate);
					jiraVersion.setReleaseDate(Utils.dateToStringPattern(releaseDateTime, Utils.DATE_PATTERN_SIMPLE));
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

	public void createSprintDoGrupoOption(String optionName) {
		JiraCustomField sprintDoGrupo = getCustomFieldDetailed(FIELD_SPRINT_DO_GRUPO, optionName, Boolean.TRUE);
		createCustomFieldOption(optionName, null, sprintDoGrupo);
	}

	public void enableSprintDoGrupoOption(String optionName) {
		JiraCustomField sprintDoGrupo = getCustomFieldDetailed(FIELD_SPRINT_DO_GRUPO, optionName, Boolean.FALSE);
		setStatusCustomFieldOption(optionName, null, sprintDoGrupo, true);
	}

	public void disableSprintDoGrupoOption(String optionName) {
		JiraCustomField sprintDoGrupo = getCustomFieldDetailed(FIELD_SPRINT_DO_GRUPO, optionName, Boolean.TRUE);
		setStatusCustomFieldOption(optionName, null, sprintDoGrupo, false);
	}

	public void createSubCategoriaEstruturaDocumentacao(String categoriaId, String nomeSubCategoria) {
		JiraCustomField subCategorias = getCustomFieldDetailed(FIELD_ESTRUTURA_DOCUMENTACAO, nomeSubCategoria, Boolean.TRUE);
		createCustomFieldOption(nomeSubCategoria, categoriaId, subCategorias); // TODO - alterar esta função para permitir consulta com cascading de opcoes
	}

	public void createCategoriaEstruturaDocumentacao(String nomeCategoria) {
		JiraCustomField categorias = getCustomFieldDetailed(FIELD_ESTRUTURA_DOCUMENTACAO, nomeCategoria, Boolean.TRUE);
		createCustomFieldOption(nomeCategoria, null, categorias);
	}

	/**
	 * Caso tenha sido passado o parentOptionId, busca na lista de opcoes filhas desse opcao pai, caso contrário, busca la lista de pais
	 * se não encontrar a opção, cadastra, se a nova opcao for uma subopcao, solicita update da opcao pai, caso contrário cadastra nova opcao pai
	 * 
	 * @param optionName
	 * @param parentOptionId
	 * @param customField
	 * @return
	 */
	public void createCustomFieldOption(String optionName, String parentOptionId, JiraCustomField customField) {
		JiraCustomFieldOption parentOption = null;
		if(customField != null) {
			boolean optionExists = false;
			if(!customField.getOptions().isEmpty()) {
				for (JiraCustomFieldOption option : customField.getOptions()) {
					if(StringUtils.isNotBlank(parentOptionId)) {
						if(parentOptionId.equals(option.getId().toString()) && option.getCascadingOptions() != null) {
							for (JiraCustomFieldOption childOption : option.getCascadingOptions()) {
								if(Utils.compareAsciiIgnoreCase(childOption.getValue(), optionName)) {
									optionExists = true;
									break;
								}
							}
							if(optionExists) {
								break;
							}
							parentOption = option;
						}
					}else {
						if(Utils.compareAsciiIgnoreCase(option.getValue(), optionName)) {
							optionExists = true;
							break;
						}
					}
				}
			}
			if(!optionExists) {
				JiraCustomFieldOption newOption = new JiraCustomFieldOption();
				newOption.setValue(optionName);
				if(parentOption != null) {
					List<JiraCustomFieldOption> childrenOptions = parentOption.getCascadingOptions();
					if(childrenOptions == null) {
						childrenOptions = new ArrayList<>();
					}
					childrenOptions.add(newOption);
					parentOption.setCascadingOptions(childrenOptions);

					updateCustomFieldOption(customField.getId().toString(), parentOption.getId().toString(), parentOption);

				}else {
					JiraCustomFieldOptionsRequest optionsRequest = new JiraCustomFieldOptionsRequest(newOption);
					jiraClient.addCustomFieldOptions(customField.getId().toString(), optionsRequest);
				}
			}
		}
	}

	public JiraCustomFieldOption getCustomFieldOption(String optionNameOrId, String parentOptionId, JiraCustomField customField) {
		JiraCustomFieldOption option = null;
		String optionName = null;
		BigDecimal optionId = null;
		if(StringUtils.isNotBlank(optionNameOrId)) {
			if(StringUtils.isNumeric(optionNameOrId)) {
				optionId = new BigDecimal(optionNameOrId);
			}else {
				optionName = optionNameOrId;
			}
		}

		if(customField != null) {
			if(!customField.getOptions().isEmpty()) {
				for (JiraCustomFieldOption opt : customField.getOptions()) {
					if(StringUtils.isNotBlank(parentOptionId)) {
						if(parentOptionId.equals(opt.getId().toString()) && opt.getCascadingOptions() != null) {
							for (JiraCustomFieldOption childOption : opt.getCascadingOptions()) {
								if((StringUtils.isNotBlank(optionName) && Utils.compareAsciiIgnoreCase(childOption.getValue(), optionName))
									|| (optionId != null && childOption.getId().equals(optionId))) {
									option = childOption;
									break;
								}
							}
							if(option != null) {
								break;
							}
						}
					}else {
						if((StringUtils.isNotBlank(optionName) && Utils.compareAsciiIgnoreCase(opt.getValue(), optionName))
								|| (optionId != null && opt.getId().equals(optionId))) {
							option = opt;
							break;
						}
					}
				}
			}
		}
		return option;
	}

	public void setStatusCustomFieldOption(String optionName, String parentOptionId, JiraCustomField customField, boolean status) {
		JiraCustomFieldOption option = getCustomFieldOption(optionName, parentOptionId, customField);
		if(option != null && customField != null) {
			String customFieldId = customField.getId().toString();
			String optionId = option.getId().toString();
			option.setEnabled(status);
			updateCustomFieldOption(customFieldId, optionId, option);
		}
	}
	
	public void updateCustomFieldOption(String customFieldId, String optionId, JiraCustomFieldOption optionRequest) {
		JiraCustomFieldOptionRequest customOptionRequest = new JiraCustomFieldOptionRequest();
		customOptionRequest.setEnabled(optionRequest.getEnabled());
		customOptionRequest.setId(optionRequest.getId());
		customOptionRequest.setSequence(optionRequest.getSequence());
		customOptionRequest.setValue(optionRequest.getValue());
		List<String> cascadingOptionsStr = new ArrayList<>();
		List<JiraCustomFieldOption> cascadingOptions = optionRequest.getCascadingOptions();
		if(cascadingOptions != null) {
			for (JiraCustomFieldOption childOption : cascadingOptions) {
				cascadingOptionsStr.add(childOption.getValue());
			}
		}
		customOptionRequest.setCascadingOptions(cascadingOptionsStr);
		updateCustomFieldOption(customFieldId, optionId, customOptionRequest);
	}

	public void updateCustomFieldOption(String customFieldId, String optionId, JiraCustomFieldOptionRequest optionRequest) {
		try {
			optionRequest.setSelf(null);
			jiraClient.updateCustomFieldOption(customFieldId, optionId, optionRequest);			
		}catch (Exception e) {
			String errorMesasge = "Erro ao atualizar a opção " + optionId +" do campo customizado: "+ customFieldId 
					+ " com as opções: " + optionRequest + " - erro: " + e.getLocalizedMessage();
			logger.error(errorMesasge);
			slackService.sendBotMessage(errorMesasge);
			rocketchatService.sendBotMessage(errorMesasge);
			telegramService.sendBotMessage(errorMesasge);
		}
	}

	public String calulateNextVersionNumber(String projectKey, String versaoAtual) {
		String proximaVersao = null;
		int index = 2;
		int numDigits = 3;
		if(projectKey != null && (projectKey.equalsIgnoreCase("PJEII") || projectKey.equalsIgnoreCase("PJEVII") || projectKey.equalsIgnoreCase("PJELEG"))) {
			numDigits = 4;
			if(versaoAtual.startsWith("1.7.") || versaoAtual.startsWith("2.0.") || versaoAtual.startsWith("2.1.0.") || versaoAtual.startsWith("2.1.1.") || versaoAtual.startsWith("2.1.2.")) {
				index = 3;
			}
		}
		proximaVersao = Utils.calculateNextOrdinaryVersion(versaoAtual, index, numDigits);
		return proximaVersao;
	}	
}