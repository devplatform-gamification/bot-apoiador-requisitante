package dev.pje.bots.apoiadorrequisitante.handlers.jira;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.ListItemsWithListOfStrings;
import com.devplatform.model.bot.ListItemsWithNumber;
import com.devplatform.model.jira.JiraEventChangelogItems;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.custom.JiraCustomField;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Component
public class Jira070RegistroAtividadesDemandasHandler extends Handler<JiraEventIssue>{
	private static final Logger logger = LoggerFactory.getLogger(Jira070RegistroAtividadesDemandasHandler.class);

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
		return "|JIRA||070||REGISTRO-ATIVIDADES|";
	}
	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	/**
	 * :: Registro de evolução das demandas - para gerar relatórios posteriores ::
	 *	>>>> levar em consideração o momento da alteração e não o momento de chagada da informação neste serviço de registro <<<<
	 * - atualizar campo: "Data atribuição responsável"
	 * 		>> quando alterar o responsável atual, indica a data atual neste campo
	 * - atualizar campo: "Tempo atribuição por responsável"
	 * 		>> campo JSON
	 * 		>> quando alterar o responsável atual, calcula o tempo (mins.) em que o responsável anterior chegou na issue e soma ao valor previamente existente
	 * - atualizar campo: "Data atribuição raia"
	 * 		>> quando alterar a raia atual, indica a data atual neste campo
	 * - campo: "Tempo atribuição por raia"
	 * 		>> campo JSON
	 * 		>> quando alterar a raia atual, calcula o tempo (mins.) em que a raia anterior chegou na issue e soma ao valor previamente existente
	 * - campo: "Responsáveis por raia"
	 * 		>> campo JSON
	 * 		>> quando alterar a raia atual, verifica qual foi o usuário que fez a ação e vincula o usuário à raia
	 * 
	 * 
	 * >> quando a raia da demanda for alterada, zerar os valores de:
	 * 		- "Data última verificação"
	 * 		- "Data próxima verificação" >> data que será definida com um intervalo X a partir da última verificação ou a data para ficar pronto mais 3 dias (o que tiver o maior valor)
	 * 		- "Data para ficar pronto" << verificar se deve-se manter o histórico para validação posterior
	 * 		- "Percentdone"
	 * 
	 * 
	 * - deve seguir em frente apenas em situações em que haja alteração de raias ou de responsável pela demanda
	 */
	@Override
	public void handle(JiraEventIssue issueEvent) throws Exception {
		messages.clean();
		if(issueEvent != null && issueEvent.getIssue() != null && issueEvent.getIssue().getFields() != null 
				&& issueEvent.getIssue().getFields().getStatus() != null && issueEvent.getIssue().getFields().getStatus().getId() != null 
				&& issueEvent.getUser() != null && issueEvent.getChangelog() != null && issueEvent.getChangelog().getItems() != null) {

			// verifica se a alteração foi nas raias ou no usuário responsável pela issue ou no responsável pela codificação
			JiraEventChangelogItems alteracaoResponsavel = null;
			JiraEventChangelogItems alteracaoRaia = null;
			for (JiraEventChangelogItems changedItems: issueEvent.getChangelog().getItems()) {
				if(changedItems.getField().equals(JiraService.FIELD_RESPONSAVEL_ISSUE)){
					alteracaoResponsavel = changedItems;
				}else if(changedItems.getFieldtype().equals("custom")){
					JiraCustomField customfieldRaiaFluxo = jiraService.getCustomFieldSummary(JiraService.FIELD_RAIA_FLUXO);
					if(customfieldRaiaFluxo != null && changedItems.getField().equals(customfieldRaiaFluxo.getFieldName())) {
						alteracaoRaia = changedItems;
					}
				}
			}
			if(alteracaoRaia != null || alteracaoResponsavel != null) {
				// obtem a data em que houve a aleracao
				Date dataAlteracao = Utils.getDateFromTimestamp(issueEvent.getTimestamp());
	
				if(dataAlteracao == null) {
					messages.error("Houve um erro ao tentar obter o horário do evento: " + issueEvent.getTimestamp());
					return;
				}
				JiraIssue issue = issueEvent.getIssue();
				issue = jiraService.recuperaIssueDetalhada(issue);
				if(issue == null) {
					return;
				}
	
				Map<String, Object> updateFields = new HashMap<>();
				String transitionKey = JiraService.TRANSITION_PROPERTY_KEY_EDICAO_AVANCADA;
				if(alteracaoResponsavel != null) {
					/*
					 * recupera a última data de atribuição existente e calcula o tempo entre essa última data e a alteração atual
					 * este será o tempo que deverá ser atribuído ao responsável anterior da issue
					 * - se não houver data da última atribuição (em caso de issue criada ou issue legada), não se deve calcular tempo com o responsável
					 */
					String dataAtribuicaoResponsavelAnteriorStr = issue.getFields().getDataAtribuicaoResponsavel();
					Date dataAtribuicaoResponsavelAnterior = null;
					if(StringUtils.isNotBlank(dataAtribuicaoResponsavelAnteriorStr)) {
						dataAtribuicaoResponsavelAnterior = Utils.getDateFromString(dataAtribuicaoResponsavelAnteriorStr);
					}
					JiraUser responsavelAnterior = recuperarResponsavelAnterior(alteracaoResponsavel);
					ListItemsWithNumber tempoAtribuicaoPorResponsavel = recuperaTempoAtribuicaoPorResponsavel(issue);
	
					if(responsavelAnterior != null) {
						if(dataAtribuicaoResponsavelAnterior != null) {
							String nomeUsuario = responsavelAnterior.getName();
							Integer tempoInicial = 0;
							if(tempoAtribuicaoPorResponsavel != null && tempoAtribuicaoPorResponsavel.getItemValue(nomeUsuario) != null) {
								tempoInicial = tempoAtribuicaoPorResponsavel.getItemValue(nomeUsuario);
							}
							Long tempoDesdeUltimaAlteracao = Utils.checkDifferenceInMinutesBetweenTwoDates(dataAlteracao, dataAtribuicaoResponsavelAnterior);
							Integer tempoTotal = tempoInicial + tempoDesdeUltimaAlteracao.intValue();
							
							if(tempoAtribuicaoPorResponsavel == null) {
								tempoAtribuicaoPorResponsavel = new ListItemsWithNumber();
							}
	
							tempoAtribuicaoPorResponsavel.addItems(nomeUsuario, tempoTotal);
							jiraService.atualizarTempoAtribuicaoPorResponsavel(issue, Utils.convertObjectToJson(tempoAtribuicaoPorResponsavel), updateFields);
						}
					}else {
						messages.error("Usuário responsável anterior não identificado");
					}
					
					// recupera a data da última alteração de responsável e está será a data de atribuição ao responsável atual
					String dataAtribuicaoResponsavel = Utils.dateToStringPattern(dataAlteracao, JiraService.JIRA_DATETIME_PATTERN);
					jiraService.atualizarDataAtribuicaoResponsavel(issue, dataAtribuicaoResponsavel, updateFields);
				}
	
				if(alteracaoRaia != null) {
					/*
					 * recupera a última data de atribuição existente e calcula o tempo entre essa última data e a alteração atual
					 * este será o tempo que deverá ser atribuído à raia anterior da issue
					 * - se não houver data da última atribuição (em caso de issue criada ou issue legada), não se deve calcular tempo com a raia
					 */
					String dataAtribuicaoRaiaAnteriorStr = issue.getFields().getDataAtribuicaoRaia();
					Date dataAtribuicaoRaiaAnterior = null;
					if(StringUtils.isNotBlank(dataAtribuicaoRaiaAnteriorStr)) {
						dataAtribuicaoRaiaAnterior = Utils.getDateFromString(dataAtribuicaoRaiaAnteriorStr);
					}
					
					String raiaAnteriorId = recuperarRaiaAnterior(alteracaoRaia);
					ListItemsWithNumber tempoAtribuicaoPorRaia = recuperaTempoAtribuicaoPorRaia(issue);
	
					if(StringUtils.isNotBlank(raiaAnteriorId)) {
						if(dataAtribuicaoRaiaAnterior != null) {
							Integer tempoInicial = 0;
							if(tempoAtribuicaoPorRaia != null && tempoAtribuicaoPorRaia.getItemValue(raiaAnteriorId) != null) {
								tempoInicial = tempoAtribuicaoPorRaia.getItemValue(raiaAnteriorId);
							}
							Long tempoDesdeUltimaAlteracao = Utils.checkDifferenceInMinutesBetweenTwoDates(dataAlteracao, dataAtribuicaoRaiaAnterior);
							Integer tempoTotal = tempoInicial + tempoDesdeUltimaAlteracao.intValue();
	
							if(tempoAtribuicaoPorRaia == null) {
								tempoAtribuicaoPorRaia = new ListItemsWithNumber();
							}
							tempoAtribuicaoPorRaia.addItems(raiaAnteriorId, tempoTotal);
							jiraService.atualizarTempoAtribuicaoPorRaia(issue, Utils.convertObjectToJson(tempoAtribuicaoPorRaia), updateFields);
						}
					}else {
						messages.error("Raia anterior não identificada");
					}
					
					// recupera a data da última alteração de raia e está será a data de atribuição à raia atual
					String dataAtribuicaoRaia = Utils.dateToStringPattern(dataAlteracao, JiraService.JIRA_DATETIME_PATTERN);
					jiraService.atualizarDataAtribuicaoRaia(issue, dataAtribuicaoRaia, updateFields);
				}
	
				/*
				 * 1. se houve alteraçao de responsável + alteracao de raia
				 * - armazena o responsável anterior na raia anterior
				 * 2. se houve alteracao de responsável - sem alteraçao de raia
				 * - armazena o responsável anterior na raia atual
				 * 3. sem alteração de responsável + alteraccao de raia
				 * - armazena o responsável atual na raia anterior
				 * 
				 * >> para registrar o responsável na raia, não pode duplicar o último responsável que já estava registrado
				 */
				JiraUser responsavelParaRegistrar = null;
				String raiaParaRegistrar = null;
				if(alteracaoResponsavel != null) {
					responsavelParaRegistrar = recuperarResponsavelAnterior(alteracaoResponsavel);
				}else {
					responsavelParaRegistrar = issue.getFields().getAssignee();
				}
				if(alteracaoRaia != null) {
					raiaParaRegistrar = recuperarRaiaAnterior(alteracaoRaia);
				}else {
					if(issue.getFields().getRaiaDoFluxo() != null) {
						raiaParaRegistrar = issue.getFields().getRaiaDoFluxo().getId().toString();
					}
				}
				if(responsavelParaRegistrar != null && StringUtils.isNotBlank(raiaParaRegistrar)) {
					ListItemsWithListOfStrings atribuicoesPorRaia = recuperaResponsaveisPorRaia(issue);
					if(atribuicoesPorRaia == null) {
						atribuicoesPorRaia = new ListItemsWithListOfStrings();
					}
					List<String> usuariosRaia = atribuicoesPorRaia.getItemValue(raiaParaRegistrar);
					if(usuariosRaia == null) {
						usuariosRaia = new ArrayList<>();
					}
					if(usuariosRaia.size() == 0 || !usuariosRaia.get(usuariosRaia.size()-1).equalsIgnoreCase(responsavelParaRegistrar.getName())) {
						usuariosRaia.add(responsavelParaRegistrar.getName());
						atribuicoesPorRaia.addItems(raiaParaRegistrar, usuariosRaia);
						
						/// adiciona no campo
						jiraService.atualizarResponsaveisPorRaia(issue, Utils.convertObjectToJson(atribuicoesPorRaia), updateFields);
					}
				}
				 /* >> quando a raia da demanda for alterada, zerar os valores de:
					 * 		- "Data última verificação"
					 * 		- "Data próxima verificação" >> data que será definida com um intervalo X a partir da última verificação ou a data para ficar pronto mais 3 dias (o que tiver o maior valor)
					 * 		- "Data para ficar pronto" << verificar se deve-se manter o histórico para validação posterior
					 * 		- "Percentdone"
					 */
				if(alteracaoRaia != null) {
					jiraService.atualizarDataUltimaVerificacao(issue, null, updateFields);
					jiraService.atualizarDataProximaVerificacao(issue, null, updateFields);
				}
				if(alteracaoResponsavel != null) {
					jiraService.atualizarDataParaFicarPronto(issue, null, updateFields);
					jiraService.atualizarPercentualDeConclusao(issue, 0f, updateFields);
				}
				
				if((updateFields == null || updateFields.isEmpty())) {
					messages.info("Não há alterações a realizar");
				}else {
					enviarAlteracaoJira(issue, updateFields, null, transitionKey, true, false);
				}
	
				/* 
				 * - atualizar campo: "Data atribuição responsável"
				 * 		>> quando alterar o responsável atual, indica a data atual neste campo
				 * - atualizar campo: "Tempo atribuição por responsável"
				 * 		>> campo JSON
				 * 		>> quando alterar o responsável atual, calcula o tempo (mins.) em que o responsável anterior chegou na issue e soma ao valor previamente existente
				 * - atualizar campo: "Data atribuição raia"
				 * 		>> quando alterar a raia atual, indica a data atual neste campo
				 * - campo: "Tempo atribuição por raia"
				 * 		>> campo JSON
				 * 		>> quando alterar a raia atual, calcula o tempo (mins.) em que a raia anterior chegou na issue e soma ao valor previamente existente
				 * - campo: "Responsáveis por raia"
				 * 		>> campo JSON
				 * 		>> quando alterar a raia atual, verifica qual foi o usuário que fez a ação e vincula o usuário à raia
				 */
	
	
				/**
				 * {
				 * 		dataAtribuicaoResponsavel
				 * 		tempoAtribuicaoPorResponsavel >>> Map<String, Integer>
				 * 		[
				 * 			jose: 5
				 * 			joao: 10
				 * 			joaquim: 29
				 * 			joca: 7
				 * 		]
				 * 		dataAtribuicaoRaia	>>> String
				 * 		tempoAtribuicaoPorRaia [] >>> Map<String, Integer>
				 * 		responsaveisPorRaia >>> Map<String, List<String>>
				 * 		[
				 * 			raiaA: [],
				 * 			raiaB: [],
				 * 			raiaC: [],
				 * 			raiaD: [],
				 * 			raiaX: [],
				 * 		]
				 * 
				 */
			}
		}
	}
	
	private JiraUser recuperarResponsavelAnterior(JiraEventChangelogItems alteracaoResponsavel) {
		JiraUser responsavelAnterior = null;
		if(alteracaoResponsavel != null) {
			String usuarioResponsavelAnteriorKey = alteracaoResponsavel.getFrom();
			responsavelAnterior = jiraService.findUserByUserName(usuarioResponsavelAnteriorKey);
			if(responsavelAnterior == null) {
				String usuarioResponsavelAnteriorName = alteracaoResponsavel.getFromString();
				JiraUser usuarioEncontrado = jiraService.findUserByUserName(usuarioResponsavelAnteriorName);
				if(usuarioEncontrado != null && usuarioEncontrado.getKey().equalsIgnoreCase(usuarioResponsavelAnteriorKey)) {
					responsavelAnterior = usuarioEncontrado;
				}
			}
		}
		return responsavelAnterior;
	}
	
	private String recuperarRaiaAnterior(JiraEventChangelogItems alteracaoRaia) {
		String raiaAnteriorId = null;
		if(alteracaoRaia != null) {
			raiaAnteriorId = alteracaoRaia.getFrom();
		}
		return raiaAnteriorId;
	}

	private ListItemsWithNumber recuperaTempoAtribuicaoPorResponsavel(JiraIssue issue) {
		ListItemsWithNumber tempoAtribuicaoPorResponsavel = null;
		String tempoAtribuicaoPorResponsavelStr = issue.getFields().getTempoAtribuicaoPorResponsavel();
		if(StringUtils.isNotBlank(tempoAtribuicaoPorResponsavelStr)) {
			try {
				tempoAtribuicaoPorResponsavel = Utils.convertJsonToObject(tempoAtribuicaoPorResponsavelStr, ListItemsWithNumber.class);
			}catch (Exception e) {
			}
		}
		return tempoAtribuicaoPorResponsavel;
	}

	private ListItemsWithNumber recuperaTempoAtribuicaoPorRaia(JiraIssue issue) {
		ListItemsWithNumber tempoAtribuicaoPorRaia = null;
		String tempoAtribuicaoPorRaiaStr = issue.getFields().getTempoAtribuicaoPorRaia();
		if(StringUtils.isNotBlank(tempoAtribuicaoPorRaiaStr)) {
			try {
				tempoAtribuicaoPorRaia = Utils.convertJsonToObject(tempoAtribuicaoPorRaiaStr, ListItemsWithNumber.class);
			}catch (Exception e) {
			}
		}
		return tempoAtribuicaoPorRaia;
	}

	private ListItemsWithListOfStrings recuperaResponsaveisPorRaia(JiraIssue issue) {
		ListItemsWithListOfStrings responsaveisPorRaia = null;
		String responsaveisPorRaiaStr = issue.getFields().getResponsavelPorRaia();
		if(StringUtils.isNotBlank(responsaveisPorRaiaStr)) {
			try {
				responsaveisPorRaia = Utils.convertJsonToObject(responsaveisPorRaiaStr, ListItemsWithListOfStrings.class);
			}catch (Exception e) {
			}
		}
		return responsaveisPorRaia;
	}
}