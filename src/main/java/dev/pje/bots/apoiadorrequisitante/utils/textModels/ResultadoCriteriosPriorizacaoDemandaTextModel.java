package dev.pje.bots.apoiadorrequisitante.utils.textModels;


import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.prioridadeDemanda.ResultadoCriterioPriorizacao;

import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class ResultadoCriteriosPriorizacaoDemandaTextModel extends AbstractTextModel{

	private List<ResultadoCriterioPriorizacao> resultadosCriterios;
	private Integer pontuacaoTotal;
	private String dataAvaliacao;
	
	@Value("${clients.rocketchat.url}") 
	private String rocketUrl;
	
	@Value("${project.rocketchat.channel.pje-dev-platform-id}") 
	private String GRUPO_PJE_DEV_PLATFORM;
	
	/**
	 * h3. Atualização da pontuação de prioridade desta demanda
	 * - critério X (+3)
	 * 	 "foi avaliado X, Y e Z"
	 * - critério Y (+40)
	 * 	  "foi avaliado o cenário Y, quanto tempo a issue está em aberto ...."
	 * - critério N (+X)
	 * <b>TOTAL +60</b>
	 */
	public String convert(MarkdownInterface markdown) {
		StringBuilder markdownText = new StringBuilder();

		markdownText.append(markdown.head3("Atualização da pontuação de prioridade desta demanda"));
		if(resultadosCriterios != null) {
			for (ResultadoCriterioPriorizacao criterio : resultadosCriterios) {
				markdownText
					.append(markdown.listItem(markdown.bold(criterio.getNome())))
					.append(markdown.normal(" (+" + criterio.getPontuacao() + ")"));
				if(StringUtils.isNotBlank(criterio.getMensagem())) {
					markdownText
						.append(markdown.newLine())
						.append(markdown.italic(criterio.getMensagem()));
				}
			}
		}
		markdownText
			.append(markdown.newLine())
			.append(markdown.bold("TOTAL +" + pontuacaoTotal));
		
		Date dataAtualizacao = Utils.getDateFromString(dataAvaliacao);
		String dataAtualizacaoStr = Utils.dateToStringPattern(dataAtualizacao, Utils.DATE_PATTERN_PORTUGUESE);
		markdownText
			.append(markdown.newLine())
			.append(markdown.italic("Data de referência :: " + dataAtualizacaoStr));
		
		String url = rocketUrl;
		if(StringUtils.isNotBlank(url) && StringUtils.isNotBlank(GRUPO_PJE_DEV_PLATFORM)) {
			if(!url.endsWith("/")) {
				url += "/";
			}
			url += "channel/" + GRUPO_PJE_DEV_PLATFORM;
			markdownText
				.append(markdown.newLine())
				.append(markdown.link(url, "Para saber mais sobre esta priorização acesse aqui"));
		}
		
		return markdownText.toString();
	}

	public List<ResultadoCriterioPriorizacao> getResultadosCriterios() {
		return resultadosCriterios;
	}

	public void setResultadosCriterios(List<ResultadoCriterioPriorizacao> resultadosCriterios) {
		this.resultadosCriterios = resultadosCriterios;
	}

	public Integer getPontuacaoTotal() {
		return pontuacaoTotal;
	}

	public void setPontuacaoTotal(Integer pontuacaoTotal) {
		this.pontuacaoTotal = pontuacaoTotal;
	}

	public String getDataAvaliacao() {
		return dataAvaliacao;
	}

	public void setDataAvaliacao(String dataAvaliacao) {
		this.dataAvaliacao = dataAvaliacao;
	}
}
