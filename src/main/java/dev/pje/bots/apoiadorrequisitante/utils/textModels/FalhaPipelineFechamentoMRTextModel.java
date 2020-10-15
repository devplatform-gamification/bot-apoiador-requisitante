package dev.pje.bots.apoiadorrequisitante.utils.textModels;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.rocketchat.RocketchatUser;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.RocketchatService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class FalhaPipelineFechamentoMRTextModel implements AbstractTextModel {

	@Value("${clients.jira.url}")
	private String JIRAURL;

	@Autowired
	protected RocketchatService rocketchatService;

	@Autowired
	protected GitlabService gitlabService;

	private GitlabMRResponse mergeRequest;
	private JiraIssue issue;
	private String pipelineWebUrl;

	private JiraUser desenvolvedor;

	private String getPathIssue(String issueKey) {
		return JiraUtils.getPathIssue(issueKey, JIRAURL);
	}

	private String getPathUserProfile(String userKey) {
		return JiraUtils.getPathUserProfile(userKey, JIRAURL);
	}

	/**
	 * :: monta mensagem com as informações ::
	 * <<se não for para o jira>>
	 * PJEV-9999 - <titulo da issue sem a sigla do tribunal>
	 * <<para todas as plataformas>>
	 * [MR#xx/link para o merge] foi fechado por não ter passado no pipeline de validações automatizadas do gitlab, 
	 * o desenvolvedor [~desenvolvedor.xyz] deve rever a saída deste processo [jobID/link para o job com falha], adaptar a solução e ressubmete-la.
	 */
	public String convert(MarkdownInterface markdown) {
		StringBuilder markdownText = new StringBuilder();

		String username = getUsername(desenvolvedor, markdown);
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
		// 	[MR#xx/link para o merge] foi fechado por não ter passado no pipeline de validações automatizadas do gitlab, 
		//	o desenvolvedor [~desenvolvedor.xyz] deve rever a saída deste processo [jobID/link para o job com falha], adaptar a solução e ressubmete-la.
		markdownText
			.append(markdown.link(mergeRequest.getWebUrl(), nomeMR))
			.append(" ")
			.append(markdown.bold("foi fechado"))
			.append(markdown.normal(" por ter "))
			.append(markdown.underline("falhado"))
			.append(markdown.normal(" no processamento do pipeline de validações automatizadas do gitlab, o desenvolvedor "))
			.append(markdown.normal(referUser))
			.append(markdown.normal(" deve rever a saída deste processo "));
		
		// link para o pipeline
		markdownText.append(markdown.link(pipelineWebUrl, "(aqui)"));
		markdownText.append(markdown.normal(", adaptar sua solução e ressubmete-la."));

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

	public void setMergeRequest(GitlabMRResponse mergeRequest) {
		this.mergeRequest = mergeRequest;
	}

	public void setIssue(JiraIssue issue) {
		this.issue = issue;
	}

	public void setDesenvolvedor(JiraUser desenvolvedor) {
		this.desenvolvedor = desenvolvedor;
	}

	public void setPipelineWebUrl(String pipelineWebUrl) {
		this.pipelineWebUrl = pipelineWebUrl;
	}
}