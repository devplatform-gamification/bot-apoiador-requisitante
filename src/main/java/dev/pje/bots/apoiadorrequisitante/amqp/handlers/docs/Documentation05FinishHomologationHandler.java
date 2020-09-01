package dev.pje.bots.apoiadorrequisitante.amqp.handlers.docs;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.vo.JiraEstruturaDocumentacaoVO;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class Documentation05FinishHomologationHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(Documentation05FinishHomologationHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	public String getMessagePrefix() {
		return "|DOCUMENTATION||05||FINISH-HOMOLOGATION|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	/**
	 * :: Homologado - aguardando publicacao :: (automática)
	 * Cria mais uma opcao na estrutura da documentacao com o nome dado na issue
	 * Altera a issue para essa nova opção
	 * Tramita para: "Aguardando fechamento da versão"
	 * 
	 */
	@Override
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			if(	(
					JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_DOCUMENTATION)
					|| JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_RELEASE_NOTES)
					) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_DOCUMENTATION_FINISH_HOMOLOGATION_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_DOCUMENTATION_FINISH_HOMOLOGATION_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());
				issue = jiraService.recuperaIssueDetalhada(issue.getKey());

				if(issue.getFields() != null && issue.getFields().getProject() != null
						&& JiraUtils.isIssueInStatus(issue, JiraService.STATUS_DOCUMENTATION_FINISH_HOMOLOGATION_ID)) {
					Map<String, Object> updateFields = new HashMap<>();

					if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_DOCUMENTATION)) {
						// verifica se informou o caminho da documentacao corretamente
						JiraEstruturaDocumentacaoVO estruturaDocumentacao = new JiraEstruturaDocumentacaoVO(
								issue.getFields().getEstruturaDocumentacao(), 
								issue.getFields().getNomeParaExibicaoDocumentacao(), issue.getFields().getPathDiretorioPrincipal());

						if(!estruturaDocumentacao.getEstruturaInformadaValida()) {
							if(estruturaDocumentacao.getCategoriaIndicadaOutros() || estruturaDocumentacao.getSubCategoriaIndicadaOutros()) {
								messages.error("Há um problema na identificação da estrutura de documentação, por favor, especifique a informação nos campos: 'nome para exibição da documentação' e 'path do diretório principal', essa informação será usada para a publicação da documentação.");
							}else {
								messages.error("Há um problema na identificação da estrutura de documentação, por favor, indique a informação completa corretamente, ela será usada para a publicação da documentação.");
							}
						}
						if(!messages.hasSomeError()) {
							if(estruturaDocumentacao.getCategoriaIndicadaOutros() || estruturaDocumentacao.getSubCategoriaIndicadaOutros()) {
								String issueCategoriaId = null;
								String issueSubCategoriaId = null;
								if(estruturaDocumentacao.getCategoriaIndicadaOutros()) {
									// FIXME a informação faltante é a categoria
									String nomeCategoria = estruturaDocumentacao.getCategoriaNomeParaExibicao();
									try {
										jiraService.createCategoriaEstruturaDocumentacao(nomeCategoria);
										messages.info("Criada a nova categoria de documentação: " + nomeCategoria);
									}catch (Exception e) {
										messages.error("Falhou ao tentar criar a nova categoria de documentação: " + nomeCategoria);
									}
								}else {
									// FIXME a informação faltante é a subcategoria
									issueCategoriaId = estruturaDocumentacao.getCategoriaId().toString();
									String nomeSubCategoria = estruturaDocumentacao.getSubCategoriaNomeParaExibicao();
									try {
										jiraService.createSubCategoriaEstruturaDocumentacao(nomeSubCategoria, issueCategoriaId);
										messages.info("Criada a nova sub-categoria de documentação: " + nomeSubCategoria);
									}catch (Exception e) {
										messages.error("Falhou ao tentar criar a nova categoria de documentação: " + nomeSubCategoria);
									}
									// TODO - cadastrar a subcategoria como cascading
								}
								// TODO - mudar os campos das issues para esses novos parâmetros
							}
						}
					}

					JiraMarkdown jiraMarkdown = new JiraMarkdown();
					StringBuilder textoComentario = new StringBuilder(messages.getMessagesToJira());
					textoComentario.append(jiraMarkdown.newLine());
					textoComentario.append(jiraMarkdown.block("O trabalho nesta issue foi finalizado, aguardando o lançamento da versão para encerrá-la."));

					jiraService.adicionarComentario(issue, textoComentario.toString(), updateFields);

					enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_SAIDA_PADRAO, true, true);
				}
			}
		}
	}

}