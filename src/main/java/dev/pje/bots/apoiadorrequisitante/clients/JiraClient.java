package dev.pje.bots.apoiadorrequisitante.clients;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueAttachment;
import com.devplatform.model.jira.JiraIssueTransitions;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.custom.JiraCustomField;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;
import com.devplatform.model.jira.response.JiraJQLSearchResponse;
import com.devplatform.model.jira.response.JiraPropertyResponse;

@FeignClient(name = "jira", url = "${clients.jira.url}", configuration = JiraClientConfiguration.class)
public interface JiraClient {
	@GetMapping(value = "/rest/auth/1/session", consumes = MediaType.APPLICATION_JSON_VALUE)
	public Object whoami();
	
	@GetMapping(value = "/rest/api/latest/user?{options}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public JiraUser getUserDetails(
			@SpringQueryMap Map<String, String> options);

	@GetMapping(value = "/rest/api/latest/issue/{issueKey}?{options}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public JiraIssue getIssueDetails(
			@PathVariable("issueKey") String issueKey, @SpringQueryMap Map<String, String> options);

	@GetMapping(value = "/rest/api/latest/issue/{issueKey}/transitions", consumes = MediaType.APPLICATION_JSON_VALUE)
	public JiraIssueTransitions getIssueTransitions(
			@PathVariable("issueKey") String issueKey);

	@PostMapping(value = "/rest/api/latest/issue/{issueKey}/transitions", consumes = MediaType.APPLICATION_JSON_VALUE)
	public void changeIssueWithTransition(
			@PathVariable("issueKey") String issueKey, @RequestBody JiraIssueTransitionUpdate issueUpdate);

	@GetMapping(value = "/rest/scriptrunner/latest/custom/customFields/{customField}/option?{options}", consumes = "application/json")
	public JiraCustomField getCustomFieldOptions(
			@PathVariable("customField") String customField, @SpringQueryMap Map<String, String> options);

	@GetMapping(value = "/rest/api/latest/search?{options}", consumes = "application/json")
	public JiraJQLSearchResponse searchIssuesWithJQL(@SpringQueryMap Map<String, String> options);
	
	@PostMapping(value = "/rest/api/2/issue/{issueKey}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public List<JiraIssueAttachment> sendAttachment(
			@PathVariable("issueKey") String issueKey, @PathVariable(name = "file") MultipartFile file);

	@DeleteMapping(value = "/rest/api/2/attachment/{attachmentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public void removeAttachment(
			@PathVariable("attachmentId") String attachmentId);
	
	@GetMapping(value = "/secure/attachment/{attachmentId}/{attachmentFileName}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public String getAttachmentContent(
			@PathVariable("attachmentId") String attachmentId,
			@PathVariable("attachmentFileName") String attachmentFileName
			);
	
	@GetMapping(value = "/rest/api/2/project/{projectKey}/properties/{propertyKey}", consumes = "application/json")
	public JiraPropertyResponse getProjectProperty(
			@PathVariable("projectKey") String projectKey,
			@PathVariable("propertyKey") String propertyKey);

	@GetMapping(value = "/rest/api/2/user?{options}", consumes = "application/json")
	public JiraUser getUser(
			@SpringQueryMap Map<String, String> options);
	
	@GetMapping(value = "/rest/api/2/user/search?{options}", consumes = "application/json")
	public List<JiraUser> findUser(
			@SpringQueryMap Map<String, String> options);

}
