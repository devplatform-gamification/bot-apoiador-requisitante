package dev.pje.bots.apoiadorrequisitante.utils.textModels;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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

import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class ReleaseNotesTextModel implements AbstractTextModel{
	
	@Value("${clients.jira.url}")
	private String JIRAURL;
	@Value("${project.documentation.url}")
	private String DOCSURL;
	@Value("${project.telegram.channel.url}")
	private String TELEGRAM_CHANNEL_URL;
	@Value("${project.telegram.channel.name}")
	private String TELEGRAM_CHANNEL_NAME;

	private static final String DESENVOLVEDOR_ANONIMO = "desenvolvedor.anonimo";
	
	private VersionReleaseNotes releaseNotes;
	private MarkdownInterface markdown;	

	private String getPathJql(String jql) {
		return JiraUtils.getPathJql(jql, JIRAURL);
	}
	
	private String getPathIssue(String issueKey) {
		return JiraUtils.getPathIssue(issueKey, JIRAURL);
	}

	private String getPathUserProfile(String userKey) {
		return JiraUtils.getPathUserProfile(userKey, JIRAURL);
	}
	
	public void setReleaseNotes(VersionReleaseNotes releaseNotes) {
		this.releaseNotes = releaseNotes;
	}
	
	public String convert(MarkdownInterface markdown) {
		this.markdown = markdown;
		StringBuilder markdownText = new StringBuilder();
		if(releaseNotes != null && releaseNotes.getVersion() != null && releaseNotes.getVersionType() != null) {
			markdownText.append(getSpecificMarkdownCode(releaseNotes));
			// título
			String linkToVersion = releaseNotes.getVersion();
			if(StringUtils.isNotBlank(releaseNotes.getJql())) {
				linkToVersion = markdown.link(getPathJql(releaseNotes.getJql()), releaseNotes.getVersion());
			}
			markdownText.append(markdown.head2("Versão " + linkToVersion));
			
			if(StringUtils.isNotBlank(releaseNotes.getProject())) {
				markdownText.append(markdown.head3(releaseNotes.getProject()));
			}

			// autoria
			markdownText
				.append(markdown.link(getPathUserProfile(releaseNotes.getAuthor().getName()), releaseNotes.getAuthor().getName()))
				.append(" disponibilizou esta versão");

			if(StringUtils.isNotBlank(releaseNotes.getReleaseDate())) {
				Date releaseDate;
				try {
					releaseDate = getDateFromReleaseDate(releaseNotes.getReleaseDate());
					markdownText
						.append(" em ")
						.append(Utils.dateToStringPattern(releaseDate, "dd/MM/yyyy"));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			markdownText.append(markdown.newLine());

			// tipo de versao + resumo das issues (núm de issues de cada tipo)
			StringBuilder textToHighlight = new StringBuilder();
			
			
			if(!Utils.compareAsciiIgnoreCase(releaseNotes.getVersionType(), JiraIssueTipoVersaoEnum.ORDINARIA.toString())) {
				textToHighlight.append("Esta é uma versão: ")
					.append(releaseNotes.getVersionType())
					.append(" - ");
			}

			List<String> contadorTipoIssue = new ArrayList<>();
			if(releaseNotes.getNewFeatures() != null && !releaseNotes.getNewFeatures().isEmpty()) {
				StringBuilder tipo = new StringBuilder()
					.append(VersionReleaseNotesIssueTypeEnum.NEW_FEATURE.toString())
					.append(": ")
					.append(releaseNotes.getNewFeatures().size());
				contadorTipoIssue.add(tipo.toString());
			}
			if(releaseNotes.getImprovements() != null && !releaseNotes.getImprovements().isEmpty()) {
				StringBuilder tipo = new StringBuilder()
					.append(VersionReleaseNotesIssueTypeEnum.IMPROVEMENT.toString())
					.append(": ")
					.append(releaseNotes.getImprovements().size());
				contadorTipoIssue.add(tipo.toString());
			}
			if(releaseNotes.getBugs() != null && !releaseNotes.getBugs().isEmpty()) {
				StringBuilder tipo = new StringBuilder()
					.append(VersionReleaseNotesIssueTypeEnum.BUGFIX.toString())
					.append(": ")
					.append(releaseNotes.getBugs().size());
				contadorTipoIssue.add(tipo.toString());
			}
			if(releaseNotes.getMinorChanges() != null && !releaseNotes.getMinorChanges().isEmpty()) {
				StringBuilder tipo = new StringBuilder()
					.append(VersionReleaseNotesIssueTypeEnum.MINOR_CHANGES.toString())
					.append(": ")
					.append(releaseNotes.getMinorChanges().size());
				contadorTipoIssue.add(tipo.toString());
			}
			// contador de pessoas que contribuíram, dar destaque às pessoas que mais contribuiram
			List<IssueAuthorPointsVO> desenvs = getIssueAuthors(releaseNotes);
			if(desenvs != null && !desenvs.isEmpty()) {
				StringBuilder desenv = new StringBuilder()
					.append("Desenvolvedores")
					.append(": ")
					.append(desenvs.size());
				contadorTipoIssue.add(desenv.toString());
			}
			
			textToHighlight
				.append(String.join(" - ", contadorTipoIssue));
			
			markdownText
				.append(markdown.highlight(textToHighlight.toString()));
			
			// destaque da versão
			if(StringUtils.isNotBlank(releaseNotes.getVersionHighlights())) {
				markdownText
					.append(markdown.quote(releaseNotes.getVersionHighlights()));
			}
			// exibir por tipo
			if(releaseNotes.getNewFeatures() != null && !releaseNotes.getNewFeatures().isEmpty()) {
				markdownText
					.append(getIssuesAsList(
								VersionReleaseNotesIssueTypeEnum.NEW_FEATURE.toString(), 
								releaseNotes.getNewFeatures()));
			}
			if(releaseNotes.getImprovements() != null && !releaseNotes.getImprovements().isEmpty()) {
				markdownText
				.append(getIssuesAsList(
							VersionReleaseNotesIssueTypeEnum.IMPROVEMENT.toString(), 
							releaseNotes.getImprovements()));
			}
			if(releaseNotes.getBugs() != null && !releaseNotes.getBugs().isEmpty()) {
				markdownText
				.append(getIssuesAsList(
							VersionReleaseNotesIssueTypeEnum.BUGFIX.toString(), 
							releaseNotes.getBugs()));
			}
			if(releaseNotes.getMinorChanges() != null && !releaseNotes.getMinorChanges().isEmpty()) {
				markdownText
				.append(getIssuesAsList(
							VersionReleaseNotesIssueTypeEnum.MINOR_CHANGES.toString(), 
							releaseNotes.getMinorChanges()));
			}
			
			// desenvolvedores
			if(desenvs != null && !desenvs.isEmpty()) {
				markdownText
					.append(markdown.head4("Desenvolvedores"));
				
				for (IssueAuthorPointsVO desenv : desenvs) {
					markdownText
						.append("- ")
						.append(markdown.link(getPathUserProfile(desenv.getAuthor().getName()), "@"+desenv.getAuthor().getName()));
					
					if(desenv.getPoints() > 1) {
						markdownText.append(" x")
							.append(desenv.getPoints())
							.append(" ");
						
						String icon = null;
						switch (desenv.getClassification()) {
						case 1:
							icon = markdown.firstPlaceIco();
							break;
						case 2:
							icon = markdown.secondPlaceIco();
							break;
						case 3:
							icon = markdown.thirdPlaceIco();
							break;
						}
						if(icon != null) {
							markdownText.append(icon);
						}
					}
					if(desenv.isMvp()) {
						markdownText.append(markdown.MVPIco());
					}
					markdownText.append(markdown.newLine());
				}
			}
			
			// outras informações
			markdownText
				.append(markdown.head4("Outras informações"))
				.append(markdown.listItem("Link desta versão no jira: " + linkToVersion))
				.append(markdown.listItem("Veja outros release notes: " 
						+ markdown.link(DOCSURL + "/servicos-negociais/pje-legacy/release-notes/index.html", "aqui")))
				.append(markdown.listItem("Para mais informações, acesse a documentação do projeto em " 
						+ markdown.link(DOCSURL)))
				.append(markdown.highlight("TIP: Acompanhe as notícias do PJe em primeira-mão no canal (público) do telegram: " 
						+ markdown.link(TELEGRAM_CHANNEL_URL, "@" + TELEGRAM_CHANNEL_NAME)));
		}else {
			markdownText.append(markdown.head2("Não foi possível gerar versão para o jira, não há informações obrigatórias como versão e tipo de versão."));
		}
		
		return markdownText.toString();
	}
	
	private Date getDateFromReleaseDate(String releaseDateStr) {
		return Utils.getDateFromString(releaseDateStr);
	}
	
	private String getSpecificMarkdownCode(VersionReleaseNotes releaseNotes) {
		StringBuilder sb = new StringBuilder();
		if(markdown.getName().equals(MarkdownInterface.MARKDOWN_ASCIIDOC)) {
			sb.append(":releaseVersion: ")
				.append(releaseNotes.getVersion())
				.append(markdown.newLine());
			if(StringUtils.isNotBlank(releaseNotes.getReleaseDate())) {
				Date releaseDate;
				releaseDate = Utils.getDateFromString(releaseNotes.getReleaseDate());
				sb.append(":releaseDate: ")
				.append(Utils.dateToStringPattern(releaseDate, "dd/MM/yyyy"))
				.append(markdown.newLine());
			}
			sb.append("include::{docdir}/servicos-negociais/pje-legacy/_service-attributes.adoc[]")
				.append(markdown.newLine())
				.append("include::{docdir}/servicos-negociais/_general-attributes.adoc[]")
				.append(markdown.newLine())
				.append(markdown.newLine())
				.append("[#{serviceTitle}-v" + releaseNotes.getVersion().replaceAll("\\.", "-") + "]")
				.append(markdown.newLine());
		}
		
		return sb.toString();
	}
	
	private String getIssuesAsList(String title, List<VersionReleaseNoteIssues> issuesList) {
		StringBuilder issueList = new StringBuilder();
		issueList
			.append(markdown.head4(title));
		// TODO - ordenar a lista de issues pela prioridade
		for (VersionReleaseNoteIssues issue : issuesList) {
			String authorName = DESENVOLVEDOR_ANONIMO;
			if(issue.getAuthor() != null) {
				authorName = issue.getAuthor().getName();
			}
			issueList
				.append("- ")
				.append(issue.getSummary())
				.append(" (")
				.append(markdown.link(getPathIssue(issue.getIssueKey()), issue.getIssueKey()));
			if(!DESENVOLVEDOR_ANONIMO.equals(authorName)) {
				issueList
					.append(" por ")
					.append(markdown.link(getPathUserProfile(authorName), "@"+authorName));
			}
			issueList
				.append(") ");
			
			if(StringUtils.isNotBlank(issue.getReleaseObservation())) {
				issueList
					.append(markdown.quote(issue.getReleaseObservation()));
			}
			issueList
				.append(markdown.newLine());
		}
		
		return issueList.toString();
	}
	
	private class IssueAuthorPointsVO{
		private JiraUser author;
		private int points = 0;
		private int classification = 0;
		private boolean mvp = false;
		
		public IssueAuthorPointsVO(JiraUser author, int points) {
			super();
			this.author = author;
			this.points = points;
		}
		public JiraUser getAuthor() {
			return author;
		}
		public int getPoints() {
			return points;
		}
		
		public int getClassification() {
			return classification;
		}
		public void setClassification(int classification) {
			this.classification = classification;
		}
		
		public boolean isMvp() {
			return mvp;
		}
		public void setMvp(boolean mvp) {
			this.mvp = mvp;
		}
		@Override
		public String toString() {
			return "IssueAuthorPointsVO [author=" + author + ", points=" + points + ", classification=" + classification
					+ ", mvp=" + mvp + "]";
		}
	}
	
	private List<IssueAuthorPointsVO> getIssueAuthors(VersionReleaseNotes releaseNotes) {
		List<IssueAuthorPointsVO> authorPointsList = new ArrayList<ReleaseNotesTextModel.IssueAuthorPointsVO>();
		
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
			authorPointsList = convertToAuthorPointsList(mapAuthors);
			authorPointsList = computeClassification(authorPointsList, issueList.size());
		}
		return authorPointsList;
	}
	
	private List<IssueAuthorPointsVO> convertToAuthorPointsList(Map<JiraUser, Integer> mapAuthors){
		List<IssueAuthorPointsVO> authorPoints = new ArrayList<ReleaseNotesTextModel.IssueAuthorPointsVO>();
		for (JiraUser author : mapAuthors.keySet()) {
			Integer points = mapAuthors.get(author);
			authorPoints.add(new IssueAuthorPointsVO(author, points));
		}
		
		return authorPoints;
	}
	
	private List<IssueAuthorPointsVO> computeClassification(List<IssueAuthorPointsVO> authorPointsList, Integer totalPoints){
		Collections.sort(authorPointsList, new SortAuthorPoints());
		int classification = 0;
		int lastPoints = -1;
		for(int i=0; i< authorPointsList.size(); i++) {
			if (authorPointsList.get(i).getPoints() != lastPoints) {
				classification = (i + 1);
				lastPoints = authorPointsList.get(i).getPoints();
			}
			authorPointsList.get(i).setClassification(classification);
			/**
			 * Se o desenvolvedor tiver feito mais de 50% das issues da versão em uma versão com mais de 5 issues
			 * ele será declarado MVP da versão
			 */
			if(((authorPointsList.get(i).getPoints() / totalPoints) * 100 > 50) && (totalPoints > 5)) {
				authorPointsList.get(i).setMvp(true);
			}
		}
		
		return authorPointsList;
	}
	
	class SortAuthorPoints implements Comparator<IssueAuthorPointsVO>{ 
	    public int compare(IssueAuthorPointsVO a, IssueAuthorPointsVO b) 
	    { 
	    	int diff = (-1) * (a.getPoints() - b.getPoints()); // points in reverse order
	    	if(diff == 0 && a.getAuthor() != null && b.getAuthor() != null) {
	    		diff = a.getAuthor().getName().compareToIgnoreCase(b.getAuthor().getName()); // names in ascending order
	    	}
	        return diff; 
	    } 
	}
}
