package dev.pje.bots.apoiadorrequisitante.utils;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.devplatform.model.jira.JiraEventChangelogItems;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;

public class JiraUtils {

	public static boolean isIssueFromType(JiraIssue issue, String issueTypeId) {
		return (issue != null && issue.getFields() != null && issue.getFields().getIssuetype() != null
				&& issue.getFields().getIssuetype().getId() != null && issueTypeId
						.equals(issue.getFields().getIssuetype().getId().toString()));
	}
	
	public static boolean isIssueInStatus(JiraIssue issue, String statusId) {
		boolean issueInStatus = false;
		if(issue != null && StringUtils.isNotBlank(statusId) 
				&& issue.getFields() != null && issue.getFields().getStatus() != null
				&& issue.getFields().getStatus().getId().toString().equals(statusId)) {
			issueInStatus = true;
		}
		return issueInStatus;
	}

	public static boolean isIssueChangingToStatus(JiraEventIssue eventIssue, String statusId) {
		boolean issueChangedToStatus = false;
		if(eventIssue.getChangelog() != null && StringUtils.isNotBlank(statusId)) {
			List<JiraEventChangelogItems> changeItems = eventIssue.getChangelog().getItems();
			for (JiraEventChangelogItems changedItem : changeItems) {
				if(changedItem != null && JiraService.FIELD_STATUS.equals(changedItem.getField())) {
					if(statusId.equals(changedItem.getTo())) {
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
		String projectKey = issueKey.replaceAll("([A-Za-z]+)\\-[0-9]+", "$1");

        return projectKey;
	}
	
	public static String getFieldNameToJQL(String fieldname) {
		String fieldNameToJQL = null;
		String fieldnamePrefix = "customfield_";
		String fieldId = fieldname.replace(fieldnamePrefix, "");
		
		if(StringUtils.isNotBlank(fieldId)) {
			fieldNameToJQL = "cf[" + fieldId + "]";
		}else {
			fieldNameToJQL = fieldname;
		}
		return fieldNameToJQL;
	}
	
	public static boolean containsVersion(List<JiraVersion> versions, JiraVersion version) {
		boolean contains = false;
		if(versions != null && !versions.isEmpty() && version != null) {
			for (JiraVersion jiraVersion : versions) {
				if(jiraVersion.getId() != null && version.getId() != null) {
					if(jiraVersion.getId().equals(version.getId())) {
						contains = true;
						break;
					}
				}
				if(StringUtils.isNotBlank(jiraVersion.getName()) && jiraVersion.getProjectId() != null &&
						StringUtils.isNotBlank(version.getName()) && version.getProjectId() != null) {
					if(jiraVersion.getName().equals(version.getName()) && jiraVersion.getProjectId().equals(version.getProjectId())) {
						contains = true;
						break;
					}
				}
			}
		}
		return contains;
	}
}
