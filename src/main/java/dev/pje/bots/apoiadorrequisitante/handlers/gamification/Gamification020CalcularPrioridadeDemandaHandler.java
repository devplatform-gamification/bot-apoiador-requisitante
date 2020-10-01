package dev.pje.bots.apoiadorrequisitante.handlers.gamification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.areasConhecimento.NivelClassificacaoAreasConhecimentoEnum;
import com.devplatform.model.bot.prioridadeDemanda.ResultadoCriterioPriorizacao;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueFieldOption;
import com.devplatform.model.jira.JiraIssueFieldsPriority;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.custom.JiraCustomField;
import com.devplatform.model.jira.custom.JiraCustomFieldOption;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.request.JiraIssueLinkRequest;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.ResultadoCriteriosPriorizacaoDemandaTextModel;

@Component
public class Gamification020CalcularPrioridadeDemandaHandler extends Handler<JiraEventIssue>{
	private static final Logger logger = LoggerFactory.getLogger(Gamification020CalcularPrioridadeDemandaHandler.class);
	
	@Override
	protected Logger getLogger() {
		return logger;
	}

	@Override
	public String getMessagePrefix() {
		return "|GAMIFICATION||020||PRIORIDADES-DEMANDAS|";
	}
	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	@Autowired
	private ResultadoCriteriosPriorizacaoDemandaTextModel priorizacaoDemandaTextModel;
	
	private String getDataFinalizacaoProcesso() {
		String dataFinalizacaoProcesso = null;
		SimpleDateFormat sdf = new SimpleDateFormat(JiraService.JIRA_DATETIME_PATTERN);
		Date now = new Date();
		dataFinalizacaoProcesso = sdf.format(now);

		return dataFinalizacaoProcesso;
	}

