package dev.pje.bots.apoiadorrequisitante.utils;


import java.text.ParseException;
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

import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.JiraVersionReleaseNoteIssues;
import com.devplatform.model.jira.JiraVersionReleaseNotes;
import com.devplatform.model.jira.JiraVersionReleaseNotesIssueTypeEnum;

import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class ReleaseNotesConverter {
	
	@Value("${clients.jira.url}")
	private String JIRAURL;
	private static final String PATH_JQL = "/issues/?jql=";
	private static final String PATH_ISSUE = "/browse/";
	private static final String PATH_USERPROFILE = "/secure/ViewProfile.jspa?name=";
	
	private static final String DESENVOLVEDOR_ANONIMO = "desenvolvedor.anonimo";
	
	private MarkdownInterface markdown;

	public ReleaseNotesConverter() {
		super();
	}
	
	public ReleaseNotesConverter(MarkdownInterface markdown) {
		super();
		this.markdown = markdown;
	}

	private String getPathJql(String jql) {
		return JIRAURL + PATH_JQL + jql;
	}

	private String getPathIssue(String issueKey) {
		return JIRAURL + PATH_ISSUE + issueKey;
	}

	private String getPathUserProfile(String userKey) {
		return JIRAURL + PATH_USERPROFILE + userKey;
	}
	
	public String convert(JiraVersionReleaseNotes releaseNotes, MarkdownInterface markdown) {
		this.markdown = markdown;
		StringBuilder jiraMarkdownText = new StringBuilder();
		if(releaseNotes != null && releaseNotes.getVersion() != null && releaseNotes.getVersionType() != null) {
			// título
			String linkToVersion = releaseNotes.getVersion();
			if(StringUtils.isNotBlank(releaseNotes.getJql())) {
				linkToVersion = markdown.link(getPathJql(releaseNotes.getJql()), releaseNotes.getVersion());
			}
			jiraMarkdownText.append(markdown.head1("Versão " + linkToVersion));
			
			if(StringUtils.isNotBlank(releaseNotes.getProject())) {
				jiraMarkdownText.append(markdown.head2(releaseNotes.getProject()));
			}

			// autoria
			jiraMarkdownText
				.append(markdown.link(getPathUserProfile(releaseNotes.getAuthor().getName()), releaseNotes.getAuthor().getName()))
				.append(" disponibilizou esta versão");

			if(StringUtils.isNotBlank(releaseNotes.getReleaseDate())) {
				Date releaseDate;
				try {
					releaseDate = Utils.stringToDate(releaseNotes.getReleaseDate(), null);
					jiraMarkdownText
						.append(" em ")
						.append(Utils.dateToStringPattern(releaseDate, "dd/MM/yyyy"));
				} catch (ParseException e) {
					e.printStackTrace();
				}
			}
			jiraMarkdownText.append(markdown.newLine());

			// tipo de versao + resumo das issues (núm de issues de cada tipo)
			StringBuilder textToHighlight = new StringBuilder()
				.append("Esta é uma versão: ")
				.append(releaseNotes.getVersionType())
				.append(" - ");

			List<String> contadorTipoIssue = new ArrayList<>();
			if(releaseNotes.getNewFeatures() != null && !releaseNotes.getNewFeatures().isEmpty()) {
				StringBuilder tipo = new StringBuilder()
					.append(JiraVersionReleaseNotesIssueTypeEnum.NEW_FEATURE.toString())
					.append(": ")
					.append(releaseNotes.getNewFeatures().size());
				contadorTipoIssue.add(tipo.toString());
			}
			if(releaseNotes.getImprovements() != null && !releaseNotes.getImprovements().isEmpty()) {
				StringBuilder tipo = new StringBuilder()
					.append(JiraVersionReleaseNotesIssueTypeEnum.IMPROVEMENT.toString())
					.append(": ")
					.append(releaseNotes.getImprovements().size());
				contadorTipoIssue.add(tipo.toString());
			}
			if(releaseNotes.getBugs() != null && !releaseNotes.getBugs().isEmpty()) {
				StringBuilder tipo = new StringBuilder()
					.append(JiraVersionReleaseNotesIssueTypeEnum.BUGFIX.toString())
					.append(": ")
					.append(releaseNotes.getBugs().size());
				contadorTipoIssue.add(tipo.toString());
			}
			if(releaseNotes.getMinorChanges() != null && !releaseNotes.getMinorChanges().isEmpty()) {
				StringBuilder tipo = new StringBuilder()
					.append(JiraVersionReleaseNotesIssueTypeEnum.MINOR_CHANGES.toString())
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
			
			jiraMarkdownText
				.append(markdown.highlight(textToHighlight.toString()));
			
			// destaque da versão
			if(StringUtils.isNotBlank(releaseNotes.getVersionHighlights())) {
				jiraMarkdownText
					.append(markdown.quote(releaseNotes.getVersionHighlights()));
			}
			// exibir por tipo
			if(releaseNotes.getNewFeatures() != null && !releaseNotes.getNewFeatures().isEmpty()) {
				jiraMarkdownText
					.append(getIssuesAsList(
								JiraVersionReleaseNotesIssueTypeEnum.NEW_FEATURE.toString(), 
								releaseNotes.getNewFeatures()));
			}
			if(releaseNotes.getImprovements() != null && !releaseNotes.getImprovements().isEmpty()) {
				jiraMarkdownText
				.append(getIssuesAsList(
							JiraVersionReleaseNotesIssueTypeEnum.IMPROVEMENT.toString(), 
							releaseNotes.getImprovements()));
			}
			if(releaseNotes.getBugs() != null && !releaseNotes.getBugs().isEmpty()) {
				jiraMarkdownText
				.append(getIssuesAsList(
							JiraVersionReleaseNotesIssueTypeEnum.BUGFIX.toString(), 
							releaseNotes.getBugs()));
			}
			if(releaseNotes.getMinorChanges() != null && !releaseNotes.getMinorChanges().isEmpty()) {
				jiraMarkdownText
				.append(getIssuesAsList(
							JiraVersionReleaseNotesIssueTypeEnum.MINOR_CHANGES.toString(), 
							releaseNotes.getMinorChanges()));
			}
			
			// desenvolvedores
			if(desenvs != null && !desenvs.isEmpty()) {
				jiraMarkdownText
					.append(markdown.head3("Desenvolvedores"));
				
				for (IssueAuthorPointsVO desenv : desenvs) {
					jiraMarkdownText
						.append("- ")
						.append(markdown.link(getPathUserProfile(desenv.getAuthor().getName()), "@"+desenv.getAuthor().getName()))
						.append(" +")
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
					default:
						icon = markdown.thirdPlaceIco();
						break;
					}
					if(icon != null) {
						jiraMarkdownText.append(icon);
					}
					if(desenv.isMvp()) {
						jiraMarkdownText.append(markdown.MVPIco());
					}
					jiraMarkdownText.append(markdown.newLine());
				}
			}
			
		}else {
			jiraMarkdownText.append(markdown.head1("Não foi possível gerar versão para o jira, não há informações obrigatórias como versão e tipo de versão."));
		}
		
		return jiraMarkdownText.toString();
	}
	
	private String getIssuesAsList(String title, List<JiraVersionReleaseNoteIssues> issuesList) {
		StringBuilder issueList = new StringBuilder();
		issueList
			.append(markdown.head3(title));
		// TODO - ordenar a lista de issues pela prioridade
		for (JiraVersionReleaseNoteIssues issue : issuesList) {
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
	
	private List<IssueAuthorPointsVO> getIssueAuthors(JiraVersionReleaseNotes releaseNotes) {
		List<IssueAuthorPointsVO> authorPointsList = new ArrayList<ReleaseNotesConverter.IssueAuthorPointsVO>();
		
		List<JiraVersionReleaseNoteIssues> issueList = new ArrayList<>();
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
			for (JiraVersionReleaseNoteIssues issue : issueList) {
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
		List<IssueAuthorPointsVO> authorPoints = new ArrayList<ReleaseNotesConverter.IssueAuthorPointsVO>();
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
