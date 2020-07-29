package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.VersionReleaseNotes;
import com.devplatform.model.bot.VersionReleaseNotesIssueTypeEnum;
import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.event.GitlabEventPush;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraProject;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.ReleaseNotesTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.AsciiDocMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class LanVersion07TagPushedEventHandler extends Handler<GitlabEventPush>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion07TagPushedEventHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||06||PUBLISH-DOCS|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	@Autowired
	private ReleaseNotesTextModel releaseNotesModel;

	private static final String TRANSITION_ID_IMPEDIMENTO = "191"; // TODO buscar por propriedade da transicao
	private static final String TRANSITION_ID_FINALIZAR_PROCESSAMENTO_PUBLICACAO_RELEASE_NOTES = "201"; // TODO buscar por propriedade da transicao

	/**
	 * :: TAG pushed ::
	 *    Verifica se não há issue relacionada
	 *    Se não houver, cria uma com a versão lançada, já iniciando o release notes
	 *    Publica a criação da issue para o autor, via email e via rocketchat
	 *    Publica também a criação da issue no grupo de XXXX do rocketchat
	 */
	public void handle(GitlabEventPush gitEventPushTag) throws Exception {
		messages.clean();
		JiraIssue issue = null;
		boolean issueFounded = false;
		GitlabCommit lastCommit = null;
		if (gitEventPushTag != null) {
			if (gitEventPushTag.getCommits() != null && !gitEventPushTag.getCommits().isEmpty()) {
				lastCommit = gitEventPushTag.getCommits().get(0);
			}
			if (StringUtils.isNotBlank(lastCommit.getMessage())) {
				messages.info("Identificando issue a partir da mensagem do último commit: " + lastCommit.getMessage());
				String issueKey = Utils.getIssueKeyFromCommitMessage(lastCommit.getMessage());
				if (StringUtils.isNotBlank(issueKey)) {

					messages.setId(gitEventPushTag.getRef());
					messages.debug(gitEventPushTag.getEventName().toString());

					messages.info("Issue key identificada: " + issueKey);
					issue = jiraService.recuperaIssueDetalhada(issueKey);
					if (issue != null) {
						issueFounded = true;
					} else {
						messages.error("Issue key não encontrada no jira: " + issueKey);
					}
				} else {
					messages.error("Não foi possível idetificar o número da issue");
				}
			}
		}
		if (issueFounded) {
			// 1. Recupera o anexo com o json do releaseNotes
			String releaseNotesAttachment = jiraService.getAttachmentContent(issue, JiraService.RELEASE_NOTES_JSON_FILENAME);
			VersionReleaseNotes releaseNotes = null;
			if (releaseNotesAttachment != null) {
				releaseNotes = Utils.convertJsonToJiraReleaseNotes(releaseNotesAttachment);

				if (releaseNotes != null) {
					// ajusta a data de geracao da versao e o autor dessa versao
					if(lastCommit != null && StringUtils.isNotBlank(lastCommit.getTimestamp())) {
						releaseNotes.setReleaseDate(lastCommit.getTimestamp());
						
						String commiterEmail = null;
						if(lastCommit.getAuthor() != null) {
							commiterEmail = lastCommit.getAuthor().getEmail();
						}
						if(commiterEmail != null && (
									releaseNotes.getAuthor() == null 
									|| StringUtils.isBlank(releaseNotes.getAuthor().getEmailAddress())
									|| (!releaseNotes.getAuthor().getEmailAddress().equalsIgnoreCase(commiterEmail))
								)) {
							releaseNotes.setAuthor(jiraService.getUserFromUserName(commiterEmail));
						}
						
						if(gitEventPushTag.getProject() != null && StringUtils.isNotBlank(gitEventPushTag.getProject().getName())) {
							releaseNotes.setProject(gitEventPushTag.getProject().getName());
						}
					}
					handleReleaseNotesCreation(issue, releaseNotes);
				}
			}
			if(releaseNotes == null) {
				messages.error("Release notes não encontrado na issue de referência: " + issue.getKey());
			}
		}
		
		if(messages.hasSomeError()) {
			// tramita para o impedmento, enviando as mensagens nos comentários
			Map<String, Object> updateFields = new HashMap<>();
			jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
			enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_IMPEDIMENTO);
		}else {
			// tramita automaticamente, enviando as mensagens nos comentários
			Map<String, Object> updateFields = new HashMap<>();
			// atualiza a data de lancamento do release notes
			String dataTagStr = Utils.dateToStringPattern(new Date(), JiraService.JIRA_DATETIME_PATTERN);
			jiraService.atualizarDataReleaseNotes(issue, dataTagStr, updateFields);

			jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
			enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_FINALIZAR_PROCESSAMENTO_PUBLICACAO_RELEASE_NOTES);
		}
	}
	
	public void handleReleaseNotesCreation(JiraIssue issue, VersionReleaseNotes releaseNotes) throws ParseException {
		// 2. Gerar documento asciidoc e incluir no docs.pje.jus.br
		criarDocumentoReleaseNotesNoProjetoDocumentacao(issue, releaseNotes);
//TODO
		// 3. Gerar release do gitlab para adicionar a tag correspondente
		// 5. Fazer alterações relacionadas ao jira
		
		// Atualiza o json da issue e tramita a issue para um status de encerrada
		
	}

	public static final String PATHNAME_DOCUMENTACAO_RELEASE_NOTES = "src/main/asciidoc/projetos/";
	public static final String RELEASE_NOTES_PREFIX = "release-notes_";
	public static final String RELEASE_NOTES_SUFFIX = ".adoc";
	public static final String RELEASE_NOTES_HTML_SUFFIX = ".html";
	public static final String RELEASE_NOTES_INCLUDES_DIR = "/release-notes/includes/";
	public static final String RELEASE_NOTES_RELEASE_COMPLETO = "/release-notes/release-notes-completo.adoc";
	public static final String RELEASE_NOTES_RELEASE_LISTA = "/release-notes/index.adoc";

	private void criarDocumentoReleaseNotesNoProjetoDocumentacao(JiraIssue issue, VersionReleaseNotes releaseNotes)
			throws ParseException {
		releaseNotesModel.setReleaseNotes(releaseNotes);
		String texto = releaseNotesModel.convert(new AsciiDocMarkdown());
		
		String filePath = getPathNameReleaseNotes(issue.getFields().getProject(), releaseNotes.getVersion());
		GitlabBranchResponse branch = gitlabService.createBranchProjetoDocumentacao(GitlabService.PROJECT_DOCUMENTACO,
				issue.getKey());
		// criar um arquivo no projeto docs.pje.jus.br com o conteúdo deste arquivo
		GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(GitlabService.PROJECT_DOCUMENTACO,
				branch, filePath, texto, "Gerando release notes da versão " + releaseNotes.getVersion());

		messages.info(commitResponse.toString());

		// criar include do documento gerado no arquivo: release-notes-completo.adoc
		adicionaNovoArquivoReleaseNotesNoReleaseCompleto(issue.getFields().getProject(), branch, filePath);
		// criar include do documento gerado no arquivo: index.adoc
		adicionaNovoArquivoReleaseNotesNaListaDeReleases(issue.getFields().getProject(), branch, filePath,
				releaseNotes.getVersion(), releaseNotes.getReleaseDate());
	}

	/**
	 * Arquivo target: release-notes-completo.adoc Item a incluir:
	 * include::{docdir}/{docsServicePATH}/release-notes/includes/PARAM_RELEASE_NOTES_FILE.adoc[]
	 * Exemplo de inclusao:
	 * include::{docdir}/{docsServicePATH}/release-notes/includes/release-notes_2-1-8-0.adoc[]
	 *
	 * @param project
	 * @param branch
	 * @param filePath
	 */
	private void adicionaNovoArquivoReleaseNotesNoReleaseCompleto(JiraProject project, GitlabBranchResponse branch,
			String filePath) {

		String branchName = branch.getBranchName();

		// 1. identificar qual é a versão que será lançada - ver nome do arquivo
		String fileName = Utils.getFileNameFromFilePath(filePath);
		// 2. obter o arquivo completo
		String projectNameInDocumentacao = getProjectNameInDocumentacaoFromJiraProject(project);
		String releaseNotesCompletoFileName = PATHNAME_DOCUMENTACAO_RELEASE_NOTES + projectNameInDocumentacao
				+ RELEASE_NOTES_RELEASE_COMPLETO;
		String releaseNotesCompletoContent = gitlabService.getRawFile(GitlabService.PROJECT_DOCUMENTACO,
				releaseNotesCompletoFileName, branchName);
		if (StringUtils.isNotBlank(releaseNotesCompletoContent)) {
			// 3. verificar se a versão já está lá
			if (releaseNotesCompletoContent.contains(fileName)) {
				messages.info("O include do release notes [" + fileName + "] já existe no arquivo de releases completas");
			} else {
				messages.info("O include do release notes [" + fileName
						+ "] AINDA NÃO existe no arquivo de releases completas");
				// 5. adicionar o include do release
				String linhaIncludeReleaseNotes = "include::{docdir}/{docsServicePATH}/release-notes/includes/"
						+ fileName + "[]";
				releaseNotesCompletoContent += "\n" + linhaIncludeReleaseNotes + "\n";
			}
			// 4. identificar a ordem de armazenamento desses includes
			releaseNotesCompletoContent = reordenarListaIncludesReleaseNotesCompleto(releaseNotesCompletoContent);

			GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(
					GitlabService.PROJECT_DOCUMENTACO, branch, releaseNotesCompletoFileName,
					releaseNotesCompletoContent,
					"Incluindo novo release notes no arquivo de releases completas do projeto");
			messages.info(commitResponse.toString());
		} else {
			// ERRO no projeto de documentação - o arquivo não existe no destino
			messages.error(" Não foi possível encontrar o arquivo "
					+ releaseNotesCompletoFileName + " no projeto de documentação");
		}
	}

	/**
	 * Arquivo target: index.adoc Item a incluir: ===
	 * link:includes/PARAM_RELEASE_NOTES_FILE.html[vPARAM_NUMERO_VERSAO -
	 * PARAM_DATA_VERSAO] Exemplo de inclusao: ===
	 * link:includes/release-notes_2-1-2-3.html[v2.1.2.3 - 20/12/2019]
	 *
	 * @param project
	 * @param branch
	 * @param filePath
	 * @param version
	 * @param releaseDate
	 * @throws ParseException
	 */
	private void adicionaNovoArquivoReleaseNotesNaListaDeReleases(JiraProject project, GitlabBranchResponse branch,
			String filePath, String version, String releaseDate) throws ParseException {

		String branchName = branch.getBranchName();

		// 1. identificar qual é a versão que será lançada - ver nome do arquivo
		String fileName = Utils.getFileNameFromFilePath(filePath);
		String fileNameHTML = fileName.replace(RELEASE_NOTES_SUFFIX, RELEASE_NOTES_HTML_SUFFIX);
		// 2. obter o arquivo completo
		String projectNameInDocumentacao = getProjectNameInDocumentacaoFromJiraProject(project);
		String listaReleaseNotesFileName = PATHNAME_DOCUMENTACAO_RELEASE_NOTES + projectNameInDocumentacao
				+ RELEASE_NOTES_RELEASE_LISTA;
		String listaReleaseNotesContent = gitlabService.getRawFile(GitlabService.PROJECT_DOCUMENTACO,
				listaReleaseNotesFileName, branchName);
		if (StringUtils.isNotBlank(listaReleaseNotesContent)) {
			// 3. verificar se a versão já está lá
			if (listaReleaseNotesContent.contains(fileNameHTML)) {
				messages.info("O include do release notes |" + fileNameHTML 
						+ "| já existe no arquivo de lista de releases");
			} else {
				messages.info("O include do release notes |" + fileNameHTML
						+ "| AINDA NÃO existe no arquivo de lista de releases");
				// 4. TODO - identificar a ordem de armazenamento desses includes
				// 5. adicionar o include do release
				String dataReleaseNotesStr = null;
				if (releaseDate != null) {
					Date dataReleaseNotes = Utils.stringToDate(releaseDate, null);
					dataReleaseNotesStr = Utils.dateToStringPattern(dataReleaseNotes, "dd/MM/yyyy");

					String linhaIncludeReleaseNotes = "=== link:includes/" + fileNameHTML + "[v" + version + " - "
							+ dataReleaseNotesStr + "]";
					listaReleaseNotesContent += "\n" + linhaIncludeReleaseNotes + "\n";
				} else {
					messages.error("Não foi possível identificar a data da release");
				}
			}
			listaReleaseNotesContent = reordenarListaIncludesReleaseNotesHtmls(listaReleaseNotesContent);

			GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(
					GitlabService.PROJECT_DOCUMENTACO, branch, listaReleaseNotesFileName, listaReleaseNotesContent,
					"Incluindo novo release notes no arquivo de lista de releases do projeto");
			messages.info(commitResponse.toString());
		} else {
			// ERRO no projeto de documentação - o arquivo não existe no destino
			messages.error(" Não foi possível encontrar o arquivo " + listaReleaseNotesFileName
					+ " no projeto de documentação");
		}
	}

	private String reordenarListaIncludesReleaseNotesCompleto(String releaseNotesCompletoContent) {
		String[] linhas = releaseNotesCompletoContent.split("\n");
		List<String> linhasArquivoAlterado = new ArrayList<>();
		List<String> releaseNotesIncludes = new ArrayList<>();
		// 1. identifica todos os includes de release notes e mantem no arquivo as
		// informacoes que sejam válidas e não sejam desses includes
		boolean encontrouAlgumIncludeValido = false;
		for (String linha : linhas) {
			if (linha.trim().startsWith("//")) { // comentarios permanessem onde estão
				linhasArquivoAlterado.add(linha);
			} else if (linha.toLowerCase().contains(RELEASE_NOTES_INCLUDES_DIR.toLowerCase())
					&& linha.startsWith("include")) {
				encontrouAlgumIncludeValido = true;
				releaseNotesIncludes.add(linha);
				// linhas que não sejam de include de release continuam onde estão, espaços em
				// branco permanessem onde estão se não estiverem entre os includes de releases
			} else if (StringUtils.isNotBlank(linha) || !encontrouAlgumIncludeValido) {
				linhasArquivoAlterado.add(linha);
			}
		}
		messages.info("Existem " + releaseNotesIncludes.size() + " releases.");
		// 3. ordena os release notes pelo número da versão ASC
		Collections.sort(releaseNotesIncludes, new SortReleaseNotesAdocByVersion());
		// 4. remonta o arquivo:
		for (String releaseInclude : releaseNotesIncludes) {
			// 4.2. Grava cada include e adiciona um \n no início e outro no final
			linhasArquivoAlterado.add("\n" + releaseInclude);
		}
		return String.join("\n", linhasArquivoAlterado);
	}

	class SortReleaseNotesAdocByVersion implements Comparator<String> {
		public int compare(String a, String b) {
			String versionAStr = getVersionFromReleaseNotesInclude(a, RELEASE_NOTES_PREFIX, RELEASE_NOTES_SUFFIX);
			List<Integer> versionA = Utils.getVersionFromString(versionAStr, "-|\\.");
			String versionBStr = getVersionFromReleaseNotesInclude(b, RELEASE_NOTES_PREFIX, RELEASE_NOTES_SUFFIX);
			List<Integer> versionB = Utils.getVersionFromString(versionBStr, "-|\\.");

			return Utils.compareVersionsDesc(versionA, versionB);
		}
	}

	private String reordenarListaIncludesReleaseNotesHtmls(String releaseNotesCompletoContent) {
		String[] linhas = releaseNotesCompletoContent.split("\n");
		List<String> linhasArquivoAlterado = new ArrayList<>();
		List<String> releaseNotesIncludes = new ArrayList<>();
		// 1. identifica todos os includes de release notes e mantem no arquivo as
		// informacoes que sejam válidas e não sejam desses includes
		boolean encontrouAlgumIncludeValido = false;
		for (String linha : linhas) {
			if (linha.trim().startsWith("//")) { // comentarios permanessem onde estão
				linhasArquivoAlterado.add(linha);
			} else if (linha.toLowerCase().contains(("link:includes/" + RELEASE_NOTES_PREFIX).toLowerCase())
					&& linha.startsWith("=")) {
				encontrouAlgumIncludeValido = true;
				releaseNotesIncludes.add(linha);
				// linhas que não sejam de include de release continuam onde estão, espaços em
				// branco permanessem onde estão se não estiverem entre os includes de releases
			} else if (StringUtils.isNotBlank(linha) || !encontrouAlgumIncludeValido) {
				linhasArquivoAlterado.add(linha);
			}
		}
		messages.info("Existem " + releaseNotesIncludes.size() + " releases.");
		// 3. ordena os release notes pelo número da versão ASC
		Collections.sort(releaseNotesIncludes, new SortReleaseNotesHtmlByVersion());
		// 4. remonta o arquivo:
		for (String releaseInclude : releaseNotesIncludes) {
			// 4.2. Grava cada include e adiciona um \n no início e outro no final
			linhasArquivoAlterado.add("\n" + releaseInclude);
		}
		return String.join("\n", linhasArquivoAlterado);
	}

	class SortReleaseNotesHtmlByVersion implements Comparator<String> {
		public int compare(String a, String b) {
			String versionAStr = getVersionFromReleaseNotesInclude(a, RELEASE_NOTES_PREFIX, RELEASE_NOTES_HTML_SUFFIX);
			List<Integer> versionA = Utils.getVersionFromString(versionAStr, "-|\\.");
			String versionBStr = getVersionFromReleaseNotesInclude(b, RELEASE_NOTES_PREFIX, RELEASE_NOTES_HTML_SUFFIX);
			List<Integer> versionB = Utils.getVersionFromString(versionBStr, "-|\\.");

			return Utils.compareVersionsDesc(versionA, versionB);
		}
	}

	private String getVersionFromReleaseNotesInclude(String include, String prefix, String suffix) {
		return include.replaceFirst(".*" + prefix, "").replaceFirst(suffix + ".*", "");
	}

	private String getProjectNameInDocumentacaoFromJiraProject(JiraProject project) {
		String projectNameInDocumentacao = project.getName();
		if (project.getKey().equalsIgnoreCase("PJEII") || project.getKey().equalsIgnoreCase("PJEVII")
				|| project.getKey().equalsIgnoreCase("PJELEG") || project.getKey().equalsIgnoreCase("PJEVSII")
				|| project.getKey().equalsIgnoreCase("TESTE")) {
			projectNameInDocumentacao = "pje-legacy";
		}
		return projectNameInDocumentacao;
	}

	private String getPathNameReleaseNotes(JiraProject project, String versao) {
		String projectNameInDocumentacao = getProjectNameInDocumentacaoFromJiraProject(project);

		StringBuilder pathNameRelease = new StringBuilder(PATHNAME_DOCUMENTACAO_RELEASE_NOTES)
				.append(projectNameInDocumentacao).append(RELEASE_NOTES_INCLUDES_DIR).append(RELEASE_NOTES_PREFIX)
				.append(versao.replaceAll("\\.", "-")).append(RELEASE_NOTES_SUFFIX);

		return pathNameRelease.toString();
	}

	public static final int ISSUE_TYPE_HOTFIX = 10202;
	public static final int ISSUE_TYPE_BUGFIX = 10201;
	public static final int ISSUE_TYPE_BUG = 1;
	public static final int ISSUE_TYPE_NEWFEATURE = 2;
	public static final int ISSUE_TYPE_IMPROVEMENT = 4;
	public static final int ISSUE_TYPE_MINOR_CHANGES = 5;

	public VersionReleaseNotesIssueTypeEnum getIssueTypeEnum(JiraIssuetype issueType) {
		VersionReleaseNotesIssueTypeEnum releaseNotesIssueType = null;
		switch (issueType.getId().intValue()) {
		case ISSUE_TYPE_HOTFIX:
		case ISSUE_TYPE_BUGFIX:
		case ISSUE_TYPE_BUG:
			releaseNotesIssueType = VersionReleaseNotesIssueTypeEnum.BUGFIX;
			break;
		case ISSUE_TYPE_NEWFEATURE:
			releaseNotesIssueType = VersionReleaseNotesIssueTypeEnum.NEW_FEATURE;
			break;
		case ISSUE_TYPE_IMPROVEMENT:
			releaseNotesIssueType = VersionReleaseNotesIssueTypeEnum.IMPROVEMENT;
			break;
		default:
			releaseNotesIssueType = VersionReleaseNotesIssueTypeEnum.MINOR_CHANGES;
			break;
		}

		return releaseNotesIssueType;
	}

	private static final String TRANSICTION_REGENERATE_RELEASE_NOTES = "Regerar release notes"; // TODO buscar por
																								// propriedade da
																								// transicao
	private void atualizarDescricao(JiraIssue issue, VersionReleaseNotes releaseNotes) {
		Map<String, Object> updateFields = new HashMap<>();
		try {
			String releaseNotesJson = Utils.convertObjectToJson(releaseNotes);
			// 1. envia o json do release-notes para a issue
			jiraService.sendTextAsAttachment(issue, JiraService.RELEASE_NOTES_JSON_FILENAME, releaseNotesJson);

			// 2. converte o release-notes em um markdown do próprio jira e preenche o campo
			// descrição
			JiraMarkdown jiraMarkdown = new JiraMarkdown();
			releaseNotesModel.setReleaseNotes(releaseNotes);
			String jiraMarkdownRelease = releaseNotesModel.convert(jiraMarkdown);
			// 3. gera comentário na issue, solicitando confirmação
			jiraService.atualizarDescricao(issue, jiraMarkdownRelease, updateFields);
			JiraIssueTransition generateReleaseNotes = jiraService.findTransitionByName(issue,
					TRANSICTION_REGENERATE_RELEASE_NOTES);
			enviarAlteracaoJira(issue, updateFields, generateReleaseNotes.getId());
		} catch (Exception e) {
			e.printStackTrace();
			try {
				String errorMessage = "Erro ao tentar atualizar o campo de descrição: " + e.getLocalizedMessage();
				jiraService.atualizarDescricao(issue,
						errorMessage, updateFields);
//				TODO
//				enviaAlteracao(issue, updateFields);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}
}