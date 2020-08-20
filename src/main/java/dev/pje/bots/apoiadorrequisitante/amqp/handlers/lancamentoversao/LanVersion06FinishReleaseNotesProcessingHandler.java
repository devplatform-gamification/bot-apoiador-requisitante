package dev.pje.bots.apoiadorrequisitante.amqp.handlers.lancamentoversao;

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
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTipoVersaoEnum;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
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
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_RELEASE_NOTES_FINISHPROCESSING_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_RELEASE_NOTES_FINISHPROCESSING_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());

				VersionReleaseNotes releaseNotes = null;
				String dataReleaseNotesStr = Utils.dateToStringPattern(new Date(), JiraService.JIRA_DATETIME_PATTERN);

				// 4.- a pessoa que solicitou a operacao está dentro do grupo de pessoas que podem abrir?
				if(jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
					// recupera o anexo cocm o json do release notes
					byte[] file = jiraService.getAttachmentContent(issue,
							JiraService.RELEASE_NOTES_JSON_FILENAME);
					String releaseNotesAttachment = new String(file);
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
												.findUserByUserName(tag.getCommit().getCommitterEmail());
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
							JiraIssue issueDocumentacao = criarIssueDocumentacaoReleaseNotes(releaseNotes);
							if(issueDocumentacao != null) {
								Map<String, Object> updateFields = new HashMap<>();
								// add link issue anterior
								jiraService.criarNovoLink(issue, issueDocumentacao.getKey(), 
										JiraService.ISSUELINKTYPE_DOCUMENTATION_ID.toString(), JiraService.ISSUELINKTYPE_DOCUMENTATION_OUTWARDNAME, false, updateFields);
								if(updateFields != null && !updateFields.isEmpty()) {
									enviarAlteracaoJira(issue, updateFields, null, false, false);
								}
							}

					/* passar esta atividade para o projeto de documentacao */
					/*******************/
							// Gerar documento asciidoc e incluir no docs.pje.jus.br
							String versaoAfetada = releaseNotes.getAffectedVersion();
							releaseNotes.setAffectedVersion(releaseNotes.getVersion());
//							String urlReleaseNotes = criarDocumentoReleaseNotesNoProjetoDocumentacao(issue, releaseNotes);
//							if(StringUtils.isNotBlank(urlReleaseNotes)) {
//								releaseNotes.setUrl(urlReleaseNotes);
//							}
					/*******************/
							// lançar o texto do release do gitlab se não houver texto na release
							if(!isTagReleaseCreated(releaseNotes.getGitlabProjectId(), releaseNotes.getVersion())) {
								GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
								releaseNotesModel.setReleaseNotes(releaseNotes);
								String releaseText = releaseNotesModel.convert(gitlabMarkdown);
								GitlabTagRelease tagReleaseResponse = gitlabService.createTagRelease(releaseNotes.getGitlabProjectId(), releaseNotes.getVersion(), releaseText);
								if(tagReleaseResponse != null) {
									messages.info("Criada o documento de release da tag do projeto: " + releaseNotes.getProject());
								}else {
									messages.error("Erro ao criar o documento de release da tag do projeto: " + releaseNotes.getProject());
								}
							}
							if(!messages.hasSomeError()) {
								// comunicar o lançamento da versão
								Boolean comunicarLancamentoVersao = false;
								if(issue.getFields().getComunicarLancamentoVersao() != null 
										&& !issue.getFields().getComunicarLancamentoVersao().isEmpty()) {
									
									if(issue.getFields().getComunicarLancamentoVersao().get(0).getValue().equalsIgnoreCase("Sim")) {
										comunicarLancamentoVersao = true;
									}
								}
								comunicarLancamentoVersao(releaseNotes, comunicarLancamentoVersao);
								
								// indica a data de lançamento da versão atual no jira, atualizando o número da versão também se necessário
								jiraService.releaseVersionInRelatedProjects(
											issue.getFields().getProject().getKey(), 
											versaoAfetada, 
											releaseNotes.getVersion(), 
											dataReleaseNotesStr, "Alterações da versão: " + releaseNotes.getVersion());

								// verifia se foi solicitada a inicializacao da próxima versão indicada
								Boolean iniciarProximaVersaoAutomaticamente = false;
								if(issue.getFields().getIniciarProximaVersaoAutomaticamente() != null 
										&& !issue.getFields().getIniciarProximaVersaoAutomaticamente().isEmpty()) {
									
									if(issue.getFields().getIniciarProximaVersaoAutomaticamente().get(0).getValue().equalsIgnoreCase("Sim")) {
										iniciarProximaVersaoAutomaticamente = true;
										messages.info("Iniciando a issue da próxima versão: " + releaseNotes.getNextVersion() + " automaticamente como solicitado.");
									}
								}
								if(iniciarProximaVersaoAutomaticamente) {
									// cria a nova versao nos projetos associados no jira
									String projectKey = issue.getFields().getProject().getKey();
									jiraService.createVersionInRelatedProjects(issue.getFields().getProject().getKey(), releaseNotes.getNextVersion());
									
									// criar nova issue de lancamento de versão para a próxima versão
									JiraIssuetype issueType = issue.getFields().getIssuetype();
									JiraIssue issueProximaVersao = criarIssueLancamentoVersao(projectKey, issueType, releaseNotes);
									if(issueProximaVersao != null) {
										// add link issue anterior
										Map<String, Object> updateFields = new HashMap<>();
										
										jiraService.criarNovoLink(issue, issueProximaVersao.getKey(), 
												JiraService.ISSUELINKTYPE_RELATES_ID.toString(), JiraService.ISSUELINKTYPE_RELATES_OUTWARDNAME, false, updateFields);
										
										if(updateFields != null && !updateFields.isEmpty()) {
											enviarAlteracaoJira(issue, updateFields, null, false, false);
										}
									}
								}
							}
						}else {
							messages.error("A release ainda não foi lançada.");
						}
					}else {
						messages.error("Release notes não encontrado na issue de referência: " + issue.getKey());
					}
				}else {
					messages.error("O usuário: [~" + jiraEventIssue.getUser().getName() + "] - não tem permissão para fazer esta operação.");
				}
				Map<String, Object> updateFields = new HashMap<>();
				if(messages.hasSomeError()) {
					// tramita para o impedmento, enviando as mensagens nos comentários
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, JiraService.TRANSITION_PROPERTY_KEY_IMPEDIMENTO, true, true);
				}else {
					// tramita automaticamente, enviando as mensagens nos comentários
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
					jiraService.atualizarURLPublicacao(issue, releaseNotes.getUrl(), updateFields);
					
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, JiraService.TRANSITION_PROPERTY_KEY_FINALIZAR_DEMANDA, true, true);
				}
			}
		}
	}
	
	private void comunicarLancamentoVersao(VersionReleaseNotes releaseNotes, Boolean comunicarCanaisOficiais) {
		TelegramMarkdownHtml telegramMarkdown = new TelegramMarkdownHtml();
		
		newVersionReleasedNewsModel.setReleaseNotes(releaseNotes);
		String versionReleasedNews = newVersionReleasedNewsModel.convert(telegramMarkdown);
		telegramService.sendBotMessageHtml(versionReleasedNews);
		
		
		newVersionSimpleCallModel.setReleaseNotes(releaseNotes);
		String versionReleasedSimpleCall = newVersionSimpleCallModel.convert(telegramMarkdown); // FIXME - criar o markdown para rocketchat + slack
		slackService.sendBotMessage(versionReleasedSimpleCall);
		
		if(comunicarCanaisOficiais) {
			telegramService.sendOficialChannelMessageHtml(versionReleasedNews);
		}
		
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
	private JiraIssue criarIssueLancamentoVersao(String projectKey, JiraIssuetype issueType, VersionReleaseNotes releaseNotes) throws Exception {
		JiraIssue issueRelacionada = null;
		// verifica se já existe uma issue com estas características
		String jql = jiraService.getJqlIssuesLancamentoVersao(releaseNotes.getNextVersion(), projectKey);
		
		List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
		if(issues == null || issues.isEmpty()) {
			Map<String, Object> issueFields = new HashMap<>();
			Map<String, Object> issueUpdateFields = new HashMap<>();

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
			// add link issue anterior
			jiraService.criarNovoLink(null, releaseNotes.getIssueKey(), 
					JiraService.ISSUELINKTYPE_RELATES_ID.toString(), JiraService.ISSUELINKTYPE_RELATES_INWARDNAME, true, issueUpdateFields);

			issueRelacionada = enviarCriacaoJiraIssue(issueFields, issueUpdateFields);
			
		}else {
			issueRelacionada = issues.get(0);
			messages.info("A issue da próxima vesrsão já existe: " + issueRelacionada.getKey());
		}
		return issueRelacionada;
	}
	
	private JiraIssue criarIssueDocumentacaoReleaseNotes(VersionReleaseNotes releaseNotes) throws Exception {
		JiraIssue relatedIssueDoc = null;
		String projectKey = JiraService.PROJECTKEY_PJEDOC;
		String issueTypeId = JiraService.ISSUE_TYPE_RELEASE_NOTES.toString();
		String versaoASerLancada = releaseNotes.getVersion();
		String issueProjectKey = JiraUtils.getProjectKeyFromIssueKey(releaseNotes.getIssueKey());
		String summary = "[" + issueProjectKey + "] Release notes do projeto: " + releaseNotes.getProject() + " - versao: " + versaoASerLancada;
		
		// gera o conteúdo da release em asciidoc
		releaseNotesModel.setReleaseNotes(releaseNotes);
		String textoAnexo = releaseNotesModel.convert(new AsciiDocMarkdown());
		StringBuilder nomeArquivoAnexoSB = new StringBuilder(JiraService.RELEASE_NOTES_FILENAME_PREFIX)
				.append(versaoASerLancada.replaceAll("\\.", "-")).append(JiraService.FILENAME_SUFFIX_ADOC);
		
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

		// Identificacao da estrutura de documentacao
		String identificacaoEstruturaDocumentacao = jiraService.getEstruturaDocumentacao(issueProjectKey);
		String opcaoId = null;
		if(StringUtils.isNotBlank(identificacaoEstruturaDocumentacao)) {
			String[] opcoes = identificacaoEstruturaDocumentacao.split(":");
			if(opcoes != null && opcoes.length > 0) {
				opcaoId = opcoes[(opcoes.length - 1)];
			}
		}
		
		if(StringUtils.isNotBlank(opcaoId)) {
			// verifica se já existe uma issue com estas características
			String jql = jiraService.getJqlIssuesDocumentacaoReleaseNotes(versaoASerLancada, opcaoId);
			
			List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
			if(issues == null || issues.isEmpty()) {
				Map<String, Object> issueFields = new HashMap<>();
				Map<String, Object> issueUpdateFields = new HashMap<>();
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
				// criar já na criação da issue um link para a issue de lancamento de versao
				jiraService.criarNovoLink(null, releaseNotes.getIssueKey(), 
						JiraService.ISSUELINKTYPE_DOCUMENTATION_ID.toString(), JiraService.ISSUELINKTYPE_DOCUMENTATION_INWARDNAME, true, issueUpdateFields);
				
				// identificar estrutura da documentacao
				// add estrutura documentacao
				jiraService.novaIssueCriarEstruturaDocumentacao(identificacaoEstruturaDocumentacao, issueFields);
				relatedIssueDoc = enviarCriacaoJiraIssue(issueFields, issueUpdateFields);
				
				if(relatedIssueDoc != null) {
					// envia o conteúdo do texto como asciidoc na issue remota
					jiraService.sendTextAsAttachment(relatedIssueDoc, nomeArquivoAnexoSB.toString(), textoAnexo);
				}
			}else {
				relatedIssueDoc = issues.get(0);
				messages.info("A issue da documentação desta vesrsão já existe: " + relatedIssueDoc.getKey());
			}
		}else {
			messages.error("Não foi possível identificar automaticamente qual é a estrutura de "
					+ "documentação relacionada para este projeto " + issueProjectKey 
					+ " no projeto de documentação " + projectKey);
		}
				
		return relatedIssueDoc;
	}
	
	private boolean isTagReleaseCreated(String gitlabProjectId, String tagName) {
		boolean tagReleaseCreated = false;

		GitlabTag tag = gitlabService.getVersionTag(gitlabProjectId, tagName);
		if (tag != null && tag.getCommit() != null && tag.getRelease() != null) {
			tagReleaseCreated = StringUtils.isNotBlank(tag.getRelease().getDescription());
		}
		
		return tagReleaseCreated;
	}
}