	/**
	 * :: criar bot para gerar a pontuação de business value automaticamente para as issues ::
	 * >> execução quando a issue for alterada <<
	 * -- Haverá cálculo/re-calculo da pontuação das issues quando houver criação ou alteração da demanda e:
	 * --- ainda não houver valor no campo: BV ou seu valor for <=0
	 * --- OU a demanda NÃO estiver nas situações: (criar um campo de controle com estas raias) 
	 * ---- JiraService.FLUXO_RAIA_ID_TRIAGEM
	 * ---- JiraService.FLUXO_RAIA_ID_DEMANDANTE
	 * ---- JiraService.FLUXO_RAIA_ID_DOCUMENTACAO
	 * ---- JiraService.FLUXO_RAIA_ID_TESTES
	 * ---- JiraService.FLUXO_RAIA_ID_GRUPO_REVISOR_TECNICO
	 * ---- JiraService.FLUXO_RAIA_ID_FINALIZADA
	 * 
	 * ::: CRITÉRIOS
	 * 1. TEMPO DE VIDA DA ISSUE (quanto mais antiga menor a pontuação - busca-se incentivar a resolução o mais breve possível
	 * -- 24horas +54
	 * -- 24-72 horas +27
	 * -- 72hs até 7 dias +16
	 * -- 7-30 dias +9
	 * -- 30-100 dias +5
	 * -- 100-365 dias +3
	 * -- 1-2 anos +1
	 * -- >2 anos +0
	 * 
	 * 2. TIPO DE ISSUE (a resolução/correção de problemas deve ser incentivada)
	 * --- hotfix +35
	 * --- bugfix release +15
	 * --- defeito +10
	 * --- dúvidas/questões +5
	 * --- melhoria +5
	 * --- nova funcionalidade +3
	 * --- outros - +0
	 * 
	 * 3. ÁREA DE CONHECIMENTO (por ordem decrescente de número de issues pendentes) - o total de issues pendentes por área de conhecimento deve ser calculado 1 vez por mês 
	 * e armazenado no campo ctrlAreasConhecimento
	 * --- nível1 - áreas com até 20% do total: +45
	 * --- nível2 - áreas entre 20% e 45% do total: +30
	 * --- nível3 - áreas entre 45% e 75%: +12
	 * --- nível4 - áreas entre 75% e 90%: +5
	 * --- nível5 - áreas acima de 90%: +1
	 * **Nos casos de issues com mais de uma área de conhecimento, os valores são somados
	 * 
	 * 4. INDICAÇÃO DE PEDIDO DE PRIORIDADE FEITA PELO DEMANDANTE
	 * -- indicação de prioridade feita pelo demandante:
	 * --- 00 - sistema fora do ar: +20 (a issue deve ser identificada como um hotfix ou bugfix)
	 * --- 01 - crítico: +11
	 * --- 03 - normal: +3
	 * --- 04 - menor: +0
	 * 
	 * 5. TRIBUNAIS REQUISITANTES
	 * -- quantidade de tribunais requisitantes: +4 por tribunal
	 * **bolar um esquema qualitativo para essa pontuação, possibilitando contabilizar mais pontos para os tribunais de acordo com a utilização da versão nacional, mais pontos para os tribunais com versões mais atualizadas
	 * ***tem que ver se da pra gerar esse tipo de coisa automaticamente
	 * 
	 * 6. DEMANDAS RELACIONADAS
	 * -- número de links para outras issues: +2 por link (verificar se há mais de um link para a mesma issue)
	 * TODO ** só são contabilizadas demandas de tribunais solicitantes diferentes
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
			
			if(demandaEmRaiaCalculavel(issue)) {
				List<ResultadoCriterioPriorizacao> resultadosCriterios = new ArrayList<>();
				resultadosCriterios.add(calculaCriterioTempoDeVida(issue));
				resultadosCriterios.add(calculaCriterioTipoIssue(issue));
				resultadosCriterios.add(calculaCriterioClassificacaoAreaConhecimento(issue));
				resultadosCriterios.add(calculaCriterioPedidoPrioridade(issue));
				resultadosCriterios.add(calculaCriterioTribunalRequisitante(issue));
				resultadosCriterios.add(calculaCriterioLigacaoEntreDemandas(issue));
				
				// calcula o valor total dos critérios acima
				Integer prioridadeTodosCriterios = 0;
				for (ResultadoCriterioPriorizacao resultadoCriterio : resultadosCriterios) {
					if(resultadoCriterio.getPontuacao() != null) {
						prioridadeTodosCriterios += resultadoCriterio.getPontuacao();
					}
				}
				// commpara o valor encontrado com o valor do BV:
				// -- se não tiver mudado o valor global da priorização - não faz alterações
				// -- se tiver mudado o valor global da priorização - lança o comentário e altera o valor do BV
				Integer prioridadeAtual = issue.getFields().getBusinessValue();
				if(!prioridadeTodosCriterios.equals(prioridadeAtual)) {
					priorizacaoDemandaTextModel.setDataAvaliacao(getDataFinalizacaoProcesso());
					priorizacaoDemandaTextModel.setPontuacaoTotal(prioridadeTodosCriterios);
					priorizacaoDemandaTextModel.setResultadosCriterios(resultadosCriterios);
					
					JiraMarkdown jiraMarkdown = new JiraMarkdown();
					String jiraPriorizacaoDemandaText = priorizacaoDemandaTextModel.convert(jiraMarkdown);
					
					messages.info("Prioridade total: " + prioridadeTodosCriterios);
					
					String transitionKey = JiraService.TRANSITION_PROPERTY_KEY_EDICAO_AVANCADA;
					Map<String, Object> updateFields = new HashMap<>();
					jiraService.atualizarBussinessValue(issue, prioridadeTodosCriterios, updateFields);
					
					if((updateFields == null || updateFields.isEmpty())) {
						messages.info("Não há alterações a realizar");
					}else {
						jiraService.adicionarComentario(issue, jiraPriorizacaoDemandaText, updateFields);
						enviarAlteracaoJira(issue, updateFields, null, transitionKey, false, false);
					}
				}else {
					messages.info("A Prioridade total não foi alterada: " + prioridadeTodosCriterios);
				}
			}
		}
	}

	/**
	 * 	 * --- OU a demanda NÃO estiver nas situações: (criar um campo de controle com estas raias) 
	 * ---- JiraService.FLUXO_RAIA_ID_TRIAGEM
	 * ---- JiraService.FLUXO_RAIA_ID_DEMANDANTE
	 * ---- JiraService.FLUXO_RAIA_ID_DOCUMENTACAO
	 * ---- JiraService.FLUXO_RAIA_ID_TESTES
	 * ---- JiraService.FLUXO_RAIA_ID_GRUPO_REVISOR_TECNICO
	 * ---- JiraService.FLUXO_RAIA_ID_FINALIZADA
	 * 
	 * ** se não houver raia, será FALSE (pois pode ser que ainda não tenha conseguido identificar a raia)
	 * ** se houver raia, o default será TRUE
	 * @return
	 */
	private Boolean demandaEmRaiaCalculavel(JiraIssue issue) {
		JiraIssueFieldOption raiaFluxo = issue.getFields().getRaiaDoFluxo();
		String raiaAtualId = null;
		if(raiaFluxo != null) {
			raiaAtualId = raiaFluxo.getId().toString();
		}
		Boolean demandaEmRaiaCalculavel = (StringUtils.isNotBlank(raiaAtualId));
		if(StringUtils.isNotBlank(raiaAtualId)) {
			// não se calcula em nenhuma hipótese
			List<String> raiasNaoCalculaveis = new ArrayList<>();
			raiasNaoCalculaveis.add(JiraService.FLUXO_RAIA_ID_TRIAGEM);
			raiasNaoCalculaveis.add(JiraService.FLUXO_RAIA_ID_DEMANDANTE);
			raiasNaoCalculaveis.add(JiraService.FLUXO_RAIA_ID_AUTOMATIZACAO);
			raiasNaoCalculaveis.add(JiraService.FLUXO_RAIA_ID_FINALIZADA);
			
			if(raiasNaoCalculaveis.contains(raiaAtualId)) {
				demandaEmRaiaCalculavel = false;
			}else {
				// calcula-se apenas se na issue não houver nenhum valor ainda
				List<String> raiasCalculoOpcional = new ArrayList<>();
				raiasCalculoOpcional.add(JiraService.FLUXO_RAIA_ID_DOCUMENTACAO);
				raiasCalculoOpcional.add(JiraService.FLUXO_RAIA_ID_TESTES);
				raiasCalculoOpcional.add(JiraService.FLUXO_RAIA_ID_GRUPO_REVISOR_TECNICO);

				if(raiasCalculoOpcional.contains(raiaAtualId)) {
					Integer prioridadeAtual = issue.getFields().getBusinessValue();
					if(prioridadeAtual != null && prioridadeAtual > 5) {
						messages.info("não deve recalcular pois a prioridade atual é: " + prioridadeAtual);
						demandaEmRaiaCalculavel = false;
					}
				}
			}
		}
		return demandaEmRaiaCalculavel;
	}
	
