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
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;

@FeignClient(name = "jiraDebug", url = "https://webhook.site/0602e93d-4659-40eb-86ba-3ed09dab942f", configuration = JiraClientConfiguration.class)
public interface JiraClientDebug {
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

}
