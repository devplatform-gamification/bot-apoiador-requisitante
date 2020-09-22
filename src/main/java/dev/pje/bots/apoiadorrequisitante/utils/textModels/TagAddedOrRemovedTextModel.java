package dev.pje.bots.apoiadorrequisitante.utils.textModels;


import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class TagAddedOrRemovedTextModel extends AbstractTextModel{

	@Value("${clients.jira.url}")
	private String JIRAURL;

	private static final String DESENVOLVEDOR_ANONIMO = "desenvolvedor.anonimo";

	private String getPathIssue(String issueKey) {
		return JiraUtils.getPathIssue(issueKey, JIRAURL);
	}

	private String getPathUserProfile(String userKey) {
		return JiraUtils.getPathUserProfile(userKey, JIRAURL);
	}

	private String issueKey;
	private String actualVersion;
	private String pomActualVersion;
	private String tagMessage;
	private Boolean tagCreated;
	private String userName;

	public String convert(MarkdownInterface markdown) {
		StringBuilder markdownText = new StringBuilder();
		markdownText.append("A tag ")
		.append(markdown.underline(actualVersion));
		if(StringUtils.isNotBlank(tagMessage)) {
			markdownText.append(markdown.italic("("+ tagMessage +")"));
		}
		if(tagCreated) {
			markdownText.append(" foi gerada");
		}else {
			markdownText.append(" " + markdown.bold("foi cancelada"));
		}
		if(StringUtils.isNotBlank(userName)) {
			if(userName.equalsIgnoreCase(DESENVOLVEDOR_ANONIMO)) {
				markdownText.append(" automaticamente");
			}else {
				String referUser = markdown.referUser(userName);
				if(StringUtils.isBlank(referUser) || referUser.equals(userName)) {
					referUser = markdown.link(getPathUserProfile(userName), userName);
				}
				markdownText.append(" por ")
				.append(markdown.underline(referUser));
			}
		}
		markdownText.append(", valide o texto do novo release notes para atualizar a documentação da versão.");
		if(!tagCreated) {
			markdownText.append(" E para regerar a tag.");
		}
		if(StringUtils.isNotBlank(markdown.getName()) && !markdown.getName().equalsIgnoreCase("JiraMarkdown")) {
			markdownText.append(markdown.newLine())
			.append(markdown.normal("Na issue: " + markdown.link(getPathIssue(issueKey), issueKey)))
			.append(markdown.newLine());
		}
		if(!actualVersion.equals(pomActualVersion)) {
			markdownText.append(markdown.newLine())
			.append(markdown.block(
					markdown.error("O número da TAG (" + actualVersion 
							+ ") não corresponde à versão do código (" + 
							pomActualVersion + "), essa situação precisa ser verificada.")));
		}
		
		return markdownText.toString();
	}

	public void setIssueKey(String issueKey) {
		this.issueKey = issueKey;
	}

	public void setActualVersion(String actualVersion) {
		this.actualVersion = actualVersion;
	}

	public void setPomActualVersion(String pomActualVersion) {
		this.pomActualVersion = pomActualVersion;
	}

	public void setTagMessage(String tagMessage) {
		this.tagMessage = tagMessage;
	}

	public void setTagCreated(Boolean tagCreated) {
		this.tagCreated = tagCreated;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}
}