	/**
	 * 	 * 1. TEMPO DE VIDA DA ISSUE (quanto mais antiga menor a pontuação - busca-se incentivar a resolução o mais breve possível
	 * -- 24horas +54
	 * -- 24-72 horas +27
	 * -- 72hs até 7 dias +16
	 * -- 7-30 dias +9
	 * -- 30-100 dias +5
	 * -- 100-365 dias +3
	 * -- 1-2 anos +1
	 * -- > 2 anos 0
	 * 
	 * @return dias
	 */
	private static long limiteFaixaTempo1 = TimeUnit.HOURS.convert(1, TimeUnit.DAYS);
	private static long limiteFaixaTempo2 = TimeUnit.HOURS.convert(3, TimeUnit.DAYS);
	private static long limiteFaixaTempo3 = TimeUnit.HOURS.convert(7, TimeUnit.DAYS);
	private static long limiteFaixaTempo4 = TimeUnit.HOURS.convert(30, TimeUnit.DAYS);
	private static long limiteFaixaTempo5 = TimeUnit.HOURS.convert(100, TimeUnit.DAYS);
	private static long limiteFaixaTempo6 = TimeUnit.HOURS.convert(365, TimeUnit.DAYS);
	private static long limiteFaixaTempo7 = 2 * TimeUnit.HOURS.convert(365, TimeUnit.DAYS);;
	
	private static Integer pontuacaoFaixaTempo1 = 54;
	private static Integer pontuacaoFaixaTempo2 = 27;
	private static Integer pontuacaoFaixaTempo3 = 16;
	private static Integer pontuacaoFaixaTempo4 = 9;
	private static Integer pontuacaoFaixaTempo5 = 5;
	private static Integer pontuacaoFaixaTempo6 = 3;
	private static Integer pontuacaoFaixaTempo7 = 1;
	private static Integer pontuacaoFaixaTempo8 = 0;

