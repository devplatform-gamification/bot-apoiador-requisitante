package dev.pje.bots.apoiadorrequisitante.utils.textModels;


import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.VersionReleaseNotes;

import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class NewVersionReleasedSimpleCallTextModel extends AbstractTextModel{
	private VersionReleaseNotes releaseNotes;
	
	public void setReleaseNotes(VersionReleaseNotes releaseNotes) {
		this.releaseNotes = releaseNotes;
	}

	public String convert(MarkdownInterface markdown) {
		
		StringBuilder markdownText = new StringBuilder();
		if(releaseNotes != null && releaseNotes.getVersion() != null && releaseNotes.getVersionType() != null && StringUtils.isNotBlank(releaseNotes.getProject())) {
			if(releaseNotes.getProject().contains("legay") || Utils.compareAsciiIgnoreCase(releaseNotes.getProject(), "PJE")) {
				markdownText.append(markdown.bold("Atualize o PJe-Legacy do seu tribunal!"))
					.append(markdown.newLine());
			}
			StringBuilder titleSb = new StringBuilder();
			titleSb.append("Versão ").append(releaseNotes.getVersion());
			if(releaseNotes.getVersionType().equalsIgnoreCase("hotfix")) {
				titleSb.append(" (").append(releaseNotes.getVersionType()).append(")");
			}
			titleSb.append(" do ").append(releaseNotes.getProject()).append(" disponibilizada");
			markdownText.append(markdown.head3(titleSb.toString()))
				.append(markdown.newLine())
				.append(markdown.normal("Acesse o link para maiores infrmações " + markdown.link("https://t.me/pjenews")));
		}
		
		return markdownText.toString();
	}
}
