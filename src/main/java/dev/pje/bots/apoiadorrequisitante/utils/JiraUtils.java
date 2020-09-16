package dev.pje.bots.apoiadorrequisitante.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.devplatform.model.jira.JiraEventChangelogItems;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.custom.JiraCustomFieldOption;
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
			versaoAfetada = version.getName();
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
	
	public static String getSprintDoGrupoName(String version) {
		String sprintDoGrupoName = null;
		if(StringUtils.isNotBlank(version)) {
			sprintDoGrupoName = "Sprint " + version;
		}
		return sprintDoGrupoName;
	}
	
	public static String getSiglaTribunal(String provavelSigla) {
		String siglaTribunal = provavelSigla;
		if(StringUtils.isNotBlank(provavelSigla) && provavelSigla.split(" ").length > 1) {
			String[] partesSigla = provavelSigla.split(" ");
			int lengthParte = 0;
			for (String parte : partesSigla) {
				if(parte.length() > lengthParte) {
					siglaTribunal = parte;
					lengthParte = parte.length();
				}
			}
		}
		return siglaTribunal;
	}
	
	/**
	 * Verifia se 
	 * @param options
	 * @param listaNomes
	 * @return
	 */
	public static Boolean compareOptionsAndNames(List<JiraCustomFieldOption> options, List<String> names) {
		Boolean validation = false;
		if(options != null && names != null && options.size() == names.size()) {
			validation = true;
			for (String name : names) {
				boolean nameFounded = false;
				for (JiraCustomFieldOption option : options) {
					if(StringUtils.isNotBlank(option.getValue()) && Utils.compareAsciiIgnoreCase(option.getValue(), name)) {
						nameFounded = true;
						break;
					}
				}
				if(!nameFounded) {
					validation = false;
					break;
				}
			}
		}
		return validation;
	}

	public static JiraCustomFieldOption getParentOptionWithChildName(List<JiraCustomFieldOption> options, String name) {
		JiraCustomFieldOption parentOption = null;
		JiraCustomFieldOption option = null;
		if(options != null && StringUtils.isNotBlank(name)) {
			for (JiraCustomFieldOption opt : options) {
				if(opt.getCascadingOptions() != null) {
					parentOption = opt;
					option = getOptionWithName(opt.getCascadingOptions(), name, true);
					if(option != null) {
						break;
					}
				}
			}
		}
		return parentOption;
	}

	public static JiraCustomFieldOption getOptionWithName(List<JiraCustomFieldOption> options, String name, Boolean recursive) {
		JiraCustomFieldOption option = null;
		if(options != null && StringUtils.isNotBlank(name)) {
			for (JiraCustomFieldOption opt : options) {
				if(StringUtils.isNotBlank(opt.getValue()) && Utils.compareAsciiIgnoreCase(name, opt.getValue())) {
					option = opt;
					break;
				}else if(recursive && opt.getCascadingOptions() != null) {
					option = getOptionWithName(opt.getCascadingOptions(), name, recursive);
					if(option != null) {
						break;
					}
				}
			}
		}
		return option;
	}
	
	public static List<JiraCustomFieldOption> getChildrenOptionsFromOptionName(List<JiraCustomFieldOption> options, String name){
		List<JiraCustomFieldOption> childrenOptions = new ArrayList<>();
		if(options != null && StringUtils.isNotBlank(name)) {
			JiraCustomFieldOption option = getOptionWithName(options, name, false);
			if(option != null) {
				childrenOptions = option.getCascadingOptions();
			}
		}
		return childrenOptions;
	}
}
