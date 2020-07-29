package dev.pje.bots.apoiadorrequisitante.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.devplatform.model.jira.JiraEventChangelogItems;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;

public class JiraUtils {

	public static boolean isIssueFromType(JiraIssue issue, Integer issueTypeId) {
		return (issue != null && issue.getFields() != null && issue.getFields().getIssuetype() != null
				&& issue.getFields().getIssuetype().getId() != null && issueTypeId.toString()
						.equals(issue.getFields().getIssuetype().getId().toString()));
	}
	
	public static boolean isIssueInStatus(JiraIssue issue, Integer statusId) {
		boolean issueInStatus = false;
		if(issue != null && statusId != null 
				&& issue.getFields() != null && issue.getFields().getStatus() != null
				&& issue.getFields().getStatus().getId().toString().equals(statusId.toString())) {
			issueInStatus = true;
		}
		return issueInStatus;
	}

	public static boolean isIssueChangingToStatus(JiraEventIssue eventIssue, Integer statusId) {
		boolean issueChangedToStatus = false;
		if(eventIssue.getChangelog() != null) {
			List<JiraEventChangelogItems> changeItems = eventIssue.getChangelog().getItems();
			for (JiraEventChangelogItems changedItem : changeItems) {
				if(changedItem != null && JiraService.FIELD_STATUS.equals(changedItem.getField())) {
					if(statusId.toString().equals(changedItem.getTo())) {
						issueChangedToStatus = true;
						break;
					}
				}
			}
		}
		return issueChangedToStatus;
	}

	
	public static String getVersaoAfetada(List<JiraVersion> versions){
		String versaoAfetada = null;
		if(versions != null && versions.size() == 1){
			JiraVersion version = versions.get(0);
			if(!version.getArchived() && !version.getReleased()){
				versaoAfetada = version.getName();
			}
		}
		return versaoAfetada;
	}
	
	public static String getProjectKeyFromIssueKey(String issueKey) {
		String projectKey = null;
		List<String> issueKeys = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[([A-Za-z]+)\\-[0-9]+\\]");

        Matcher matcher = pattern.matcher(issueKey);
        while(matcher.find()) {
        	String k = matcher.group();
        	issueKeys.add(k);
        	if(StringUtils.isBlank(issueKey)) {
        		projectKey = k;
        	}
        }
        
        return projectKey;
	}
}
