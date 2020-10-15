package dev.pje.bots.apoiadorrequisitante.utils.textModels;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.VersionReleaseNoteIssues;
import com.devplatform.model.bot.VersionReleaseNotes;
import com.devplatform.model.bot.VersionReleaseNotesIssueTypeEnum;
import com.devplatform.model.jira.JiraIssueTipoVersaoEnum;
import com.devplatform.model.jira.JiraUser;

import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class NewVersionReleasedNewsTextModel implements AbstractTextModel{

	private static final String DESENVOLVEDOR_ANONIMO = "desenvolvedor.anonimo";

	@Value("${project.documentation.url}")
	private String DOCSURL;

	//	private MarkdownInterface markdown;
	private VersionReleaseNotes releaseNotes;

	public void setReleaseNotes(VersionReleaseNotes releaseNotes) {
		this.releaseNotes = releaseNotes;
	}

	public String convert(MarkdownInterface markdown) {
		//		this.markdown = markdown;
		StringBuilder markdownText = new StringBuilder();
		if(releaseNotes != null && releaseNotes.getVersion() != null && releaseNotes.getVersionType() != null) {
			if(releaseNotes.getProject().contains("legay") || Utils.compareAsciiIgnoreCase(releaseNotes.getProject(), "PJE")) {
				markdownText.append(markdown.bold("Atualize o PJe-Legacy do seu tribunal!"))
					.append(markdown.newLine());
			}

			markdownText.append(markdown.head3("Versão " + releaseNotes.getVersion() + " disponível"))
			.append(markdown.newLine())
			.append(markdown.normal(releaseNotes.getProject()))
			.append(markdown.newLine());

			String resumoVersao = getResumoDaVersao();
			if(StringUtils.isNotBlank(resumoVersao)) {
				markdownText
				.append(markdown.highlight(resumoVersao));
			}

			// destaque da versão
			if(StringUtils.isNotBlank(releaseNotes.getVersionHighlights())) {
				markdownText
				.append(markdown.quote(releaseNotes.getVersionHighlights()));
			}

			// link disponível
			String releaseUrl = releaseNotes.getUrl();
			if(StringUtils.isBlank(releaseUrl)) {
				releaseUrl = DOCSURL;
			}
			markdownText.append(markdown.newLine())
			.append(markdown.link(releaseUrl, "Acesse o release notes completo desta versão aqui"));
			
			// link gitlab
			if(StringUtils.isNotBlank(releaseNotes.getGitlabProjectUrl())) {
				markdownText.append(markdown.newLine())
					.append(markdown.link(releaseNotes.getGitlabProjectUrl(), "Ou veja as releases deste serviço diretamente no gitlab."));
				
			}
		}

		return markdownText.toString();
	}

	private String getResumoDaVersao() {
		StringBuilder textToHighlight = new StringBuilder();
		if(!Utils.compareAsciiIgnoreCase(releaseNotes.getVersionType(), JiraIssueTipoVersaoEnum.ORDINARIA.toString())) {
			textToHighlight.append("Esta é uma versão: ")
			.append(releaseNotes.getVersionType())
			.append(" - ");
		}

		List<String> resumoIssuesVersao = new ArrayList<>();
		if(releaseNotes.getNewFeatures() != null && !releaseNotes.getNewFeatures().isEmpty()) {
			StringBuilder tipo = new StringBuilder()
					.append(VersionReleaseNotesIssueTypeEnum.NEW_FEATURE.toString())
					.append(": ")
					.append(releaseNotes.getNewFeatures().size());
			resumoIssuesVersao.add(tipo.toString());
		}
		if(releaseNotes.getImprovements() != null && !releaseNotes.getImprovements().isEmpty()) {
			StringBuilder tipo = new StringBuilder()
					.append(VersionReleaseNotesIssueTypeEnum.IMPROVEMENT.toString())
					.append(": ")
					.append(releaseNotes.getImprovements().size());
			resumoIssuesVersao.add(tipo.toString());
		}
		if(releaseNotes.getBugs() != null && !releaseNotes.getBugs().isEmpty()) {
			StringBuilder tipo = new StringBuilder()
					.append(VersionReleaseNotesIssueTypeEnum.BUGFIX.toString())
					.append(": ")
					.append(releaseNotes.getBugs().size());
			resumoIssuesVersao.add(tipo.toString());
		}
		if(releaseNotes.getMinorChanges() != null && !releaseNotes.getMinorChanges().isEmpty()) {
			StringBuilder tipo = new StringBuilder()
					.append(VersionReleaseNotesIssueTypeEnum.MINOR_CHANGES.toString())
					.append(": ")
					.append(releaseNotes.getMinorChanges().size());
			resumoIssuesVersao.add(tipo.toString());
		}
		// contador de pessoas que contribuíram, dar destaque às pessoas que mais contribuiram
		Integer numDesenvs = getNumIssueAuthors(releaseNotes);
		if(numDesenvs != null && numDesenvs > 0) {
			StringBuilder desenv = new StringBuilder()
					.append("Desenvolvedores responsáveis")
					.append(": ")
					.append(numDesenvs);
			resumoIssuesVersao.add(desenv.toString());
		}

		textToHighlight
		.append(String.join(" - ", resumoIssuesVersao));

		return textToHighlight.toString();
	}

	private Integer getNumIssueAuthors(VersionReleaseNotes releaseNotes) {
		Integer numDifferentAuthors = 0;
		List<VersionReleaseNoteIssues> issueList = new ArrayList<>();
		if(!releaseNotes.getNewFeatures().isEmpty()) {
			issueList.addAll(releaseNotes.getNewFeatures());
		}
		if(!releaseNotes.getImprovements().isEmpty()) {
			issueList.addAll(releaseNotes.getImprovements());
		}
		if(!releaseNotes.getBugs().isEmpty()) {
			issueList.addAll(releaseNotes.getBugs());
		}
		if(!releaseNotes.getMinorChanges().isEmpty()) {
			issueList.addAll(releaseNotes.getMinorChanges());
		}


		if(issueList != null) {
			Map<JiraUser, Integer> mapAuthors = new HashMap<>();
			for (VersionReleaseNoteIssues issue : issueList) {
				if(issue.getAuthor() != null && !DESENVOLVEDOR_ANONIMO.equals(issue.getAuthor().getName())) {
					Integer numMentions = mapAuthors.get(issue.getAuthor());
					if(numMentions == null) {
						numMentions = 0;
					}
					mapAuthors.put(issue.getAuthor(), ++numMentions);
				}
			}
			numDifferentAuthors = mapAuthors.keySet().size();
		}
		return numDifferentAuthors;
	}

}
