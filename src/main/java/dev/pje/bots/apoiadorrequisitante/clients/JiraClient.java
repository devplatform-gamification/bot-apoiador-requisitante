package dev.pje.bots.apoiadorrequisitante.clients;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransitions;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.custom.JiraCustomField;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;

@FeignClient(name = "jira", url = "${clients.jira.url}", configuration = JiraClientConfiguration.class)
public interface JiraClient {
	@GetMapping(value = "/rest/auth/1/session", consumes = "application/json")
	public Object whoami();
	
	@GetMapping(value = "/rest/api/latest/user?{options}", consumes = "application/json")
	public JiraUser getUserDetails(
			@SpringQueryMap Map<String, String> options);

	@GetMapping(value = "/rest/api/latest/issue/{issueKey}?{options}", consumes = "application/json")
	public JiraIssue getIssueDetails(
			@PathVariable("issueKey") String issueKey, @SpringQueryMap Map<String, String> options);

	@GetMapping(value = "/rest/api/latest/issue/{issueKey}/transitions", consumes = "application/json")
	public JiraIssueTransitions getIssueTransitions(
			@PathVariable("issueKey") String issueKey);

	@PostMapping(value = "/rest/api/latest/issue/{issueKey}/transitions", consumes = "application/json")
	public void changeIssueWithTransition(
			@PathVariable("issueKey") String issueKey, @RequestBody JiraIssueTransitionUpdate issueUpdate);

	@GetMapping(value = "/rest/scriptrunner/latest/custom/customFields/{customField}/option?{options}", consumes = "application/json")
	public JiraCustomField getCustomFieldOptions(
			@PathVariable("customField") String customField, @SpringQueryMap Map<String, String> options);

}
