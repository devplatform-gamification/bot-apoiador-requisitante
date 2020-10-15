package dev.pje.bots.apoiadorrequisitante.utils.textModels;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabMergeRequestAttributes;
import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.rocketchat.RocketchatUser;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.RocketchatService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class AprovacoesMRTextModel implements AbstractTextModel {

	@Value("${clients.jira.url}")
	private String JIRAURL;

	@Value("${project.documentation.manual-revisao-codigo}")
	private String MANUAL_REVISAO_CODIGO;

	@Autowired
	protected RocketchatService rocketchatService;

	@Autowired
	protected GitlabService gitlabService;

	private GitlabMergeRequestAttributes mergeRequest;
	private JiraIssue issue;
	private Integer aprovacoesRealizadas;
	private Integer aprovacoesNecessarias;

	private boolean aprovou = false;
	private JiraUser ultimoRevisor;

	private List<String> tribunaisRequisitantesPendentes;
	private List<JiraUser> usuariosResponsaveisAprovacoes;

	private String getPathIssue(String issueKey) {
		return JiraUtils.getPathIssue(issueKey, JIRAURL);
	}

	private String getPathUserProfile(String userKey) {
		return JiraUtils.getPathUserProfile(userKey, JIRAURL);
	}

	/**
	 * :: monta mensagem com as informações ::
	 * - qual MR e de qual issue recebeu a aprovação - quantas aprovações são necessárias e quantas já foram feitas
	 * - se não completou as necessárias:
	 * -- quais tribunais requisitantes ainda não aprovaram
	 * - se já completou as necessárias:
	 * -- indica a aprovação e quais foram as pessoas que aprovaram o MR
	 * **colocar um link para a documentação do grupo revisor de códigos existente em docs.pje.jus.br**
	 */

	/**
	 * <<se não for para o jira>>
	 * PJEV-9999 - <titulo da issue sem a sigla do tribunal>
	 * <<para todas as plataformas>>
	 * <Aprovação>
	 * [MR#xx/link para o merge] aprovado pelo revisor @username.revisor (aprovação <1> de <3> necessárias).
	 * <<se atingiu as necessárias>>
	 * Este MR será automaticamente integrado ao branch <develop> do projeto <nome-projeto>, de acordo com as aprovações de:
	 * - @revisor1
	 * - @revisor2
	 * - @revisor3
	 * 	 * 
	 * <Retirada>
	 * @username.revisor retirou sua aprovação do [MR#XXX/link para o merge] (há <1> aprovação de <3> necessárias).
	 * 
	 * 
	 * <<<<se há tribunais requisitantes que ainda não aprovaram>>>>
	 *  Os tribunais TJSS, TJMM, TJDD constam como requisitantes da demanda, mas ainda não fizeram a sua homologação.
	 * 
	 */
	public String convert(MarkdownInterface markdown) {
		StringBuilder markdownText = new StringBuilder();

		String username = getUsername(ultimoRevisor, markdown);
		if(aprovacoesRealizadas == null) {
			aprovacoesRealizadas = 0;
		}
		if(aprovacoesNecessarias == null) {
			aprovacoesNecessarias = 0;
		}

		String referUser = markdown.referUser(username);
		if (StringUtils.isBlank(referUser) || referUser.equals(username)) {
			referUser = markdown.link(getPathUserProfile(username), username);
		}
		if(!markdown.getName().equals(MarkdownInterface.MARKDOWN_JIRA)) {
			// <<se não for para o jira>>
			// PJEV-9999 - <titulo da issue sem a sigla do tribunal>

			markdownText
				.append(markdown.link(getPathIssue(issue.getKey()), issue.getKey()))
				.append(markdown.normal(" - "))
				.append(markdown.normal(Utils.clearSummary(issue.getFields().getSummary())))
				.append(markdown.newLine());
		}

		String nomeMR = "MR#" + mergeRequest.getIid().toString();
		if (aprovou) {
			// APROVOU O LABEL
			// 	 * [MR#xx/link para o merge] aprovado pelo revisor @username.revisor (aprovação <1> de <3> necessárias).
			markdownText
					.append(markdown.link(mergeRequest.getUrl(), nomeMR))
					.append(" ")
					.append(markdown.bold("aprovado pelo revisor"))
					.append(" ")
					.append(markdown.normal(referUser))
					.append(" (aprovação ")
					.append(aprovacoesRealizadas)
					.append(" de ")
					.append(aprovacoesNecessarias)
					.append(" necessárias).");
		} else {
			// RETIROU LABEL
			// @username.revisor retirou sua aprovação do [MR#XXX/link para o merge] (há <1> aprovação de <3> necessárias).
			markdownText.append(markdown.normal(referUser))
					.append(" ")
					.append(markdown.bold("retirou sua aprovação do"))
					.append(" ")
					.append(markdown.link(mergeRequest.getUrl(), nomeMR))
					.append(" (há ").append(aprovacoesRealizadas);

			if (aprovacoesRealizadas <= 1) {
				markdownText.append(" aprovação ");
			} else {
				markdownText.append(" aprovações ");
			}

			markdownText.append(" de ").append(aprovacoesNecessarias).append(" necessárias).");
		}

		if (aprovacoesRealizadas >= aprovacoesNecessarias) {
			// ATINGIU O MÍNIMO DE APROVACOES NECESSÁRIO
			String targetBranch = mergeRequest.getTargetBranch();
			String nomeProjeto = issue.getFields().getProject().getName();
			markdownText.append(markdown.newLine()).append("Este MR será automaticamente integrado ao branch ")
					.append(targetBranch).append(" do projeto ").append(nomeProjeto)
					.append(", de acordo com as aprovações de:");

			if (usuariosResponsaveisAprovacoes != null) {
				for (JiraUser usuarioResponsavel : usuariosResponsaveisAprovacoes) {
					String usernameResponsavel = getUsername(usuarioResponsavel, markdown);
					String referUserResponsaveis = markdown.referUser(usernameResponsavel);
					if (StringUtils.isBlank(referUserResponsaveis)
							|| referUserResponsaveis.equals(usernameResponsavel)) {
						referUserResponsaveis = markdown.link(getPathUserProfile(usernameResponsavel),
								usernameResponsavel);
					}
					markdownText.append(markdown.listItem(referUserResponsaveis));
				}
			}
		} else if (tribunaisRequisitantesPendentes != null && !tribunaisRequisitantesPendentes.isEmpty()) {
			// AINDA NÃO APROVOU O MÍNIMO E HÁ TRIBUNAIS REQUISITANTES PENDENTES
			boolean isPlural = (tribunaisRequisitantesPendentes.size() > 1);
			markdownText.append(markdown.newLine());
			if (isPlural) {
				markdownText
						.append(markdown.bold(String.join(", ", tribunaisRequisitantesPendentes)))
						.append(" constam como requisitantes da demanda, mas ")
						.append(markdown.underline("ainda não registraram a sua homologação."));
			} else {
				markdownText
						.append(markdown.bold(String.join(", ", tribunaisRequisitantesPendentes)))
						.append(" consta como requisitante da demanda, mas ")
						.append(markdown.underline("ainda não registrou a sua homologação."));
			}
		}
		markdownText
			.append(markdown.newLine())
			.append(markdown.link(MANUAL_REVISAO_CODIGO, "Referência: Manual de revisão de código"));
		return markdownText.toString();
	}

	private String getUsername(JiraUser jirauser, MarkdownInterface markdown) {
		String username = null;
		if (jirauser != null) {
			if (markdown.getName().equals(MarkdownInterface.MARKDOWN_ROCKETCHAT)) {
				if (jirauser != null) {
					username = jirauser.getName();
					RocketchatUser rocketUser = rocketchatService.findUser(jirauser.getEmailAddress());
					if (rocketUser != null) {
						username = rocketUser.getUsername();
					}
				}
			} else if (markdown.getName().equals(MarkdownInterface.MARKDOWN_JIRA)) {
				username = jirauser.getName();
			} else if (markdown.getName().equals(MarkdownInterface.MARKDOWN_GITLAB)) {
				if (jirauser != null) {
					username = jirauser.getName();
					GitlabUser gitlabUser = gitlabService.findUserByEmail(jirauser.getEmailAddress());
					if (gitlabUser != null) {
						username = gitlabUser.getUsername();
					}
				}
			} else {
				username = jirauser.getName();
			}
		}
		return username;
	}

	public void setMergeRequest(GitlabMergeRequestAttributes mergeRequest) {
		this.mergeRequest = mergeRequest;
	}

	public void setIssue(JiraIssue issue) {
		this.issue = issue;
	}

	public void setAprovacoesRealizadas(Integer aprovacoesRealizadas) {
		this.aprovacoesRealizadas = aprovacoesRealizadas;
	}

	public void setAprovacoesNecessarias(Integer aprovacoesNecessarias) {
		this.aprovacoesNecessarias = aprovacoesNecessarias;
	}

	public void setAprovou(boolean aprovou) {
		this.aprovou = aprovou;
	}

	public void setUltimoRevisor(JiraUser ultimoRevisor) {
		this.ultimoRevisor = ultimoRevisor;
	}

	public void setTribunaisRequisitantesPendentes(List<String> tribunaisRequisitantesPendentes) {
		this.tribunaisRequisitantesPendentes = tribunaisRequisitantesPendentes;
	}

	public void setUsuariosResponsaveisAprovacoes(List<JiraUser> usuariosResponsaveisAprovacoes) {
		this.usuariosResponsaveisAprovacoes = usuariosResponsaveisAprovacoes;
	}
}