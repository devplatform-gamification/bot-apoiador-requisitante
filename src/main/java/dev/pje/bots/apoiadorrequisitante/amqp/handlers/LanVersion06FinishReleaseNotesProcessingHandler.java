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
import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.GitlabTagRelease;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraProject;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.NewVersionReleasedNewsTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.NewVersionReleasedSimpleCallTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.ReleaseNotesTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.AsciiDocMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.GitlabMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.TelegramMarkdownHtml;

@Component
public class LanVersion06FinishReleaseNotesProcessingHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion06FinishReleaseNotesProcessingHandler.class);

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

	@Autowired
	private NewVersionReleasedNewsTextModel newVersionReleasedNewsModel;

	@Autowired
	private NewVersionReleasedSimpleCallTextModel newVersionSimpleCallModel;

	private static final String TRANSITION_ID_IMPEDIMENTO = "191"; // TODO buscar por propriedade da transicao
	private static final String TRANSITION_ID_FINALIZAR_PROCESSAMENTO_PUBLICACAO_RELEASE_NOTES = "201"; // TODO buscar por propriedade da transicao

	/**
	 * :: Finalizar versao ::
	 *    Tenta recuperar o release notes do arquivo anexado à issue
	 *    Atualiza a informação de autor e data da release
	 *    
	 *    Gerar release notes no projeto docs.pje.jus.br
	 *    Comunicar lançamento da versão
	 *    Criar a issue para o lançamento da próxima versão
	 *    Lançar evento no rabbit
	 */
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_NEW_VERSION) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_RELEASE_NOTES_CONFIRMED_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_RELEASE_NOTES_CONFIRMED_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());

				VersionReleaseNotes releaseNotes = null;

				// 4.- a pessoa que solicitou a operacao está dentro do grupo de pessoas que podem abrir?
				if(jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
					// recupera o anexo cocm o json do release notes
					String releaseNotesAttachment = jiraService.getAttachmentContent(issue,
							JiraService.RELEASE_NOTES_JSON_FILENAME);
					boolean releaseNotesEncontrado = false;
					if (releaseNotesAttachment != null) {
						releaseNotes = Utils.convertJsonToJiraReleaseNotes(releaseNotesAttachment);
						if (releaseNotes != null && StringUtils.isNotBlank(releaseNotes.getProject())) {
							releaseNotesEncontrado = true;
						}
					}
					boolean releaseLancada = false;
					if(releaseNotesEncontrado) {
						if (StringUtils.isBlank(releaseNotes.getReleaseDate())) {
							// verifica se a tag da versão já existe
							boolean tagJaExistente = false;
							if (StringUtils.isNotBlank(releaseNotes.getGitlabProjectId())) {
								GitlabTag tag = gitlabService.getVersionTag(releaseNotes.getGitlabProjectId(),
										releaseNotes.getVersion());
								if (tag != null && tag.getCommit() != null) {
									tagJaExistente = true;
									releaseNotes.setReleaseDate(tag.getCommit().getCommittedDate().toString());
									JiraUser usuarioCommiter = null;
									if (tag.getCommit().getCommitterName() != null) {
										usuarioCommiter = jiraService
												.getUserFromUserName(tag.getCommit().getCommitterEmail());
									}
									if (usuarioCommiter != null) {
										releaseNotes.setAuthor(usuarioCommiter);
									}
								}
							}
							if (!tagJaExistente) {
								messages.error("A tag: " + releaseNotes.getVersion()
									+ " ainda NÃO foi lançada.");
							} else {
								releaseLancada = true;
							}
						}else {
							messages.info("Esta versão foi lançada em: " + releaseNotes.getReleaseDate());
							releaseLancada = true;
						}
						if(releaseLancada) {
							// Gerar documento asciidoc e incluir no docs.pje.jus.br
							// TODO - deve retornar o path do arquivo de release notes criado no asciidoc - este path deverá ser adicionado à issue
							String urlReleaseNotes = criarDocumentoReleaseNotesNoProjetoDocumentacao(issue, releaseNotes);
							releaseNotes.setUrl(urlReleaseNotes);
							// lançar o texto do release do gitlab se não houver texto na release
							if(!isTagReleaseCreated(releaseNotes.getGitlabProjectId(), releaseNotes.getVersion())) {
								GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
								releaseNotesModel.setReleaseNotes(releaseNotes);
								String releaseText = releaseNotesModel.convert(gitlabMarkdown);
								GitlabTagRelease tagReleaseResponse = gitlabService.createTagRelease(releaseNotes.getGitlabProjectId(), releaseNotes.getVersion(), releaseText);
								if(tagReleaseResponse != null) {
									messages.info("Criada a release da tag do projeto: " + releaseNotes.getProject());
								}else {
									messages.error("Erro ao criar release da tag do projeto: " + releaseNotes.getProject());
								}
							}
							if(!messages.hasSomeError()) {
								// comunicar o lançamento da versão
								comunicarLancamentoVersao(releaseNotes);
								// cria a nova versao nos projetos associado no jira
								String projectKey = issue.getFields().getProject().getKey();
								jiraService.createVersionInRelatedProjects(issue.getFields().getProject().getKey(), releaseNotes.getNextVersion());
								// criar nova issue de lancamento de versão para a próxima versão
								JiraIssuetype issueType = issue.getFields().getIssuetype();
								criarIssueLancamentoVersao(projectKey, issueType, releaseNotes);
							}
						}else {
							messages.error("A release ainda não foi lançada.");
						}
					}else {
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
					// atualiza o anexo com o backup do relase em json
					String releaseNotesJson = Utils.convertObjectToJson(releaseNotes);
					jiraService.sendTextAsAttachment(issue, JiraService.RELEASE_NOTES_JSON_FILENAME, releaseNotesJson);

					// atualiza a descricao da issue com o novo texto
					JiraMarkdown jiraMarkdown = new JiraMarkdown();
					releaseNotesModel.setReleaseNotes(releaseNotes);
					String jiraMarkdownRelease = releaseNotesModel.convert(jiraMarkdown);
					jiraService.atualizarDescricao(issue, jiraMarkdownRelease, updateFields);

					// atualiza a data de lancamento do release notes
					String dataTagStr = Utils.dateToStringPattern(new Date(), JiraService.JIRA_DATETIME_PATTERN);
					jiraService.atualizarDataReleaseNotes(issue, dataTagStr, updateFields);
					// atualiza link para o release notes na issue
					jiraService.atualizarURLReleaseNotes(issue, releaseNotes.getUrl(), updateFields);
					
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_FINALIZAR_PROCESSAMENTO_PUBLICACAO_RELEASE_NOTES);
				}
			}
		}
	}
	
	private void comunicarLancamentoVersao(VersionReleaseNotes releaseNotes) {
		TelegramMarkdownHtml telegramMarkdown = new TelegramMarkdownHtml();
		
		newVersionReleasedNewsModel.setReleaseNotes(releaseNotes);
		String versionReleasedNews = newVersionReleasedNewsModel.convert(telegramMarkdown);
		telegramService.sendBotMessageHtml(versionReleasedNews);// FIXME = encaminhar para o canal correto
		
		newVersionSimpleCallModel.setReleaseNotes(releaseNotes);
		String versionReleasedSimpleCall = newVersionSimpleCallModel.convert(telegramMarkdown); // FIXME - criar o markdown para rocketchat + slack
		slackService.sendBotMessage(versionReleasedSimpleCall);
		
		// TODO criar service e client para o rocketchat
	}
	
	/**
	 * Cria uma nova issue:
	 * 	- tipo: lancamento de uma nova versao
	 *  - número da versao sendo o número da versao indicada como próxima da issue atual
	 *  - tipo de versao: ordinaria
	 *  - se o projeto implementa gitflow, entao terá a marcacao de criacao automática da release candidate
	 *  
	 * @param projectKey
	 * @param issueType
	 * @param releaseNotes
	 * @throws Exception
	 */
	private void criarIssueLancamentoVersao(String projectKey, JiraIssuetype issueType, VersionReleaseNotes releaseNotes) throws Exception { // TODO
		// verifica se já existe uma issue com estas características
		String jql = jiraService.getJqlIssuesLancamentoVersao(releaseNotes.getNextVersion(), projectKey);
		
		List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
		if(issues == null || issues.isEmpty()) {
			Map<String, Object> issueFields = new HashMap<>();
			// add project
			jiraService.novaIssueCriarProject(projectKey, issueFields);
			// add issueType
			jiraService.novaIssueCriarIssueType(issueType, issueFields);
			// add summary
			jiraService.novaIssueCriarSummary("Lançamento da versão " + releaseNotes.getNextVersion(), issueFields);
			// add Gerar versão RC automaticamente? - apenas se o projeto possuir gitflow
			Boolean implementsGitflow = false;
			if (StringUtils.isNotBlank(releaseNotes.getGitlabProjectId())) {
				implementsGitflow = gitlabService
						.isProjectImplementsGitflow(releaseNotes.getGitlabProjectId());
			}
			if(implementsGitflow) {
				jiraService.novaIssueCriarIndicacaoGeracaoReleaseCandidateAutomaticamente(issueFields);
			}
			// add versao afetada (version)
			JiraVersion versao = new JiraVersion();
			versao.setName(releaseNotes.getNextVersion());
			jiraService.novaIssueCriarAffectedVersion(versao, issueFields);
			// add tipo de versao (ordinaria)
			jiraService.novaIssueCriarTipoVersao("Ordinária", issueFields);
			
			enviarCriacaoJiraIssue(issueFields);
			
		}else {
			messages.info("A issue da próxima vesrsão já existe: " + issues.get(0).getKey());
		}
		
	}
	
	private boolean isTagReleaseCreated(String gitlabProjectId, String tagName) {
		boolean tagReleaseCreated = false;

		GitlabTag tag = gitlabService.getVersionTag(gitlabProjectId, tagName);
		if (tag != null && tag.getCommit() != null) {
			tagReleaseCreated = StringUtils.isNotBlank(tag.getRelease().getDescription());
		}
		
		return tagReleaseCreated;
	}
	
	private static final String PATHNAME_DOCUMENTACAO_RELEASE_NOTES = "src/main/asciidoc/projetos/";
	private static final String RELEASE_NOTES_PREFIX = "release-notes_";
	private static final String RELEASE_NOTES_SUFFIX = ".adoc";
	private static final String RELEASE_NOTES_HTML_SUFFIX = ".html";
	private static final String RELEASE_NOTES_INCLUDES_DIR = "/release-notes/includes/";
	private static final String RELEASE_NOTES_RELEASE_COMPLETO = "/release-notes/release-notes-completo.adoc";
	private static final String RELEASE_NOTES_RELEASE_LISTA = "/release-notes/index.adoc";

	private String criarDocumentoReleaseNotesNoProjetoDocumentacao(JiraIssue issue, VersionReleaseNotes releaseNotes)
			throws ParseException {
		String documentationURL = null;
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
		
		documentationURL = filePath;
		return documentationURL;
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
				messages.info("O include do release notes |" + fileName + "| já existe no arquivo de releases completas");
			} else {
				messages.info("O include do release notes |" + fileName
						+ "| AINDA NÃO existe no arquivo de releases completas");
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
			messages.error("Não foi possível encontrar o arquivo "
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
}