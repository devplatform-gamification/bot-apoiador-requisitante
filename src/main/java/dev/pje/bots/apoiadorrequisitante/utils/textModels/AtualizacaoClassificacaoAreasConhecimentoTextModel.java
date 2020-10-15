package dev.pje.bots.apoiadorrequisitante.utils.textModels;


import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.areasConhecimento.ClassificacaoAreaConhecimento;
import com.devplatform.model.bot.areasConhecimento.NivelClassificacaoAreasConhecimentoEnum;
import com.devplatform.model.bot.areasConhecimento.PontuacoesAreasConhecimento;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class AtualizacaoClassificacaoAreasConhecimentoTextModel implements AbstractTextModel{

	@Value("${clients.jira.url}")
	private String JIRAURL;

	private PontuacoesAreasConhecimento pontuacoes;
	private String jqlBase;
	private String dataAtualizacaoClassificacao;

	/**
	 * Atualização da classificação das áreas de conhecimento em: DD/MM/YYYY
	 * Total de issues avaliadas: XX >> link para as issues
	 * Nível 1
	 * 	- Área X - num issues (N%) >> link para as issues
	 *  - Área Y - num issues (M%) >> link para as issues
	 * Nível 2
	 * 	- Área Z - num (%)
	 * Nível 3
	 * 	- Area 
	 * ....
	 */
	public String convert(MarkdownInterface markdown) {
		StringBuilder markdownText = new StringBuilder();

		markdownText.append(markdown.head3("Atualização da classificação das áreas de conhecimento"));
		markdownText
			.append(markdown.underline("Total de issues avaliadas: "))
			.append(pontuacoes.getTotalIssues())
			.append(markdown.newLine());
		
		NivelClassificacaoAreasConhecimentoEnum nivel = null;
		for (ClassificacaoAreaConhecimento classAreaConhecimento : pontuacoes.getClassificacoesAreasConhecimento()) {
			if(nivel == null || !nivel.equals(classAreaConhecimento.getNivel())) {
				nivel = classAreaConhecimento.getNivel();
				markdownText
					.append(markdown.newLine())
					.append(markdown.bold(nivel.name()));
			}
			
			StringBuilder itemList = new StringBuilder();
			itemList
				.append(markdown.link(getLinkJqlIssuesPendentesGeralPorAreaConhecimento(classAreaConhecimento.getNome()), classAreaConhecimento.getNome()))
				.append(markdown.normal(" - "))
				.append(markdown.normal(classAreaConhecimento.getTotalIssues().toString()))
				.append(markdown.normal(" "))
				.append(markdown.normal("("))
				.append(markdown.normal(Utils.doubleToStringAsPercent(classAreaConhecimento.getPercentual())))
				.append(markdown.normal(")"));
			
			markdownText
				.append(markdown.listItem(itemList.toString()));
		}
		
		Date dataAtualizacao = Utils.getDateFromString(dataAtualizacaoClassificacao);
		String dataAtualizacaoStr = Utils.dateToStringPattern(dataAtualizacao, Utils.DATE_PATTERN_PORTUGUESE);
		markdownText
			.append(markdown.newLine())
			.append(markdown.normal("Data de referência :: " + dataAtualizacaoStr));
		
		return markdownText.toString();
	}
	
	private String getLinkJqlIssuesPendentesGeralPorAreaConhecimento(String areaConhecimento) {
		StringBuilder jql = new StringBuilder();
		String jqlBase = getJqlBase();
		if(StringUtils.isNotBlank(areaConhecimento) && StringUtils.isNotBlank(jqlBase)) {
			jql
				.append(JIRAURL)
				.append("/issues/?jql=")
				.append(JiraUtils.getFieldNameToJQL(JiraService.FIELD_AREAS_CONHECIMENTO)).append(" in (\"")
				.append(areaConhecimento).append("\") AND ").append(jqlBase);
		}
		
		return jql.toString();
	}

	public PontuacoesAreasConhecimento getPontuacoes() {
		return pontuacoes;
	}

	public void setPontuacoes(PontuacoesAreasConhecimento pontuacoes) {
		this.pontuacoes = pontuacoes;
	}

	public String getJqlBase() {
		return jqlBase;
	}

	public void setJqlBase(String jqlBase) {
		this.jqlBase = jqlBase;
	}

	public String getDataAtualizacaoClassificacao() {
		return dataAtualizacaoClassificacao;
	}

	public void setDataAtualizacaoClassificacao(String dataAtualizacaoClassificacao) {
		this.dataAtualizacaoClassificacao = dataAtualizacaoClassificacao;
	}
}