	private ResultadoCriterioPriorizacao calculaCriterioTempoDeVida(JiraIssue issue) {
		ResultadoCriterioPriorizacao resultadoCriterio = new ResultadoCriterioPriorizacao("Tempo de vida da issue");

		String mensagemCriterio = "";
		Integer pontosCriterio = 0;
		String datetimeCriacaoIssueStr = issue.getFields().getCreated();
		if(StringUtils.isNotBlank(datetimeCriacaoIssueStr)) {
			Date datetimeCriacaoIssue = Utils.getDateFromString(datetimeCriacaoIssueStr);
			if(datetimeCriacaoIssue != null) {
				mensagemCriterio = "Issue criada em: " + Utils.dateToStringPattern(datetimeCriacaoIssue, Utils.DATE_PATTERN_PORTUGUESE);
				Date dataHoje = Utils.calculateDaysFromNow(0);
				long horasDesdeAbertura = Utils.checkDifferenceInHoursBetweenTwoDates(dataHoje, datetimeCriacaoIssue);
				if(horasDesdeAbertura > TimeUnit.HOURS.convert(3, TimeUnit.DAYS)) {
					long diasDesdeAbertura = TimeUnit.DAYS.convert(horasDesdeAbertura, TimeUnit.HOURS);
					mensagemCriterio += " (" + diasDesdeAbertura + " dias)";
				}else {
					mensagemCriterio += " (" + horasDesdeAbertura + " horas)";
				}

				if(horasDesdeAbertura < limiteFaixaTempo1) {
					pontosCriterio = pontuacaoFaixaTempo1;
				}else if(horasDesdeAbertura < limiteFaixaTempo2) {
					pontosCriterio = pontuacaoFaixaTempo2;
				}else if(horasDesdeAbertura < limiteFaixaTempo3) {
					pontosCriterio = pontuacaoFaixaTempo3;
				}else if(horasDesdeAbertura < limiteFaixaTempo4) {
					pontosCriterio = pontuacaoFaixaTempo4;
				}else if(horasDesdeAbertura < limiteFaixaTempo5) {
					pontosCriterio = pontuacaoFaixaTempo5;
				}else if(horasDesdeAbertura < limiteFaixaTempo6) {
					pontosCriterio = pontuacaoFaixaTempo6;
				}else if(horasDesdeAbertura < limiteFaixaTempo7) {
					pontosCriterio = pontuacaoFaixaTempo7;
				}else{
					pontosCriterio = pontuacaoFaixaTempo8;
				}
				resultadoCriterio.setMensagem(mensagemCriterio);
			}
		}else {
			resultadoCriterio.setMensagem("Data de criação da issue não identificada");
		}
		
		resultadoCriterio.setPontuacao(pontosCriterio);

		return resultadoCriterio;
	}
	
	/**
	 * 2. TIPO DE ISSUE (a resolução/correção de problemas deve ser incentivada)
	 * --- hotfix +35
	 * --- bugfix release +15
	 * --- defeito +10
	 * --- dúvidas/questões +5
	 * --- melhoria +5
	 * --- nova funcionalidade +3
	 * --- outros - +0
	 */
	
	private static Integer pontuacaoTipoIssueHotFix = 35;
	private static Integer pontuacaoTipoIssueBugFix = 15;
	private static Integer pontuacaoTipoIssueBug = 10;
	private static Integer pontuacaoTipoIssueImprovement = 5;
	private static Integer pontuacaoTipoIssueNewFeature = 3;
	private static Integer pontuacaoTipoIssueQuestion = 5;

