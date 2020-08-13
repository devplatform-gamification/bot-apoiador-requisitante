package dev.pje.bots.apoiadorrequisitante.amqp.handlers.docs;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.gitlab.vo.GitlabCommitFileVO;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueAttachment;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.vo.JiraEstruturaDocumentacaoVO;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class Documentation02CreateSolutionHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(Documentation02CreateSolutionHandler.class);
	
	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|DOCUMENTATION||02||CREATE-SOLUTION|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	private static final String TRANSITION_ID_INDICAR_IMPEDIMENTO = "41"; // TODO buscar por propriedade da transicao
	private static final String TRANSITION_ID_ENVIAR_PARA_HOMOLOGACAO = "31"; // TODO buscar por propriedade da transicao
		
	/**
	 * Criar documentacao relacionada à issue
	 * ok 1. obtem os dados da estrutura de documentacao
	 * ok 2. traduz os dados da estrutura de documentacao em paths: path principal + path de anexos (assets)
	 * ok 3. identifica o nome do arquivo principal de documentacao com seu path
	 * ok 4. cria o branch da issue atual - feature branch atual
	 * ok 5. verifica se já existe um documento principal com o conteúdo atual
	 * ok 6. cria a pasta de anexos (assets) e adiciona todos os demais documentos da issue lá
	 * ok 7. se for release notes:
	 * ok 7.1 adiciona o release na lista de releases concatenadas
	 * ok 7.2 adiciona o release na lista de releases com links separados
	 * TODO - ADICIONA NO SITE DA ELISA AQUI
	 * ok 8. cria um path do link de documentacao (apenas sem o host:porta)
	 * 10. Proxima atividade
	 *  
	 * @param issue
	 * @param estruturaDocumentacao
	 * @return
	 * @throws ParseException
	 */

	@Override
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if(	(
					JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_DOCUMENTATION)
					|| JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES)
					) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_DOCUMENTATION_PROCESSING_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_DOCUMENTATION_PROCESSING_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());
				issue = jiraService.recuperaIssueDetalhada(issue.getKey());
				
				if(issue.getFields() != null && issue.getFields().getProject() != null) {
					// verifica se informou o caminho da documentacao corretamente
					JiraEstruturaDocumentacaoVO estruturaDocumentacao = new JiraEstruturaDocumentacaoVO(
								issue.getFields().getEstruturaDocumentacao(), 
								issue.getFields().getNomeParaExibicaoDocumentacao(), issue.getFields().getPathDiretorioPrincipal());
	
					if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES) && estruturaDocumentacao.getEstruturaInformadaManualmente()) {
						messages.error("Para demandas de documentação de release notes, deve-se selecionar as opções de estrutura de documentação atualmente existentes (e não 'Outros')");
					}else {
						if(!estruturaDocumentacao.getEstruturaInformadaValida()) {
							if(estruturaDocumentacao.getCategoriaIndicadaOutros() || estruturaDocumentacao.getSubCategoriaIndicadaOutros()) {
								messages.error("Há um problema na identificação da estrutura de documentação, por favor, especifique a informação nos campos: 'nome para exibição da documentação' e 'path do diretório principal', essa informação será usada para a publicação da documentação.");
							}else {
								messages.error("Há um problema na identificação da estrutura de documentação, por favor, indique a informação completa corretamente, ela será usada para a publicação da documentação.");
							}
						}
					}
					
					JiraIssueAttachment anexoAdoc = recuperaAnexoDocumentoPrincipalAdoc(issue);
					if (anexoAdoc == null){
						messages.error("Deve haver um e apenas um arquivo anexo com a extensão: " + JiraService.FILENAME_SUFFIX_ADOC + " - ele será utilizado como documento principal da documentação criada.");
					}
					
					String versaoASerLancada = issue.getFields().getVersaoSeraLancada();
					if(StringUtils.isBlank(versaoASerLancada)){
						messages.error("A indicação da versão a ser lançada é obrigatória, indique apenas uma versão.");
					}
					
					String jiraProjectKey = issue.getFields().getProject().getKey();
					if(StringUtils.isBlank(jiraProjectKey)) {
						messages.error("Não foi possível identificar a chave do projeto atual");
					}
					String gitlabProjectId = jiraService.getGitlabProjectFromJiraProjectKey(jiraProjectKey);
					if(StringUtils.isBlank(gitlabProjectId)) {
						messages.error("Não foi possível identificar qual é o repositório no gitlab para este projeto do jira.");
					}
					String documentationURL = null;
					String MRsAbertos = issue.getFields().getMrAbertos();
					String MrsAbertosConfirmados = gitlabService.checkMRsOpened(MRsAbertos);
					
					String branchName = issue.getKey();
					String branchesRelacionados = issue.getFields().getBranchesRelacionados();
					if(StringUtils.isNotBlank(branchesRelacionados)) {
						String[] branches = branchesRelacionados.split(",");
						if(branches.length > 0 && StringUtils.isNotBlank(branches[0])) {
							branchName = branches[0].trim();
						}
					}

					if(!messages.hasSomeError()) {						
						// identifica o path e o nome de exibicao da categoria e da subcategoria
						if(estruturaDocumentacao.getSubCategoriaValida() && !estruturaDocumentacao.getCategoriaIndicadaOutros()) {
							String subCategoriaPath = translateSubCategoryToPathName(estruturaDocumentacao.getSubCategoriaValue(), gitlabProjectId);
							estruturaDocumentacao.setSubCategoriaPathDiretorio(subCategoriaPath);
							estruturaDocumentacao.setSubCategoriaNomeParaExibicao(estruturaDocumentacao.getSubCategoriaValue());
						}
						if(!estruturaDocumentacao.getCategoriaIndicadaOutros()) {
							String categoriaPath = translateCategoryToPathName(estruturaDocumentacao.getCategoriaValue());
							estruturaDocumentacao.setCategoriaPathDiretorio(categoriaPath);
							estruturaDocumentacao.setCategoriaNomeParaExibicao(estruturaDocumentacao.getCategoriaValue());
						}
	
						// Para o tipo de documentacao release notes, o path referência será um subpath do projeto principal
						String pathSrcPreffix = PATHNAME_SRC_DOCUMENTACAO;
						String projectPath = null;
						String documentoPrincipalNomeAdoc = null;
						if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES)) {
							String fullPath = estruturaDocumentacao.getSubCategoriaPathDiretorio();
							if(fullPath != null) {
								projectPath = fullPath;
								if(fullPath.contains("html")) {
									projectPath = Utils.getPathFromFilePath(fullPath);
								}
								projectPath += "/" + RELEASE_NOTES_DIR + "/" + RELEASE_NOTES_INCLUDES_DIR;
								documentoPrincipalNomeAdoc = anexoAdoc.getFilename();
							}
						}else {
							// Para outros tipos de documentacao o path referência será exatamente o projeto principal
							if(estruturaDocumentacao.getSubCategoriaValida()) {
								projectPath = estruturaDocumentacao.getSubCategoriaPathDiretorio();
							}else {
								projectPath = estruturaDocumentacao.getCategoriaPathDiretorio();
							}
							if(projectPath.contains("html")) {
								String fileNameHtml = Utils.getFileNameFromFilePath(projectPath);
								documentoPrincipalNomeAdoc = fileNameHtml.replace(JiraService.FILENAME_SUFFIX_HTML, JiraService.FILENAME_SUFFIX_ADOC);
								
								projectPath = Utils.getPathFromFilePath(projectPath);
							}else {
								documentoPrincipalNomeAdoc = anexoAdoc.getFilename();
							}
						}
						
						messages.info("Identificado o path: " + projectPath + " e o arquivo principal: " + documentoPrincipalNomeAdoc);

						// 4. cria o branch da issue atual - feature branch atual
						GitlabBranchResponse branchResponse = gitlabService.createFeatureBranch(gitlabProjectId, branchName);
						if(branchResponse != null && branchName.equals(branchResponse.getBranchName())) {
							messages.info("Feature branch: " + branchName + " criado no projeto: " + gitlabProjectId);
						}else {
							messages.error("Erro ao tentar criar o feature branch: " + branchName + " no projeto: " + gitlabProjectId);
						}

						if(!messages.hasSomeError()) {
							// 5. verifica se já existe um documento principal com o conteúdo atual
							// recupera o documento atual
							byte[] textFile = jiraService.getAttachmentContent(issue, documentoPrincipalNomeAdoc);
							String texto = new String(textFile);
							String adocFilePath = Utils.normalizePaths(pathSrcPreffix  + "/" + projectPath + "/" + documentoPrincipalNomeAdoc);
							
							// verifica se já existe o arquivo no destino e se o texto é diferente, caso contrário, mantem como está
							String conteudoArquivo = gitlabService.getRawFile(gitlabProjectId, adocFilePath, branchName);
							
							if(StringUtils.isBlank(conteudoArquivo) || (!Utils.compareAsciiIgnoreCase(conteudoArquivo, texto))) {
								String textoCommit = getCommitText(estruturaDocumentacao, JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES), versaoASerLancada);
								
								if(!messages.hasSomeError()) {
									String commitMessage = "[" + issue.getKey() + "] " + textoCommit;
									// criar o arquivo principal da documentacao no branch
									GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(gitlabProjectId, branchResponse, adocFilePath,
											texto, commitMessage);
									if(commitResponse != null) {
										messages.info("Criado o arquivo: " + adocFilePath + " no branch: " + branchName + " do projeto: " + gitlabProjectId);
										messages.debug(commitResponse.toString());
									}else {
										messages.error("Erro ao tentar criar o arquivo: " + adocFilePath + " no branch: " + branchName + " do projeto: " + gitlabProjectId);
									}
								}
							}else {
								messages.info("Arquivo: " + adocFilePath + " já existe no branch: " + branchName + " do projeto: " + gitlabProjectId);
							}
							
							if(!messages.hasSomeError()) {
								// 6. cria a pasta de anexos (assets) e adiciona todos os demais documentos da issue lá
								List<JiraIssueAttachment> demaisAnexos = recuperaDemaisAnexosNaoAdoc(issue);
								if(demaisAnexos != null && !demaisAnexos.isEmpty()) {
									String assetsPath = pathSrcPreffix  + "/" + projectPath + "/" + JiraService.DOCUMENTATION_ASSETS_DIR;
									List<GitlabCommitFileVO> files = new ArrayList<>();
									for (JiraIssueAttachment anexo : demaisAnexos) {
										String fileName = Utils.normalizePaths(assetsPath + "/" + anexo.getFilename());
										byte[] content = jiraService.getAttachmentContent(issue, anexo.getFilename());
										String contentBase64 = Utils.byteArrToBase64(content);
										Boolean isBase64 = true;
										GitlabCommitFileVO file = new GitlabCommitFileVO(fileName, contentBase64, isBase64);
										files.add(file);
									}
									String commitMessage = "[" + issue.getKey() + "] Adicionando anexos relacionados";
									GitlabCommitResponse commitResponse = gitlabService.sendFilesToBranch(gitlabProjectId, branchName, files, commitMessage);
									if(commitResponse != null) {
										messages.info("Enviados anexos ao branch: " + branchName + " do projeto: " + gitlabProjectId);
										messages.debug(commitResponse.toString());
									}else {
										messages.error("Erro ao tentar enviar anexos ao branch: " + branchName + " do projeto: " + gitlabProjectId);
									}
								}
							}
							
							if(!messages.hasSomeError()) {
								if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES)) {
									// * 7.1 adiciona o release na lista de releases concatenadas
									// * 7.2 adiciona o release na lista de releases com links separados									
									String documentoPrincipalNomeHtml = documentoPrincipalNomeAdoc.replace(JiraService.FILENAME_SUFFIX_ADOC, JiraService.FILENAME_SUFFIX_HTML);
									String projectPathSubdir = projectPath;
									if(projectPathSubdir.endsWith(RELEASE_NOTES_INCLUDES_DIR)) {
										projectPathSubdir = projectPathSubdir.replaceAll(RELEASE_NOTES_INCLUDES_DIR, "/");
									}
									if(!messages.hasSomeError()) {
										String pathReleaseNotesCompleto = Utils.normalizePaths(pathSrcPreffix  + "/" + projectPathSubdir + "/" + RELEASE_NOTES_RELEASE_COMPLETO);
										adicionaNovoArquivoReleaseNotesNoReleaseCompleto(issue, gitlabProjectId, branchName, documentoPrincipalNomeAdoc, pathReleaseNotesCompleto);
									}
									if(!messages.hasSomeError()) {
										String pathReleaseNotesLista = Utils.normalizePaths(pathSrcPreffix  + "/" + projectPathSubdir + "/" + RELEASE_NOTES_RELEASE_LISTA);
										
										adicionaNovoArquivoReleaseNotesNaListaDeReleases(issue, gitlabProjectId, branchName, documentoPrincipalNomeHtml,
												versaoASerLancada, pathReleaseNotesLista);
									}
									if(!messages.hasSomeError()) {
										String serverAddressTemplate = "https://SERVER-ADDRESS.COM.BR/";
										documentationURL = createDocumentationLink(serverAddressTemplate, documentoPrincipalNomeHtml, Utils.getPathFromFilePath(adocFilePath));
									}
									if(!messages.hasSomeError()) {
										String textoCommit = getCommitText(estruturaDocumentacao, JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES), versaoASerLancada);

										String mergeMessage = "[" + issue.getKey() + "] " + textoCommit;
										GitlabMRResponse mrResponse = null;
										String erro = null;
										try {
											mrResponse = gitlabService.openMergeRequestIntoBranchDefault(gitlabProjectId, branchName, mergeMessage);
										} catch (Exception e) {
											erro = e.getLocalizedMessage();
											messages.error("Houve um problema ao abrir o merge do branch: " + branchName + " do projeto: " + gitlabProjectId);
											if(StringUtils.isNotBlank(erro)) {
												messages.error(erro);
											}
										}
										if(mrResponse != null) {
											messages.info("MR " + mrResponse.getIid() + " aberto para o branch: " + branchName + " no projeto: " + gitlabProjectId);
											if(StringUtils.isBlank(MrsAbertosConfirmados)) {
												MrsAbertosConfirmados = mrResponse.getWebUrl();
											}else  if(!MrsAbertosConfirmados.contains(mrResponse.getWebUrl())) {
												MrsAbertosConfirmados += ", " + mrResponse.getWebUrl();
											}
											messages.debug(mrResponse.toString());
										}
									}
								}
							}
						}
					}
					JiraMarkdown jiraMarkdown = new JiraMarkdown();
					StringBuilder textoComentario = new StringBuilder(messages.getMessagesToJira());
					textoComentario.append(jiraMarkdown.newLine());
					textoComentario.append(jiraMarkdown.block("Caso se pretenda utilizar anexos (imagem ou outro formato) deve-se utilizar no arquivo principal de documentação .adoc, referências"
							+ " à pasta '" + JiraService.DOCUMENTATION_ASSETS_DIR + "', pois todos os documentos anexados a esta issue que não sejam .adoc serão enviados ao reposiorio na pasta"
							+ " '" + JiraService.DOCUMENTATION_ASSETS_DIR + "' no mesmo path do arquivo principal."));
					
					if(messages.hasSomeError()) {
						// indica que há pendências - encaminha ao demandante
						Map<String, Object> updateFields = new HashMap<>();
						jiraService.adicionarComentario(issue, textoComentario.toString(), updateFields);
						enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_INDICAR_IMPEDIMENTO);
					}else {
						// tramita automaticamente, enviando as mensagens nos comentários
						Map<String, Object> updateFields = new HashMap<>();
						// adiciona a URL relacionada
						jiraService.atualizarURLPublicacao(issue, documentationURL, updateFields);
						// adiciona o nome do branch relacionado
						jiraService.atualizarBranchRelacionado(issue, branchName, updateFields, false);
						// adiciona o MR aberto
						jiraService.atualizarMRsAbertos(issue, MrsAbertosConfirmados, updateFields, true);
						
						jiraService.adicionarComentario(issue, textoComentario.toString(), updateFields);
						
						enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_ENVIAR_PARA_HOMOLOGACAO);
					}
				}
				
			}
		}
	}
	
	private JiraIssueAttachment recuperaAnexoDocumentoPrincipalAdoc(JiraIssue issue) {
		JiraIssueAttachment documentoAdoc = null;
		if(issue.getFields().getAttachment() != null && !issue.getFields().getAttachment().isEmpty()) {
			for (JiraIssueAttachment anexo : issue.getFields().getAttachment()) {
				if(anexo.getFilename().endsWith(JiraService.FILENAME_SUFFIX_ADOC)) {
					documentoAdoc = anexo;
					break;
				}
			}
		}
		
		return documentoAdoc;
	}
	
	private List<JiraIssueAttachment> recuperaDemaisAnexosNaoAdoc(JiraIssue issue) {
		List<JiraIssueAttachment> outrosAnexos = new ArrayList<>();
		if(issue.getFields().getAttachment() != null && !issue.getFields().getAttachment().isEmpty()) {
			for (JiraIssueAttachment anexo : issue.getFields().getAttachment()) {
				if(!anexo.getFilename().endsWith(JiraService.FILENAME_SUFFIX_ADOC)) {
					outrosAnexos.add(anexo);
				}
			}
		}
		
		return outrosAnexos;
	}

	private String getCommitText(JiraEstruturaDocumentacaoVO estruturaDocumentacao, boolean isReleaseNotes, String versao) {
		String nomeExibicaoDocumentacao = estruturaDocumentacao.getCategoriaNomeParaExibicao();
		if(StringUtils.isNotBlank(estruturaDocumentacao.getSubCategoriaNomeParaExibicao())) {
			nomeExibicaoDocumentacao = nomeExibicaoDocumentacao + " - " + estruturaDocumentacao.getSubCategoriaNomeParaExibicao();
		}
		String textoCommit = "Gerando documentacao para " + nomeExibicaoDocumentacao;
		if(isReleaseNotes) {
			textoCommit = "Gerando release notes para " + nomeExibicaoDocumentacao;
			textoCommit = textoCommit + " da versão: "+ versao;
		}
		
		return textoCommit;
	}

	
	
	private static final String PATHNAME_SRC_DOCUMENTACAO = "src/main/asciidoc/";
	private static final String RELEASE_NOTES_DIR = "/release-notes/";
	private static final String RELEASE_NOTES_INCLUDES_DIR = "/includes/";
	private static final String RELEASE_NOTES_RELEASE_COMPLETO = "release-notes-completo.adoc";
	private static final String RELEASE_NOTES_RELEASE_LISTA = "index.adoc";

	private String createDocumentationLink(String serverAddress, String htmlFileName, String path) {
		String pathHtml = path.replace(PATHNAME_SRC_DOCUMENTACAO, "");
		return serverAddress + Utils.normalizePaths("/" + pathHtml + "/" + htmlFileName);
	}

	/**
	 * Arquivo target: release-notes-completo.adoc <br/>
	 * Item a incluir: <br/>
	 * include::{docdir}/{docsServicePATH}/release-notes/includes/PARAM_RELEASE_NOTES_FILE.adoc[] <br/>
	 * Exemplo de inclusao: <br/>
	 * include::{docdir}/{docsServicePATH}/release-notes/includes/release-notes_2-1-8-0.adoc[] <br/>
	 * <br/>
	 * 
	 * @param issue
	 * @param gitlabProjectId
	 * @param branchName
	 * @param adocFileName
	 * @param pathReleaseNotesCompleto
	 */
	private void adicionaNovoArquivoReleaseNotesNoReleaseCompleto(JiraIssue issue, String gitlabProjectId,
			String branchName, String adocFileName, String pathReleaseNotesCompleto) {

		// 2. obter o arquivo de releases completo
		String releaseNotesCompletoContent = gitlabService.getRawFile(gitlabProjectId, pathReleaseNotesCompleto, branchName);

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
						gitlabProjectId, branchName, pathReleaseNotesCompleto,
						releaseNotesCompletoContent, commitMessage);
				if(commitResponse != null) {
					messages.info("Atualizado o arquivo: " + pathReleaseNotesCompleto + " no branch: " + branchName + " do projeto: " + gitlabProjectId);
					messages.debug(commitResponse.toString());
				}else {
					messages.error("Erro ao tentar atualizar o arquivo: " + pathReleaseNotesCompleto + " no branch: " + branchName + " do projeto: " + gitlabProjectId);
				}
			} else {
				messages.info("O include do release notes |" + adocFileName + "| já existe no arquivo de releases completas");
			}
		} else {
			messages.error("Não foi possível encontrar o arquivo " + pathReleaseNotesCompleto + " no projeto " + gitlabProjectId);
		}
	}

	/**
	 * Arquivo target: index.adoc <br/>
	 * Item a incluir: <br/>
	 * 		=== link:includes/PARAM_RELEASE_NOTES_FILE.html[vPARAM_NUMERO_VERSAO] <br/>
	 * Exemplo de inclusao: <br/>
	 * 		=== link:includes/release-notes_2-1-2-3.html[v2.1.2.3] <br/>
	 * <br/>
	 * @param project
	 * @param branch
	 * @param filePath
	 * @param version
	 * @param releaseDate
	 * @throws ParseException
	 */
	private void adicionaNovoArquivoReleaseNotesNaListaDeReleases(JiraIssue issue, String gitlabProjectId, 
			String branchName, String htmlFileName, String version, 
			String pathReleaseNotesLista) throws ParseException {

		// 2. obter o arquivo completo
		String listaReleaseNotesContent = gitlabService.getRawFile(gitlabProjectId, pathReleaseNotesLista, branchName);
		
		if (StringUtils.isNotBlank(listaReleaseNotesContent)) {
			// 3. verificar se a versão já está lá
			if (!listaReleaseNotesContent.contains(htmlFileName)) {
				// 5. adicionar o include do release
				if (StringUtils.isNotBlank(version)) {
					String linhaIncludeReleaseNotes = "=== link:includes/" + htmlFileName + "[v" + version + "]";
					listaReleaseNotesContent += "\n" + linhaIncludeReleaseNotes + "\n";

					listaReleaseNotesContent = reordenarListaIncludesReleaseNotesHtmls(listaReleaseNotesContent);
					
					String commitMessage = "[" + issue.getKey() + "] Incluindo novo release notes no arquivo de lista de releases do projeto";
					GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(
							gitlabProjectId, branchName, pathReleaseNotesLista, listaReleaseNotesContent,
							commitMessage);
					
					if(commitResponse != null) {
						messages.info("Atualizado o arquivo: " + pathReleaseNotesLista + 
								" no branch: " + branchName + " do projeto: " + gitlabProjectId);
						messages.debug(commitResponse.toString());
					}else {
						messages.error("Erro ao tentar atualizar o arquivo: " + pathReleaseNotesLista + 
								" no branch: " + branchName + " do projeto: " + gitlabProjectId);
					}
				} else {
					messages.error("Não foi possível identificar a data da release");
				}
			} else {
				messages.info("O include do release notes |" + htmlFileName  + "| já existe no arquivo de lista de releases");
			}
		} else {
			messages.error(" Não foi possível encontrar o arquivo " + pathReleaseNotesLista
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
			String versionAStr = getVersionFromReleaseNotesInclude(a, JiraService.RELEASE_NOTES_FILENAME_PREFIX, JiraService.FILENAME_SUFFIX_ADOC);
			List<Integer> versionA = Utils.getVersionFromString(versionAStr, "-|\\.");
			String versionBStr = getVersionFromReleaseNotesInclude(b, JiraService.RELEASE_NOTES_FILENAME_PREFIX, JiraService.FILENAME_SUFFIX_ADOC);
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
			} else if (linha.toLowerCase().contains(("link:includes/" + JiraService.RELEASE_NOTES_FILENAME_PREFIX).toLowerCase())
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
			String versionAStr = getVersionFromReleaseNotesInclude(a, JiraService.RELEASE_NOTES_FILENAME_PREFIX, JiraService.FILENAME_SUFFIX_HTML);
			List<Integer> versionA = Utils.getVersionFromString(versionAStr, "-|\\.");
			String versionBStr = getVersionFromReleaseNotesInclude(b, JiraService.RELEASE_NOTES_FILENAME_PREFIX, JiraService.FILENAME_SUFFIX_HTML);
			List<Integer> versionB = Utils.getVersionFromString(versionBStr, "-|\\.");

			return Utils.compareVersionsDesc(versionA, versionB);
		}
	}

	private String getVersionFromReleaseNotesInclude(String include, String prefix, String suffix) {
		return include.replaceFirst(".*" + prefix, "").replaceFirst(suffix + ".*", "");
	}
	
	private String translateCategoryToPathName(String categoryName) {
		String pathName = null;
		if(StringUtils.isNotBlank(categoryName)) {
			String catNameNormalized = StringUtils.stripAccents(categoryName.toLowerCase());
			pathName = catNameNormalized.replaceAll(" ", "-");
		}

	    return pathName;
	}
	
	private String translateSubCategoryToPathName(String subCategoryName, String gitlabProjectId) {
		String fullPath = null;
		// recupera o arquivo index.adoc, localiza o nome da subcategoria e com isso identifica o pathname
		String filePath = PATHNAME_SRC_DOCUMENTACAO + "index.adoc";
		String conteudoArquivoIndex = gitlabService.getRawFileFromDefaultBranch(gitlabProjectId, filePath);

		if(StringUtils.isNotBlank(conteudoArquivoIndex)) {
			String[] linhas = conteudoArquivoIndex.split("\n");
			for (String linha : linhas) {
				if(linha.contains(subCategoryName)) {
					fullPath = Utils.getPathFromAsciidocLink(linha);
					break;
				}
			}
		}
		return fullPath;
	}
}