package dev.pje.bots.apoiadorrequisitante.utils.textModels;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.jira.JiraIssue;

import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class TribunalRevisaoMRPendenteTextModel extends AbstractTextModel {

	@Value("${clients.jira.url}")
	private String JIRAURL;

	@Value("${project.documentation.manual-revisao-codigo}")
	private String MANUAL_REVISAO_CODIGO;

	private List<JiraIssue> issues;
	private String siglaTribunal;

	private String getPathIssue(String issueKey) {
		return JiraUtils.getPathIssue(issueKey, JIRAURL);
	}

	/**
	 * O tribunal XXX consta como demandante das seguintes issues, mas ainda não registrou a sua homologação: 
	 * - PJEVII-4493 - Adicionar checkbox para selecionar todos os processos na aba aptos para publicação - há 2 aprovações de 3 necessárias
	 * - PJEVII-6666 - Adicionar checkbox para selecionar todos os processos na aba aptos para publicação - há 2 aprovações de 3 necessárias
	 * Para que a alteração seja incluída na versão nacional é necessário que o tribunal avalie a sua implementação.
	 * Referência: Manual de revisão de código
	 * 
	 */
	public String convert(MarkdownInterface markdown) {
		StringBuilder markdownText = new StringBuilder();
		markdownText
			.append(markdown.normal("O tribunal "))
			.append(markdown.bold(siglaTribunal))
			.append(markdown.normal("  consta como demandante das seguintes issues, mas ainda não registrou a sua homologação:"))
			.append(markdown.newLine());
		
		for (JiraIssue issue : issues) {
			StringBuilder identificacaoIssue = new StringBuilder();
			Integer aprovacoesRealizadas = issue.getFields().getAprovacoesRealizadas();
			if(aprovacoesRealizadas == null) {
				aprovacoesRealizadas = 0;
			}
			Integer aprovacoesNecessarias = issue.getFields().getAprovacoesNecessarias();
			if(aprovacoesNecessarias == null) {
				aprovacoesNecessarias = 0;
			}
			
			identificacaoIssue
				.append(markdown.link(getPathIssue(issue.getKey()), issue.getKey()))
				.append(markdown.normal(" - "))
				.append(markdown.normal(Utils.clearSummary(issue.getFields().getSummary())))
				.append(markdown.normal(" - "));
			if(aprovacoesRealizadas> 0) {
				identificacaoIssue
					.append("(há ")
					.append(aprovacoesRealizadas);
				if(aprovacoesRealizadas < 2) {
					identificacaoIssue.append(markdown.normal(" aprovação "));
				}else {
					identificacaoIssue.append(markdown.normal(" aprovações"));
				}
			}else {
				identificacaoIssue.append("(sem aprovações");
			}
			identificacaoIssue
				.append(" de ")
				.append(aprovacoesNecessarias)
				.append(" necessárias);");
			
			markdownText
				.append(markdown.listItem(identificacaoIssue.toString()));
		}
		markdownText
			.append(markdown.newParagraph())
			.append(markdown.normal("Para que a alteração seja incluída na versão nacional é necessário que o tribunal homologue essas demandas."));

		markdownText
			.append(markdown.newParagraph())
			.append(markdown.link(MANUAL_REVISAO_CODIGO, "Referência: Manual de revisão de código"));

		return markdownText.toString();
	}

	public void setIssues(List<JiraIssue> issues) {
		this.issues = issues;
	}

	public void setSiglaTribunal(String siglaTribunal) {
		this.siglaTribunal = siglaTribunal;
	}
}