	private ResultadoCriterioPriorizacao calculaCriterioTipoIssue(JiraIssue issue) {
		ResultadoCriterioPriorizacao resultadoCriterio = new ResultadoCriterioPriorizacao("Tipo de issue");

		Integer pontosCriterio = 0;
		JiraIssuetype issueType = issue.getFields().getIssuetype();
		if(issueType != null && StringUtils.isNotBlank(issueType.getName())) {
			resultadoCriterio.setMensagem("O tipo da demanda é: " + issueType.getName());
			
			switch (issueType.getId().toString()) {
			case JiraService.ISSUE_TYPE_HOTFIX:
				pontosCriterio = pontuacaoTipoIssueHotFix;
				break;
			case JiraService.ISSUE_TYPE_BUGFIX:
				pontosCriterio = pontuacaoTipoIssueBugFix;
				break;
			case JiraService.ISSUE_TYPE_BUG:
				pontosCriterio = pontuacaoTipoIssueBug;
				break;
			case JiraService.ISSUE_TYPE_NEWFEATURE:
				pontosCriterio = pontuacaoTipoIssueNewFeature;
				break;
			case JiraService.ISSUE_TYPE_IMPROVEMENT:
				pontosCriterio = pontuacaoTipoIssueImprovement;
				break;
			case JiraService.ISSUE_TYPE_QUESTION:
				pontosCriterio = pontuacaoTipoIssueQuestion;
				break;
			}
		}else {
			resultadoCriterio.setMensagem("Tipo de demanda não identificado");
		}

		resultadoCriterio.setPontuacao(pontosCriterio);

		return resultadoCriterio;
	}
	

	/**
 	 * 3. ÁREA DE CONHECIMENTO (por ordem decrescente de número de issues pendentes) - o total de issues pendentes por área de conhecimento deve ser calculado 1 vez por mês 
	 * e armazenado no campo ctrlAreasConhecimento
	 * --- nível1 - áreas com até 20% do total: +45
	 * --- nível2 - áreas entre 20% e 45% do total: +30
	 * --- nível3 - áreas entre 45% e 75%: +12
	 * --- nível4 - áreas entre 75% e 90%: +5
	 * --- nível5 - áreas acima de 90%: +1
	 * **Nos casos de issues com mais de uma área de conhecimento, os valores são somados
	 */

	private static Integer pontuacaoAreaConhecimentoNivel1 = 45;
	private static Integer pontuacaoAreaConhecimentoNivel2 = 30;
	private static Integer pontuacaoAreaConhecimentoNivel3 = 12;
	private static Integer pontuacaoAreaConhecimentoNivel4 = 5;
	private static Integer pontuacaoAreaConhecimentoNivel5 = 1;

	private ResultadoCriterioPriorizacao calculaCriterioClassificacaoAreaConhecimento(JiraIssue issue) {
		ResultadoCriterioPriorizacao resultadoCriterio = new ResultadoCriterioPriorizacao("Área de conhecimento relacionada");

		Integer pontosCriterio = 0;
		JiraCustomField ctrlAreasConhecimento = jiraService.getCtrlPontuacaoAreasConhecimento();
		if(ctrlAreasConhecimento != null) {
			List<JiraIssueFieldOption> areasConhecimentoDemanda = issue.getFields().getAreasConhecimento();
			
			if(areasConhecimentoDemanda != null && !areasConhecimentoDemanda.isEmpty()) {
				List<String> areas = new ArrayList<>();
				
				for (JiraIssueFieldOption areaConhecimento : areasConhecimentoDemanda) {
					
					JiraCustomFieldOption nivel = JiraUtils.getParentOptionWithChildName(
									ctrlAreasConhecimento.getOptions(), areaConhecimento.getValue());
					if(nivel != null && StringUtils.isNotBlank(nivel.getValue())) {
						if(Utils.compareAsciiIgnoreCase(NivelClassificacaoAreasConhecimentoEnum.NIVEL1.name(), nivel.getValue())) {
							pontosCriterio += pontuacaoAreaConhecimentoNivel1;
							areas.add(areaConhecimento.getValue() + " (+" + pontuacaoAreaConhecimentoNivel1 + ")");
						}else if(Utils.compareAsciiIgnoreCase(NivelClassificacaoAreasConhecimentoEnum.NIVEL2.name(), nivel.getValue())) {
							pontosCriterio += pontuacaoAreaConhecimentoNivel2;
							areas.add(areaConhecimento.getValue() + " (+" + pontuacaoAreaConhecimentoNivel2 + ")");
						}else if(Utils.compareAsciiIgnoreCase(NivelClassificacaoAreasConhecimentoEnum.NIVEL3.name(), nivel.getValue())) {
							pontosCriterio += pontuacaoAreaConhecimentoNivel3;
							areas.add(areaConhecimento.getValue() + " (+" + pontuacaoAreaConhecimentoNivel3 + ")");
						}else if(Utils.compareAsciiIgnoreCase(NivelClassificacaoAreasConhecimentoEnum.NIVEL4.name(), nivel.getValue())) {
							pontosCriterio += pontuacaoAreaConhecimentoNivel4;
							areas.add(areaConhecimento.getValue() + " (+" + pontuacaoAreaConhecimentoNivel4 + ")");
						}else{
							pontosCriterio += pontuacaoAreaConhecimentoNivel5;
							areas.add(areaConhecimento.getValue() + " (+" + pontuacaoAreaConhecimentoNivel5 + ")");
						}
					}
				}
				resultadoCriterio.setMensagem(String.join(", ", areas));
			}else {
				resultadoCriterio.setMensagem("Áreas de conhecimento não identificadas");
			}
		}else {
			resultadoCriterio.setMensagem("ERRO!! A classificação das áreas de conhecimento não foi encontrada.");
		}

		resultadoCriterio.setPontuacao(pontosCriterio);

		return resultadoCriterio;
	}

