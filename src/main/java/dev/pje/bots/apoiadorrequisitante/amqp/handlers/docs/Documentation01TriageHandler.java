package dev.pje.bots.apoiadorrequisitante.amqp.handlers.docs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueAttachment;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.event.JiraEventIssue.IssueEventTypeNameEnum;
import com.devplatform.model.jira.vo.JiraEstruturaDocumentacaoVO;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class Documentation01TriageHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(Documentation01TriageHandler.class);
	
	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|DOCUMENTATION||01||TRIAGE|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	private static final String TRANSITION_ID_INDICAR_PENDENCIAS = "201"; // TODO buscar por propriedade da transicao
	private static final String TRANSITION_ID_CONFIRMAR_TRIAGEM = "11"; // TODO buscar por propriedade da transicao
	
	/**
	 * Este handler verifica:
	 * - se a estrutura de documentação está completa
	 * -- se tiver selecionado outro (especificar), verificar quando nao for do tipo "release notes" se foram especificados: Título para exibição e Path diretório principal
	 * - Para tipo de issue release notes, verifiar:
	 * -- se há uma Versao a ser lancada:
	 * -- se uma documentacao com as mesmas características: estrutura documentacao + numero da versao já não foi lancada
	 * - se foi solicitado Publicar documentação automaticamente? e que no caso o usuário possui permissao para isso, caso nao tenha permissao, 
	 * 		apenas ignora, sem gerar erro, lançando a informação como comentário e alterando o valor
	 * - se foram indicados anexos, pelo menos 1 anexo com a extensao .adoc deve existir - caso contrário gera erro para o usuario
	 * + Lancar comentario na issue indicando que se a pessoa pretende utilizar algum anexo (imagem ou outro) deve utilizar 
	 * 		como nome de pasta o nome "assets" e todos os demais documentos anexados que não sejam .adoc serao enviados ao repositorio na pasta "assets" na mesma  path dos demais arquivos
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
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_OPEN_ID) &&
					(JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_OPEN_ID) 
						|| jiraEventIssue.getIssueEventTypeName().equals(IssueEventTypeNameEnum.ISSUE_CREATED)
						|| jiraEventIssue.getIssueEventTypeName().equals(IssueEventTypeNameEnum.ISSUE_UPDATED))) {

				JiraIssue issueDetalhada = jiraService.recuperaIssueDetalhada(issue);
				if(issueDetalhada != null && JiraUtils.isIssueInStatus(issueDetalhada, JiraService.STATUS_OPEN_ID)) {
					messages.setId(issue.getKey());
					messages.debug(jiraEventIssue.getIssueEventTypeName().name());
					
					// verifica se informou o caminho da documentacao corretamente
					JiraEstruturaDocumentacaoVO estruturaDocumentacao = new JiraEstruturaDocumentacaoVO(
								issue.getFields().getEstruturaDocumentacao(), 
								issue.getFields().getNomeParaExibicaoDocumentacao(), issue.getFields().getPathDiretorioPrincipal());

					if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES) && estruturaDocumentacao.getEstruturaInformadaManualmente()) {
						messages.error("Para demandas de documentação de release notes, deve-se selecionar as opções de estrutura de documentação atualmente existentes (e não 'Outros')");
					}else {
						if(estruturaDocumentacao.getEstruturaInformadaValida()) {
							messages.info("A estrutura de documentação informada é válida.");
						}else {
							if(estruturaDocumentacao.getCategoriaIndicadaOutros() || estruturaDocumentacao.getSubCategoriaIndicadaOutros()) {
								messages.error("Há um problema na identificação da estrutura de documentação, por favor, especifique a informação nos campos: 'nome para exibição da documentação' e 'path do diretório principal', essa informação será usada para a publicação da documentação.");
							}else {
								messages.error("Há um problema na identificação da estrutura de documentação, por favor, indique a informação completa corretamente, ela será usada para a publicação da documentação.");
							}
						}
					}

					if(!messages.hasSomeError()) {
						 /* - Para tipo de issue release notes, verifiar:
						 * -- se há uma Versao a ser lancada:
						 * -- se uma documentacao com as mesmas características: estrutura documentacao + numero da versao já não foi lancada
						 * */
						if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES)) {
							String versaoASerLancada = issue.getFields().getVersaoSeraLancada();
							if(StringUtils.isBlank(versaoASerLancada)){
								messages.error("A indicação da versão a ser lançada é obrigatória, indique apenas uma versão.");
							}else {
								// verifica se já há uma issue do mesmo tipo com as mesmas características: estrutura documentacao + numero da versao já não foi lancada
								String opcaoId = null;
								if(estruturaDocumentacao.getSubCategoriaId() != null) {
									opcaoId = estruturaDocumentacao.getSubCategoriaId().toString();
									String jql = jiraService.getJqlIssuesDocumentacaoReleaseNotes(versaoASerLancada, opcaoId);
									List<JiraIssue> issuesDuplicadas = jiraService.getIssuesFromJql(jql);
									
									if(issuesDuplicadas != null && !issuesDuplicadas.isEmpty() && issuesDuplicadas.size() > 1 ) {
										if(issuesDuplicadas.size() > 2) {
											messages.error("Esta issue está duplicada, por favor, verifique as outras issues relacionadas.");
										}else {
											messages.error("Esta issue está duplicada, por favor, verifique a outra issue relacionada.");
										}
										
										// registra as issues duplicadas
										for (JiraIssue issueDuplicada : issuesDuplicadas) {
											if(!issueDuplicada.getKey().equalsIgnoreCase(issue.getKey())) {
												Map<String, Object> updateFields = new HashMap<>();
												jiraService.criarNovoLink(issue, issueDuplicada.getKey(), 
														JiraService.ISSUELINKTYPE_DUPLICATES_ID.toString(), JiraService.ISSUELINKTYPE_DUPLICATES_OUTWARDNAME, false, updateFields);
												if(updateFields != null && !updateFields.isEmpty()) {
													enviarAlteracaoJira(issue, updateFields, null, false, false);
												}
											}
										}
									}
								}else {
									messages.error("Há um problema na identifiacação da subestrutura de documentação.");
								}
							}
						}
					}
					
					boolean publicarDocumentacaoAutomaticamente = false;
					String branchName = null;
					if(!messages.hasSomeError()) {
						 /* - se foi solicitado Publicar documentação automaticamente? e que no caso o usuário possui permissao para isso, caso nao tenha permissao, 
						 * 		apenas ignora, sem gerar erro, lançando a informação como comentário e alterando o valor
						 * */
						if(issue.getFields().getPublicarDocumentacaoAutomaticamente() != null 
								&& !issue.getFields().getPublicarDocumentacaoAutomaticamente().isEmpty()) {
							
							if(issue.getFields().getPublicarDocumentacaoAutomaticamente().get(0).getValue().equalsIgnoreCase("Sim")) {
								publicarDocumentacaoAutomaticamente = true;
							}
						}
						if(publicarDocumentacaoAutomaticamente && !jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
							publicarDocumentacaoAutomaticamente = false;
							messages.info("Apesar da indicação, o usuário atual não tem permissão para publicar documentações automaticamente sem homologação prévia, removendo a seleção dessa opção.");
						}

						/**
						 * verifica se há branch relacionado, se houver, não valida os anexos e utilizará o branch como referência
						 */
						branchName = recuperarBranchRelacionado(issue);

						/**
						 * valida anexos:
						 * - se foram indicados anexos, pelo menos 1 anexo com a extensao .adoc deve existir - caso contrário gera erro para o usuario
						 */
						JiraIssueAttachment anexoAdoc = recuperaAnexoDocumentoPrincipalAdoc(issue);
						if (anexoAdoc != null){
							messages.info("Encontrado o anexo com a documentação no formato: "+ JiraService.FILENAME_SUFFIX_ADOC);
						}else {
							if(StringUtils.isBlank(branchName)) {
								messages.error("Deve haver um e apenas um arquivo anexo com a extensão: " + JiraService.FILENAME_SUFFIX_ADOC 
										+ " - ele será utilizado como documento principal da documentação criada.\n"
										+ "Alternativamente pode-se indicar um branch com código da implementação manual.");
								
							}
						}
					}

					JiraMarkdown jiraMarkdown = new JiraMarkdown();
					StringBuilder textoComentario = new StringBuilder(messages.getMessagesToJira());
					if(StringUtils.isBlank(branchName)) {
						textoComentario.append(jiraMarkdown.newLine());
						textoComentario.append(jiraMarkdown.block("Caso se pretenda utilizar anexos (imagem ou outro formato) deve-se utilizar no arquivo principal de documentação .adoc, referências"
								+ " à pasta '" + JiraService.DOCUMENTATION_ASSETS_DIR + "', pois todos os documentos anexados a esta issue que não sejam .adoc serão enviados ao repositório na pasta"
								+ " '" + JiraService.DOCUMENTATION_ASSETS_DIR + "' no mesmo path do arquivo principal."));
					}

					if(messages.hasSomeError()) {
						// indica que há pendências - encaminha ao demandante
						Map<String, Object> updateFields = new HashMap<>();
						jiraService.adicionarComentario(issue, textoComentario.toString(), updateFields);
						enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_INDICAR_PENDENCIAS, true, true);
					}else {
						// tramita automaticamente, enviando as mensagens nos comentários
						Map<String, Object> updateFields = new HashMap<>();
						// atualizar campo: publicarDocumentacaoAutomaticamente
						if(!publicarDocumentacaoAutomaticamente) {
							jiraService.removerPublicarDocumentacaoAutomaticamente(issue, updateFields);
						}
						jiraService.adicionarComentario(issue, textoComentario.toString(), updateFields);
						enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_CONFIRMAR_TRIAGEM, true, true);
					}
				}		
			}
		}
	}
		
    private String recuperarBranchRelacionado(JiraIssue issue) {
    	String branchName = null;
		String branchesRelacionados = issue.getFields().getBranchesRelacionados();
		if(StringUtils.isNotBlank(branchesRelacionados)) {
			String[] branches = branchesRelacionados.split(",");
			if(branches.length > 0 && StringUtils.isNotBlank(branches[0])) {
				branchName = branches[0].trim();
			}
		}
		return branchName;
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
}