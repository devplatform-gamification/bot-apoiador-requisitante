package dev.pje.bots.apoiadorrequisitante.utils.textModels;


import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class ReleaseCandidateTextModel implements AbstractTextModel{
	
	@Value("${clients.jira.url}")
	private String JIRAURL;

	private static final String PATH_JQL = "/issues/?jql=";
	
	private String version;
	private String branchName;
	private String jql;
	private String projectName;
	
	private String getPathJql(String jql) {
		return JIRAURL + PATH_JQL + jql;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public void setBranchName(String branchName) {
		this.branchName = branchName;
	}

	public void setJql(String jql) {
		this.jql = jql;
	}

	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}

	public String convert(MarkdownInterface markdown) {
		StringBuilder sbText = new StringBuilder();
		
		if(StringUtils.isNotBlank(version) && StringUtils.isNotBlank(branchName) && StringUtils.isNotBlank(jql) && StringUtils.isNotBlank(projectName)	) {
			sbText.append(markdown.head3("Disponibilizada a prévia da versão " + this.version + " do " + this.projectName))
				.append(markdown.newLine())
				.append(markdown.normal("Ela estará disponível para a homologação dos tribunais durante 15 dias quando será lançada a nova versão oficial, o branch é o " + markdown.bold(this.branchName)))
				.append(markdown.newLine())
				.append(markdown.normal("Veja as issues que compõem esta versão " + markdown.link(getPathJql(this.jql), "aqui")));
		}
		
		return sbText.toString();
	}
}