	/**
 	 * 4. INDICAÇÃO DE PEDIDO DE PRIORIDADE FEITA PELO DEMANDANTE
	 * -- indicação de prioridade feita pelo demandante:
	 * --- 00 - sistema fora do ar: +20 (a issue deve ser identificada como um hotfix ou bugfix)
	 * --- 01 - crítico: +11
	 * --- 03 - normal: +3
	 * --- 04 - menor: +0
	 */
	private static Integer pontuacaoPedidoPrioridade00 = 20;
	private static Integer pontuacaoPedidoPrioridade01 = 11;
	private static Integer pontuacaoPedidoPrioridade03 = 3;
	private static Integer pontuacaoPedidoPrioridade04 = 0;

	private ResultadoCriterioPriorizacao calculaCriterioPedidoPrioridade(JiraIssue issue) {
		ResultadoCriterioPriorizacao resultadoCriterio = new ResultadoCriterioPriorizacao("Pedido de prioridade");

		Integer pontosCriterio = 0;
		JiraIssueFieldsPriority pedidoPrioridade = issue.getFields().getPriority();
		if(pedidoPrioridade != null && StringUtils.isNotBlank(pedidoPrioridade.getName())) {
			resultadoCriterio.setMensagem("Indicado: " + pedidoPrioridade.getName());
			
			switch (pedidoPrioridade.getId().toString()) {
			case JiraService.ISSUE_REPORTER_PRIORITY_PANIC:
				pontosCriterio = pontuacaoPedidoPrioridade00;
				break;
			case JiraService.ISSUE_REPORTER_PRIORITY_CRITICAL:
				pontosCriterio = pontuacaoPedidoPrioridade01;
				break;
			case JiraService.ISSUE_REPORTER_PRIORITY_NORMAL:
				pontosCriterio = pontuacaoPedidoPrioridade03;
				break;
			case JiraService.ISSUE_REPORTER_PRIORITY_MINOR:
				pontosCriterio = pontuacaoPedidoPrioridade04;
				break;
			}
		}else {
			resultadoCriterio.setMensagem("Pedido de prioridade da demanda não identificado");
		}

		resultadoCriterio.setPontuacao(pontosCriterio);

		return resultadoCriterio;
	}

	/**
	 * 5. TRIBUNAIS REQUISITANTES
	 * -- quantidade de tribunais requisitantes: +4 por tribunal
	 * **bolar um esquema qualitativo para essa pontuação, possibilitando contabilizar mais pontos para os tribunais de acordo com a utilização da versão nacional, mais pontos para os tribunais com versões mais atualizadas
	 * ***tem que ver se da pra gerar esse tipo de coisa automaticamente
	 */
	private static Integer pontuacaoPorTribunal = 4;

