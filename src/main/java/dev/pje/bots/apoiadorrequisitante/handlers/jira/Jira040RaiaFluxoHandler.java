package dev.pje.bots.apoiadorrequisitante.handlers.jira;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueFieldOption;
import com.devplatform.model.jira.JiraProperty;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.custom.JiraCustomFieldOption;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Component
public class Jira040RaiaFluxoHandler extends Handler<JiraEventIssue>{
	private static final Logger logger = LoggerFactory.getLogger(Jira040RaiaFluxoHandler.class);
	
	@Value("${clients.gitlab.url}")
	private String gitlabUrl;
	
	@Value("${clients.jira.user}")
	private String jiraBotUser;
	
	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|JIRA||040||RAIA-FLUXO|";
	}
	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	/**
	 * :: identificar a raia do fluxo :: (geral)
	 * ->> classifica a situação atual da issue de acordo com a localização: demandante / triagem / negócios / desenvolvedor / fábrica / revisão (raia do fluxo)
	 * - basicamente deve ler a propriedade: FLUXO_RAIA_ID=<xxxx> em que o XXX é o ID da opção do campo
	 * -- se a propriedade estiver vazia no status, então a raia deverá ser removida
	 * - se houver alteração de raia, deve alterar o responsável pela issue
	 * -- se a raia for:
	 * --- Desenvolvedor >> deve-se mudar o responsável pela issue para o responsável pela codificação, se esse campo não tiver o nome do bot (desenvolvedor.anonimo)
	 * --- Fábrica de desenvolvimento >> deve-se mudar o responsável pela issue para a fábrica do desenvolvedor responsável, se não houver, para a fábrica do tribunal que abriu a issue
	 * --- Demandante >> deve-se mudar o responsável para o usuário que abriu a issue
	 * --- Triagem >> Equipe de triagem de demandas do PJe (triagem.pje)
	 * --- Grupo negocial >> Grupo negocial do PJe (negocios.pje)
	 * --- Grupo revisor técnico >> Grupo revisor técnico do PJe (revisao.tecnia.pje)
	 * --- Testes >> Equipe de testes do PJe (testes.pje)
	 * --- Documentação >> Equipe de documentação do PJe (documentacao.pje)
	 * --- Infra-estrutura >> Equipe de infra-estrutura e segurança do PJe (infra.seguranca.pje)
	 * --- Análise & design >> Equipe de análise e design do PJe (analise.design.pje)
	 * --- Default: Equipe de suporte ao PJe (suporte.pje)
	 */
	@Override
	public void handle(JiraEventIssue issueEvent) throws Exception {
		messages.clean();
		if(issueEvent != null && issueEvent.getIssue() != null && issueEvent.getIssue().getFields() != null 
				&& issueEvent.getIssue().getFields().getStatus() != null && issueEvent.getIssue().getFields().getStatus().getId() != null 
				&& issueEvent.getUser() != null) {
			
			JiraIssue issue = issueEvent.getIssue();
			issue = jiraService.recuperaIssueDetalhada(issue);
			if(issue == null) {
				return;
			}
			String statusId = issue.getFields().getStatus().getId().toString();
			JiraIssueFieldOption raiaFluxo = issue.getFields().getRaiaDoFluxo();
			String raiaAtualId = null;
			if(raiaFluxo != null) {
				raiaAtualId = raiaFluxo.getId().toString();
			}
			
			// identifica qual é a raia identificada para o status atual da issue
			JiraProperty propriedadeStatusRaiaFluxo 
				= jiraService.getIssueStatusProperty(issue.getKey(), statusId, JiraService.STATUS_PROPERTY_KEY_FLUXO_RAIA_ID);
			String novaRaiaId = null;
			if(propriedadeStatusRaiaFluxo != null && StringUtils.isNotBlank(propriedadeStatusRaiaFluxo.getValue())) {
				if(StringUtils.isNumeric(propriedadeStatusRaiaFluxo.getValue())) {
					novaRaiaId = propriedadeStatusRaiaFluxo.getValue();
				}
			}
			boolean alterarRaia = !Utils.compareAsciiIgnoreCase(raiaAtualId, novaRaiaId);
			if(alterarRaia || (JiraService.FLUXO_RAIA_ID_FABRICA_DESENVOLVIMENTO.equals(novaRaiaId) || JiraService.FLUXO_RAIA_ID_DESENVOLVEDOR.equals(novaRaiaId))) {
				// identifica qual deve ser o novo responsável pela issue
				/**
				 * --- Desenvolvedor >> deve-se mudar o responsável pela issue para o responsável pela codificação, se esse campo não tiver o nome do bot (desenvolvedor.anonimo)
				 * --- Fábrica de desenvolvimento >> deve-se mudar o responsável pela issue para a fábrica do desenvolvedor responsável, se não houver, para a fábrica do tribunal que abriu a issue
				 * --- Demandante >> deve-se mudar o responsável para o usuário que abriu a issue
				 * --- Triagem >> Equipe de triagem de demandas do PJe (triagem.pje)
				 * --- Grupo negocial >> Grupo negocial do PJe (negocios.pje)
				 * --- Análise & design >> Equipe de análise e design do PJe (analise.design.pje)
				 * --- Grupo revisor técnico >> Grupo revisor técnico do PJe (revisao.tecnia.pje)
				 * --- Testes >> Equipe de testes do PJe (testes.pje)
				 * --- Documentação >> Equipe de documentação do PJe (documentacao.pje)
				 * --- Infra-estrutura >> Equipe de infra-estrutura e segurança do PJe (infra.seguranca.pje)
				 * --- Automatizaçãp >> (bot-fluxo-pje)
				 * --- Default: Equipe de suporte ao PJe (suporte.pje)
				 */
				String usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_DEFAULT;
				String usernameResponsavelCodificacao = null;
				String grupoResponsavelAtribuicao = null;
				String fabricaDesenvolvimento = null;
				String transitionKey = JiraService.TRANSITION_PROPERTY_KEY_EDICAO_AVANCADA;
				if(StringUtils.isNotBlank(novaRaiaId)) {
					switch (novaRaiaId) {
					case JiraService.FLUXO_RAIA_ID_TRIAGEM:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_TRIAGEM;
						break;
					case JiraService.FLUXO_RAIA_ID_DEMANDANTE:
						// obtem o usuário demandante da issue
						JiraUser usuarioDemandante = issue.getFields().getReporter();
						if(usuarioDemandante != null && StringUtils.isNotBlank(usuarioDemandante.getName())) {
							if(usuarioDemandante.getActive()) {
								usernameResponsavel = usuarioDemandante.getName();
							}else {
								String siglaTribunal = jiraService.getTribunalUsuario(usuarioDemandante);
								// caso o usuário anteriormente demandante da issue tenha sido inativado - recupera o usuário do próprio tribunal
								JiraUser usuarioTribunal = jiraService.getUser(siglaTribunal, null);
								if(usuarioTribunal != null && usuarioTribunal.getActive()) {
									usernameResponsavel = usuarioTribunal.getName();
								}
							}
						}
						break;
					case JiraService.FLUXO_RAIA_ID_EQUIPE_NEGOCIAL:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_EQUIPE_NEGOCIAL;
						break;
					case JiraService.FLUXO_RAIA_ID_ANALISE_DESIGN:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_ANALISE_DESIGN;
						break;
					case JiraService.FLUXO_RAIA_ID_GRUPO_REVISOR_TECNICO:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_GRUPO_REVISOR_TECNICO;
						break;
					case JiraService.FLUXO_RAIA_ID_INFRA_SEGURANCA:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_INFRA_SEGURANCA;
						break;
					case JiraService.FLUXO_RAIA_ID_TESTES:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_TESTES;
						grupoResponsavelAtribuicao = JiraService.GRUPO_TESTES;
						break;
					case JiraService.FLUXO_RAIA_ID_DOCUMENTACAO:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_DOCUMENTACAO;
						grupoResponsavelAtribuicao = JiraService.GRUPO_TESTES;
						break;
					case JiraService.FLUXO_RAIA_ID_AUTOMATIZACAO:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_AUTOMATIZACAO;
						break;
					case JiraService.FLUXO_RAIA_ID_FABRICA_DESENVOLVIMENTO:
						/**
						 * - verifica se o usuário responsável é uma fábrica
						 * - se não for, verifica se o usuário responsável é um desenvolvedor
						 * -- neste último caso, a raia deve ser mudada para "desenvolvedor"
						 * - se o responsável não for nem fábrica e nem um desenvolvedor, verifica o campo: "responsável pela codificação" (se é desenvolvedor e não é um bot)
						 * -- neste caso, a raia deve ser mudada para "desenvolvedor"
						 */
						JiraUser usuarioResponsavelAtual = issue.getFields().getAssignee();
						String usuarioResponsavelRaiaFabrica = null;
						if(usuarioResponsavelAtual != null && StringUtils.isNotBlank(usuarioResponsavelAtual.getName()) && usuarioResponsavelAtual.getActive()) {
							if(jiraService.isFabricaDesenvolvimento(usuarioResponsavelAtual)) {
								usuarioResponsavelRaiaFabrica = usuarioResponsavelAtual.getName();
							}else if(jiraService.isDesenvolvedor(usuarioResponsavelAtual)) {
								usuarioResponsavelRaiaFabrica = usuarioResponsavelAtual.getName();
								novaRaiaId = JiraService.FLUXO_RAIA_ID_DESENVOLVEDOR;
							}
						}
						if(StringUtils.isBlank(usuarioResponsavelRaiaFabrica)) {
							JiraUser usuarioResponsavelCodificacao = issue.getFields().getResponsavelCodificacao();
							if(usuarioResponsavelCodificacao != null && usuarioResponsavelCodificacao.getActive() && jiraService.isDesenvolvedor(usuarioResponsavelCodificacao) && !jiraService.isServico(usuarioResponsavelCodificacao)) {
								usuarioResponsavelRaiaFabrica = usuarioResponsavelCodificacao.getName();
								novaRaiaId = JiraService.FLUXO_RAIA_ID_DESENVOLVEDOR;
							}
						}
						/**
						 * - se ainda não tiver identificado o usuário da fábrica:
						 * -- busca identificar no campo: "Fábrica de desenvolvimento" e a partir daí o usuário relacionado
						 * -- se não identificar nenhuma fábrica ainda, busca qual é a fábrica relacionada a algum tribunal requisitante da issue
						 */
						if(StringUtils.isBlank(usuarioResponsavelRaiaFabrica)) {
							if(issue.getFields().getFabricaDesenvolvimento() != null && StringUtils.isNotBlank(issue.getFields().getFabricaDesenvolvimento().getValue())) {
								fabricaDesenvolvimento = issue.getFields().getFabricaDesenvolvimento().getValue();
								JiraUser usuarioFabricaDesenvolvimento = jiraService.getUsuarioFabricaDesenvolvimentoDeSiglaTribunal(fabricaDesenvolvimento);
								if(usuarioFabricaDesenvolvimento != null && usuarioFabricaDesenvolvimento.getActive()) {
									usuarioResponsavelRaiaFabrica = usuarioFabricaDesenvolvimento.getName();
								}
							}
						}
						if(StringUtils.isBlank(usuarioResponsavelRaiaFabrica)) {
							if(issue.getFields().getTribunalRequisitante() != null) {
								for (JiraIssueFieldOption tribunalRequisitante : issue.getFields().getTribunalRequisitante()) {
									// recupera uma fábrica para o tribunal requisitante
									String siglaTribunalRequisitante = tribunalRequisitante.getValue();
									JiraCustomFieldOption fabricaDesenvolvimentoOption = jiraService.getFabricaDesenvolvimentoDeSiglaTribunal(siglaTribunalRequisitante);
									if(fabricaDesenvolvimentoOption != null && StringUtils.isNotBlank(fabricaDesenvolvimentoOption.getValue())) {
										fabricaDesenvolvimento = fabricaDesenvolvimentoOption.getValue();
									}
									// recupera o usuário da fábrica para ser o responsável pela issue
									JiraUser usuarioFabricaDesenvolvimento = jiraService.getUsuarioFabricaDesenvolvimentoDeSiglaTribunal(siglaTribunalRequisitante);
									if(usuarioFabricaDesenvolvimento != null && usuarioFabricaDesenvolvimento.getActive()) {
										usuarioResponsavelRaiaFabrica = usuarioFabricaDesenvolvimento.getName();
										break;
									}	
								}
							}
						}
						if(StringUtils.isNotBlank(usuarioResponsavelRaiaFabrica)) {
							// identifica qual é a fábrica de desenvolvimento relacionada + qual é o grupo responsável pela atribuição
							if(StringUtils.isBlank(fabricaDesenvolvimento)) {
								fabricaDesenvolvimento = jiraService.getNomeFabricaDesenvolvimentoDeUsuario(usuarioResponsavelRaiaFabrica);
							}
							if(StringUtils.isBlank(grupoResponsavelAtribuicao)) {
								grupoResponsavelAtribuicao = jiraService.getGrupoDesenvolvimentoDeUsuario(usuarioResponsavelRaiaFabrica);
							}
							// verifica se há responsável pela codificação - se não pertencer à mesma fábrica do responsável, então substitui o responsável pela codificação pelo "desenvolvedor.anonimo"
							JiraUser usuarioResponsavelCodificacao = issue.getFields().getResponsavelCodificacao();
							if(usuarioResponsavelCodificacao != null && usuarioResponsavelCodificacao.getActive() && jiraService.isDesenvolvedor(usuarioResponsavelCodificacao) && !jiraService.isServico(usuarioResponsavelCodificacao)) {
								String fabricaDesenvolvimentoUsuarioResponsavelCodificacao = jiraService.getNomeFabricaDesenvolvimentoDeUsuario(usuarioResponsavelCodificacao.getName());
								if(StringUtils.isNotBlank(fabricaDesenvolvimentoUsuarioResponsavelCodificacao) && StringUtils.isNotBlank(fabricaDesenvolvimento) &&
										!fabricaDesenvolvimento.equalsIgnoreCase(fabricaDesenvolvimentoUsuarioResponsavelCodificacao)) {
									usernameResponsavelCodificacao = JiraService.USUARIO_DESENVOLVEDOR_ANONIMO;
								}
							}
							usernameResponsavel = usuarioResponsavelRaiaFabrica;
						}
						break;
					case JiraService.FLUXO_RAIA_ID_DESENVOLVEDOR:
						/**
						 * - verifica se responsável pela issue é desenvolvedor:
						 * -- se for:
						 * --- identifica o responsável pela codificação como sendo o mesmo usuário
						 * -- se não for:
						 * --- verifica se há usuário responsável pela codificação
						 * ---- se houver:
						 * ----- identifica o usuário responsável pela issue como sendo a mesma pessoa
						 * -- se identificou algum desenvolvedor:
						 * --- a partir da identificação do usuário desenvolvedor, identifica a fábrica de desenvolvimento + grupo responsável pela atribuição
						 * -- mas se mesmo assim não identificar o desenvolvedor:
						 * --- tramita a issue para a situação de "Fábrica de desenvolvimento"
						 */
						JiraUser usuarioResponsavelRaiaDesenvolvedor = null;
						JiraUser usuarioResponsavelIssue = issue.getFields().getAssignee();
						if(usuarioResponsavelIssue != null && usuarioResponsavelIssue.getActive() && jiraService.isDesenvolvedor(usuarioResponsavelIssue)) {
							usuarioResponsavelRaiaDesenvolvedor = usuarioResponsavelIssue;
						}
						if(usuarioResponsavelRaiaDesenvolvedor == null) {
							JiraUser usuarioResponsavelCodificacao = issue.getFields().getResponsavelCodificacao();
							if(usuarioResponsavelCodificacao != null && usuarioResponsavelCodificacao.getActive() && jiraService.isDesenvolvedor(usuarioResponsavelCodificacao) && !jiraService.isServico(usuarioResponsavelCodificacao)) {
								usuarioResponsavelRaiaDesenvolvedor = usuarioResponsavelCodificacao;
							}
						}
						if(usuarioResponsavelRaiaDesenvolvedor != null) {
							// identifica qual é a fábrica de desenvolvimento relacionada + qual é o grupo responsável pela atribuição
							fabricaDesenvolvimento = jiraService.getNomeFabricaDesenvolvimentoDeUsuario(usuarioResponsavelRaiaDesenvolvedor.getName());
							grupoResponsavelAtribuicao = jiraService.getGrupoDesenvolvimentoDeUsuario(usuarioResponsavelRaiaDesenvolvedor.getName());

							usernameResponsavelCodificacao = usuarioResponsavelRaiaDesenvolvedor.getName();
							usernameResponsavel = usuarioResponsavelRaiaDesenvolvedor.getName();
							if(jiraService.isFabricaDesenvolvimento(usuarioResponsavelRaiaDesenvolvedor)) {
								// se o desenvolvedor identificado é na verdade uma fábrica, então identifica a transição para voltar à fábrica
								transitionKey = JiraService.TRANSITION_PROPERTY_KEY_FABRICA_DESENVOLVIMENTO;
							}
						}else {
							// se está na raia de desenvolvedor, mas não tem nenhum desenvolvedor identificado, encaminha a issue para a fábrica
							transitionKey = JiraService.TRANSITION_PROPERTY_KEY_FABRICA_DESENVOLVIMENTO;
						}
						break;
					default:
						usernameResponsavel = JiraService.FLUXO_RAIA_RESPONSAVEL_DEFAULT;
						break;
					}
				}
				Map<String, Object> updateFields = new HashMap<>();
				if(StringUtils.isNotBlank(usernameResponsavel)){
					JiraUser novoUsuarioResponsavel = jiraService.getUser(usernameResponsavel, null);
					if(novoUsuarioResponsavel != null && novoUsuarioResponsavel.getActive()) {
						jiraService.atualizarResponsavelIssue(issue, novoUsuarioResponsavel, updateFields);
					}
				}
				if(StringUtils.isNotBlank(usernameResponsavelCodificacao)){
					JiraUser novoUsuarioResponsavelCodificacao = jiraService.getUser(usernameResponsavelCodificacao, null);
					if(novoUsuarioResponsavelCodificacao != null && novoUsuarioResponsavelCodificacao.getActive()) {
						jiraService.atualizarResponsavelCodificacao(issue, novoUsuarioResponsavelCodificacao, updateFields);
					}
				}
				if(StringUtils.isNotBlank(fabricaDesenvolvimento)){
					jiraService.atualizarFabricaDesenvolvimento(issue, fabricaDesenvolvimento, updateFields);
				}
				jiraService.atualizarRaiaFluxo(issue, novaRaiaId, updateFields);
				jiraService.atualizarGrupoResponsavelAtribuicao(issue, grupoResponsavelAtribuicao, updateFields);
				if((updateFields == null || updateFields.isEmpty()) && JiraService.TRANSITION_PROPERTY_KEY_EDICAO_AVANCADA.equals(transitionKey)) {
					messages.info("Não há alterações a realizar");
				}else {
					if(!JiraService.TRANSITION_PROPERTY_KEY_EDICAO_AVANCADA.equals(transitionKey)) {
						updateFields = new HashMap<>();
					}
					enviarAlteracaoJira(issue, updateFields, null, transitionKey, true, false);
				}
			}
		}
	}
}