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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.VersionReleaseNotes;
import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.GitlabTagRelease;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTipoVersaoEnum;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraProject;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.custom.JiraCustomFieldOption;
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

	@Value("${project.documentation.url}")
	private String DOCSURL;

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
				String dataReleaseNotesStr = Utils.dateToStringPattern(new Date(), JiraService.JIRA_DATETIME_PATTERN);

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
							// criar a issue de release-notes no projeto de documentacao
							
			
					/* passar esta atividade para o projeto de documentacao */
					/*******************/
							// Gerar documento asciidoc e incluir no docs.pje.jus.br
							String versaoAfetada = releaseNotes.getAffectedVersion();
							releaseNotes.setAffectedVersion(releaseNotes.getVersion());
							String urlReleaseNotes = criarDocumentoReleaseNotesNoProjetoDocumentacao(issue, releaseNotes);
							releaseNotes.setUrl(urlReleaseNotes);
					/*******************/
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
								
								// indica a data de lançamento da versão atual no jira, atualizando o número da versão também se necessário
								jiraService.releaseVersionInRelatedProjects(
											issue.getFields().getProject().getKey(), 
											versaoAfetada, 
											releaseNotes.getVersion(), 
											dataReleaseNotesStr, "Alterações da versão: " + releaseNotes.getVersion() + " - link: " + urlReleaseNotes);
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
					jiraService.atualizarDataReleaseNotes(issue, dataReleaseNotesStr, updateFields);
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
	private void criarIssueLancamentoVersao(String projectKey, JiraIssuetype issueType, VersionReleaseNotes releaseNotes) throws Exception {
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
			jiraService.novaIssueCriarTipoVersao(JiraIssueTipoVersaoEnum.ORDINARIA.toString(), issueFields);
			
			enviarCriacaoJiraIssue(issueFields);
			
		}else {
			messages.info("A issue da próxima vesrsão já existe: " + issues.get(0).getKey());
		}
	}
	
	private void criarIssueDocumentacaoReleaseNotes(VersionReleaseNotes releaseNotes) throws Exception {
		String projectKey = JiraService.PROJECTKEY_PJEDOC;
		String issueTypeId = JiraService.ISSUE_TYPE_RELEASE_NOTES.toString();
		String versaoASerLancada = releaseNotes.getVersion();
		
		// TODO - identificar item da estrutura
		String issueProjectKey = JiraUtils.getProjectKeyFromIssueKey(releaseNotes.getIssueKey());
		String summary = "[" + issueProjectKey + "] Release notes do projeto: " + releaseNotes.getProject() + " - versao: " + versaoASerLancada;
		
		releaseNotesModel.setReleaseNotes(releaseNotes);
		String description = releaseNotesModel.convert(new JiraMarkdown());

		// 0. identificar o projeto de documentacao + tipo de issue de relase notes
		
		// 1. verificar se a issue já não existe
		// 2. montar campos da nova issue:
		// - projeto
		// - tipo
		// - estrutura
		// - versao a ser lancada
		// - resumo
		// - descricao
		// - publicar doc automaticamente? = SIM
		
		
		// verifica se já existe uma issue com estas características
		String jql = jiraService.getJqlIssuesDocumentacaoReleaseNotes(versaoASerLancada);
		
		List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
		if(issues == null || issues.isEmpty()) {
			Map<String, Object> issueFields = new HashMap<>();
			// add project
			jiraService.novaIssueCriarProject(projectKey, issueFields);
			// add issueType
			jiraService.novaIssueCriarIssueTypeId(issueTypeId, issueFields);
			// add summary
			jiraService.novaIssueCriarSummary(summary, issueFields);
			// add description
			jiraService.novaIssueCriarDescription(description, issueFields);

			// add Publicar documentacao automaticamente? = sim
			jiraService.novaIssueCriarIndicacaoPublicarDocumentacaoAutomaticamente(issueFields);
			// add versao a ser lancada
			jiraService.novaIssueCriarVersaoASerLancada(versaoASerLancada, issueFields);

			// identificar estrutura
			JiraCustomFieldOption estruturaDocumentacaoOption = jiraService.getEstruturaDocumentacao(issueProjectKey);
			
			// TODO - criar já na criação da issue um link para a issue de lancamento de versao
			if(estruturaDocumentacaoOption != null) {
				// add estrutura documentacao
				jiraService.novaIssueCriarEstruturaDocumentacao(estruturaDocumentacaoOption, issueFields);
				enviarCriacaoJiraIssue(issueFields);
			}else {
				messages.error("Não foi possível identificar automaticamente qual é a estrutura de "
						+ "documentação relacionada para este projeto " + issueProjectKey 
						+ " no projeto de documentação " + projectKey);
			}
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
	
	private static final String PATHNAME_SRC_DOCUMENTACAO_RELEASE_NOTES = "src/main/asciidoc/";
	private static final String PATHNAME_SERVICOS_NEGOCIAIS_DOCUMENTACAO = "/servicos-negociais/";
	private static final String RELEASE_NOTES_PREFIX = "release-notes_";
	private static final String RELEASE_NOTES_SUFFIX = ".adoc";
	private static final String RELEASE_NOTES_HTML_SUFFIX = ".html";
	private static final String RELEASE_NOTES_INCLUDES_DIR = "/release-notes/includes/";
	private static final String RELEASE_NOTES_RELEASE_COMPLETO = "/release-notes/release-notes-completo.adoc";
	private static final String RELEASE_NOTES_RELEASE_LISTA = "/release-notes/index.adoc";

	private String criarDocumentoReleaseNotesNoProjetoDocumentacao(JiraIssue issue, VersionReleaseNotes releaseNotes)
			throws ParseException {
		String documentationURL = null;
		
		String gitlabProjectId = GitlabService.PROJECT_DOCUMENTACO;
		String filePath = getPathNameReleaseNotes(issue.getFields().getProject(), releaseNotes.getVersion());
		String branchName = issue.getKey();
		GitlabBranchResponse branchResponse = gitlabService.createFeatureBranch(gitlabProjectId, branchName);
		if(branchResponse != null && branchName.equals(branchResponse.getBranchName())) {
			messages.info("Feature branch: " + branchName + " criado no projeto: " + gitlabProjectId);
		}else {
			messages.error("Erro ao tentar criar o feature branch: " + branchName + " no projeto: " + gitlabProjectId);
		}
		if(!messages.hasSomeError()) {
			// gera o conteúdo da release em asciidoc
			releaseNotesModel.setReleaseNotes(releaseNotes);
			String texto = releaseNotesModel.convert(new AsciiDocMarkdown());
			
			// verifica se já existe o arquivo no destino e se o texto é diferente, caso contrário, mantem como está
			String conteudoArquivo = gitlabService.getRawFile(gitlabProjectId, filePath, branchName);
			if(StringUtils.isBlank(conteudoArquivo) || (!Utils.compareAsciiIgnoreCase(conteudoArquivo, texto))) {
				// criar um arquivo no projeto docs.pje.jus.br com o conteúdo deste arquivo
				String commitMessage = "[" + issue.getKey() + "] Gerando release notes da versão " + releaseNotes.getVersion();
				GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(gitlabProjectId, branchResponse, filePath,
						texto, commitMessage);
				
				if(commitResponse != null) {
					messages.info("Criado o arquivo: " + filePath + " no branch: " + branchName + " do projeto: " + gitlabProjectId);
					messages.debug(commitResponse.toString());
				}else {
					messages.error("Erro ao tentar criar o arquivo: " + filePath + " no branch: " + branchName + " do projeto: " + gitlabProjectId);
				}
			}else {
				messages.info("Arquivo: " + filePath + " já existe no branch: " + branchName + " do projeto: " + gitlabProjectId);
			}
		}

		String fileNameAdoc = Utils.getFileNameFromFilePath(filePath);
		String fileNameHtml = fileNameAdoc.replace(RELEASE_NOTES_SUFFIX, RELEASE_NOTES_HTML_SUFFIX);
		if(!messages.hasSomeError()) {
			adicionaNovoArquivoReleaseNotesNoReleaseCompleto(issue, gitlabProjectId, branchName, fileNameAdoc);
		}
		if(!messages.hasSomeError()) {
			adicionaNovoArquivoReleaseNotesNaListaDeReleases(issue, gitlabProjectId, branchName, fileNameHtml,
					releaseNotes.getVersion(), releaseNotes.getReleaseDate());
		}
		if(!messages.hasSomeError()) {
			documentationURL = createDocumentationLink(fileNameHtml, Utils.getPathFromFilePath(filePath));
		}
		if(!messages.hasSomeError()) {
			String mergeMessage = "[" + issue.getKey() + "] Gerando release notes da versão " + releaseNotes.getVersion();
			GitlabMRResponse mrResponse = null;
			String erro = null;
			try {
//				mrResponse = gitlabService.mergeFeatureBranchIntoBranchDefault(gitlabProjectId, branchName, mergeMessage);
			} catch (Exception e) {
				erro = e.getLocalizedMessage();
			}
			if(mrResponse != null) {
				messages.info("Branch: " + branchName + " integrado ao branch default do projeto: " + gitlabProjectId);
				messages.debug(mrResponse.toString());
			}else {
//				messages.error("Houve um problema no merge do branch: " + branchName + " do projeto: " + gitlabProjectId);
				if(StringUtils.isNotBlank(erro)) {
					messages.error(erro);
				}
			}
		}
		return documentationURL;
	}
	
	private String createDocumentationLink(String htmlFileName, String path) {
		String pathHtml = path.replace(PATHNAME_SRC_DOCUMENTACAO_RELEASE_NOTES, "");
		return DOCSURL + Utils.normalizePaths("/" + pathHtml + "/" + htmlFileName);
	}

	/**
	 * Arquivo target: release-notes-completo.adoc <br/>
	 * Item a incluir: <br/>
	 * include::{docdir}/{docsServicePATH}/release-notes/includes/PARAM_RELEASE_NOTES_FILE.adoc[] <br/>
	 * Exemplo de inclusao: <br/>
	 * include::{docdir}/{docsServicePATH}/release-notes/includes/release-notes_2-1-8-0.adoc[] <br/>
	 * <br/>
	 * @param project
	 * @param branch
	 * @param filePath
	 */
	private void adicionaNovoArquivoReleaseNotesNoReleaseCompleto(JiraIssue issue, String gitlabProjectId,
			String branchName, String adocFileName) {

		// 2. obter o arquivo de releases completo
		String releaseNotesCompletoFileName = concatenatePathNameToProject(issue.getFields().getProject(), RELEASE_NOTES_RELEASE_COMPLETO);
		String releaseNotesCompletoContent = gitlabService.getRawFile(gitlabProjectId,
				releaseNotesCompletoFileName, branchName);

		if (StringUtils.isNotBlank(releaseNotesCompletoContent)) {
			// 3. verificar se a versão já está lá
			if (!releaseNotesCompletoContent.contains(adocFileName)) {
				// 4. adicionar o include do release
				String linhaIncludeReleaseNotes = "include::{docdir}/{docsServicePATH}/release-notes/includes/"
						+ adocFileName + "[]";
				releaseNotesCompletoContent += "\n" + linhaIncludeReleaseNotes + "\n";

				// 5. identificar a ordem de armazenamento desses includes
				releaseNotesCompletoContent = reordenarListaIncludesReleaseNotesCompleto(releaseNotesCompletoContent);
				
				String commitMessage = "[" + issue.getKey() + "] Incluindo novo release notes no arquivo de releases completas do projeto";
				GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(
						gitlabProjectId, branchName, releaseNotesCompletoFileName,
						releaseNotesCompletoContent, commitMessage);
				if(commitResponse != null) {
					messages.info("Atualizado o arquivo: " + releaseNotesCompletoFileName + " no branch: " + branchName + " do projeto: " + gitlabProjectId);
					messages.debug(commitResponse.toString());
				}else {
					messages.error("Erro ao tentar atualizar o arquivo: " + releaseNotesCompletoFileName + " no branch: " + branchName + " do projeto: " + gitlabProjectId);
				}
			} else {
				messages.info("O include do release notes |" + adocFileName + "| já existe no arquivo de releases completas");
			}
		} else {
			messages.error("Não foi possível encontrar o arquivo " + releaseNotesCompletoFileName + " no projeto " + gitlabProjectId);
		}
	}

	/**
	 * Arquivo target: index.adoc <br/>
	 * Item a incluir: <br/>
	 * 		=== link:includes/PARAM_RELEASE_NOTES_FILE.html[vPARAM_NUMERO_VERSAO - PARAM_DATA_VERSAO] <br/>
	 * Exemplo de inclusao: <br/>
	 * 		=== link:includes/release-notes_2-1-2-3.html[v2.1.2.3 - 20/12/2019] <br/>
	 * <br/>
	 * @param project
	 * @param branch
	 * @param filePath
	 * @param version
	 * @param releaseDate
	 * @throws ParseException
	 */
	private void adicionaNovoArquivoReleaseNotesNaListaDeReleases(JiraIssue issue, 
			String gitlabProjectId, String branchName, String htmlFileName, 
			String version, String releaseDate) throws ParseException {

		// 2. obter o arquivo completo
		String listaReleaseNotesFileName = concatenatePathNameToProject(issue.getFields().getProject(), RELEASE_NOTES_RELEASE_LISTA);
		String listaReleaseNotesContent = gitlabService.getRawFile(gitlabProjectId,
				listaReleaseNotesFileName, branchName);
		if (StringUtils.isNotBlank(listaReleaseNotesContent)) {
			// 3. verificar se a versão já está lá
			if (!listaReleaseNotesContent.contains(htmlFileName)) {
				// 5. adicionar o include do release
				String dataReleaseNotesStr = null;
				if (releaseDate != null) {
					Date dataReleaseNotes = Utils.stringToDate(releaseDate, null);
					dataReleaseNotesStr = Utils.dateToStringPattern(dataReleaseNotes, "dd/MM/yyyy");

					String linhaIncludeReleaseNotes = "=== link:includes/" + htmlFileName + "[v" + version + " - "
							+ dataReleaseNotesStr + "]";
					listaReleaseNotesContent += "\n" + linhaIncludeReleaseNotes + "\n";

					listaReleaseNotesContent = reordenarListaIncludesReleaseNotesHtmls(listaReleaseNotesContent);
					
					String commitMessage = "[" + issue.getKey() + "] Incluindo novo release notes no arquivo de lista de releases do projeto";
					GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(
							gitlabProjectId, branchName, listaReleaseNotesFileName, listaReleaseNotesContent,
							commitMessage);
					
					if(commitResponse != null) {
						messages.info("Atualizado o arquivo: " + listaReleaseNotesFileName + 
								" no branch: " + branchName + " do projeto: " + gitlabProjectId);
						messages.debug(commitResponse.toString());
					}else {
						messages.error("Erro ao tentar atualizar o arquivo: " + listaReleaseNotesFileName + 
								" no branch: " + branchName + " do projeto: " + gitlabProjectId);
					}
				} else {
					messages.error("Não foi possível identificar a data da release");
				}
			} else {
				messages.info("O include do release notes |" + htmlFileName  + "| já existe no arquivo de lista de releases");
			}
		} else {
			messages.error(" Não foi possível encontrar o arquivo " + listaReleaseNotesFileName
					+ " no projeto " + gitlabProjectId);
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

	/**
	 * Faz o de-para do nome do projeto do JIRA no nome do projeto no projeto de documentacao
	 * Caso não haja um de-para mapeado utilizará o próprio nome do projeto no jira
	 * 
	 * @param project
	 * @return
	 */
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
		StringBuilder fileName = new StringBuilder().append(RELEASE_NOTES_INCLUDES_DIR).append(RELEASE_NOTES_PREFIX)
				.append(versao.replaceAll("\\.", "-")).append(RELEASE_NOTES_SUFFIX);

		return concatenatePathNameToProject(project, fileName.toString());
	}
	
	private String concatenatePathNameToProject(JiraProject project, String fileName) {
		String projectNameInDocumentacao = getProjectNameInDocumentacaoFromJiraProject(project);

		StringBuilder pathNameComplete = new StringBuilder(PATHNAME_SRC_DOCUMENTACAO_RELEASE_NOTES + PATHNAME_SERVICOS_NEGOCIAIS_DOCUMENTACAO)
				.append(projectNameInDocumentacao).append(fileName);

		return Utils.normalizePaths(pathNameComplete.toString());		
	}
}