	private ResultadoCriterioPriorizacao calculaCriterioTribunalRequisitante(JiraIssue issue) {
		ResultadoCriterioPriorizacao resultadoCriterio = new ResultadoCriterioPriorizacao("Tribunal/apoiador requisitante");

		Integer pontosCriterio = 0;
		List<JiraIssueFieldOption> tribunaisRequisitantes = issue.getFields().getTribunalRequisitante();
		if(tribunaisRequisitantes != null && !tribunaisRequisitantes.isEmpty()) {
			pontosCriterio = tribunaisRequisitantes.size() * pontuacaoPorTribunal;
			List<String> nomesTribunais = new ArrayList<>();
			for (JiraIssueFieldOption requisitante : tribunaisRequisitantes) {
				if(StringUtils.isNotBlank(requisitante.getValue())) {
					nomesTribunais.add(requisitante.getValue());
				}
			}
			resultadoCriterio.setMensagem("Requisitante(s): " + String.join(", ", nomesTribunais));
		}else {
			resultadoCriterio.setMensagem("Requisitante não identificado");
		}

		resultadoCriterio.setPontuacao(pontosCriterio);

		return resultadoCriterio;
	}
	
	/**
 	 * 6. DEMANDAS RELACIONADAS
	 * -- número de links para outras issues: +2 por link (verificar se não há mais de um link para a mesma issue)
	 * ** serão avaliadas apenas demandas de tribunais diferentes
	 */
	private static Integer pontuacaoPorRelacionamentoEntreDemandas = 4;

	private ResultadoCriterioPriorizacao calculaCriterioLigacaoEntreDemandas(JiraIssue issue) {
		ResultadoCriterioPriorizacao resultadoCriterio = new ResultadoCriterioPriorizacao("Relacionamento entre demandas de outros demandantes");

		Integer pontosCriterio = 0;
		List<JiraIssueLinkRequest> issuelinks = issue.getFields().getIssuelinks();
		JiraUser usuarioCriador = issue.getFields().getReporter();
		String tribunalUsuarioCriador = jiraService.getTribunalUsuario(usuarioCriador, false);
		List<String> issueKeys = new ArrayList<>();
		if(issuelinks != null && !issuelinks.isEmpty()) {
			for (JiraIssueLinkRequest issuelink : issuelinks) {
				String issueKeyRelacionada = null;
				if(issuelink.getOutwardIssue() != null && StringUtils.isNotBlank(issuelink.getOutwardIssue().getKey())
						&& !issuelink.getOutwardIssue().getKey().equalsIgnoreCase(issue.getKey())
						&& !issueKeys.contains(issuelink.getOutwardIssue().getKey())) {
					issueKeyRelacionada = issuelink.getOutwardIssue().getKey();
				}
				if(StringUtils.isBlank(issueKeyRelacionada)
						&& issuelink.getInwardIssue() != null && StringUtils.isNotBlank(issuelink.getInwardIssue().getKey())
						&& !issuelink.getInwardIssue().getKey().equalsIgnoreCase(issue.getKey())
						&& !issueKeys.contains(issuelink.getInwardIssue().getKey())) {
					issueKeyRelacionada = issuelink.getInwardIssue().getKey();
				}
				if(StringUtils.isNotBlank(issueKeyRelacionada)) {
					JiraIssue issueRelacionada = jiraService.recuperaIssueDetalhada(issueKeyRelacionada);
					if(issueRelacionada != null) {
						Boolean contabilizarDemandaRelacionada = true;
						if(StringUtils.isNotBlank(tribunalUsuarioCriador)) {
							JiraUser usuarioCriadorIssueRelacionada = issueRelacionada.getFields().getReporter();
							String tribunalUsuarioCriadorIssueRelacionada = jiraService.getTribunalUsuario(usuarioCriadorIssueRelacionada, false);
							if(StringUtils.isNotBlank(tribunalUsuarioCriadorIssueRelacionada) && tribunalUsuarioCriador.equalsIgnoreCase(tribunalUsuarioCriadorIssueRelacionada) ) {
								contabilizarDemandaRelacionada = false;
							}
						}
						if(contabilizarDemandaRelacionada) {
							issueKeys.add(issueKeyRelacionada);
						}
					}
				}else {
					messages.error("Não conseguiu encontrar o identificador da issue relacionada");
				}
			}
			pontosCriterio = issueKeys.size() * pontuacaoPorRelacionamentoEntreDemandas;
		}
		if(issueKeys != null && !issueKeys.isEmpty()) {
			resultadoCriterio.setMensagem("Demandas relacionadas: " + String.join(", ", issueKeys));
		}else {
			resultadoCriterio.setMensagem("Sem demandas relacionadas");
		}

		resultadoCriterio.setPontuacao(pontosCriterio);

		return resultadoCriterio;
	}	
}