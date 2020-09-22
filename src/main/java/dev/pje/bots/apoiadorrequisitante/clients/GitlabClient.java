package dev.pje.bots.apoiadorrequisitante.clients;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devplatform.model.gitlab.GitlabDiscussion;
import com.devplatform.model.gitlab.GitlabNote;
import com.devplatform.model.gitlab.GitlabPipeline;
import com.devplatform.model.gitlab.GitlabProjectExtended;
import com.devplatform.model.gitlab.GitlabProjectVariable;
import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.GitlabTagRelease;
import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.gitlab.request.GitlabAcceptMRRequest;
import com.devplatform.model.gitlab.request.GitlabBranchRequest;
import com.devplatform.model.gitlab.request.GitlabCherryPickRequest;
import com.devplatform.model.gitlab.request.GitlabCommitRequest;
import com.devplatform.model.gitlab.request.GitlabMRCommentRequest;
import com.devplatform.model.gitlab.request.GitlabMRRequest;
import com.devplatform.model.gitlab.request.GitlabMRUpdateRequest;
import com.devplatform.model.gitlab.request.GitlabRepositoryTagRequest;
import com.devplatform.model.gitlab.request.GitlabTagReleaseRequest;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.gitlab.response.GitlabRefCompareResponse;
import com.devplatform.model.gitlab.response.GitlabRepositoryFile;
import com.devplatform.model.gitlab.response.GitlabRepositoryTree;

import dev.pje.bots.apoiadorrequisitante.annotations.DecodeSlash;

@FeignClient(name = "gitlab", url = "${clients.gitlab.url}", configuration = GitlabClientConfiguration.class)
public interface GitlabClient {

	@GetMapping(value = "/api/v4/user", consumes = "application/json")
	public GitlabUser whoami();

	@GetMapping(value = "/api/v4/users?{search}", consumes = "application/json")
	public List<GitlabUser> findUser(
			@SpringQueryMap Map<String, String> search
			);
	
	@GetMapping(value = "/api/v4/projects/{projectId}/repository/files/{filepath}/raw?ref={ref}", consumes = "application/json")
	@DecodeSlash(value = false)
	public String getRawFile(
			@PathVariable("projectId") String projectId,
			@PathVariable("filepath") String filepath,
			@PathVariable("ref") String ref
			);

	@GetMapping(value = "/api/v4/projects/{projectId}/repository/files/{filepath}?ref={ref}", consumes = "application/json")
	@DecodeSlash(value = false)
	public GitlabRepositoryFile getFile(
			@PathVariable("projectId") String projectId,
			@PathVariable("filepath") String filepath,
			@PathVariable("ref") String ref
			);
	

	@GetMapping(value = "/api/v4/projects/{projectId}/repository/tree?path={path}&per_pag=300&ref={ref}", consumes = "application/json")
	public List<GitlabRepositoryTree> getRepositoryTree(
			@PathVariable("projectId") String projectId,
			@PathVariable("path") String path,
			@PathVariable("ref") String ref
			);

	@GetMapping(value = "/api/v4/projects/{projectId}/repository/branches?search={search}", consumes = "application/json")
	public List<GitlabBranchResponse> searchBranches(
			@PathVariable("projectId") String projectId,
			@PathVariable("search") String search
			);
	
	@GetMapping(value = "/api/v4/projects/{projectId}/repository/branches/{branch}", consumes = "application/json")
	public GitlabBranchResponse getSingleRepositoryBranch(
			@PathVariable("projectId") String projectId,
			@PathVariable("branch") String branch
			);
	
	@PostMapping(value = "/api/v4/projects/{projectId}/repository/branches", consumes = "application/json")
	public GitlabBranchResponse createRepositoryBranch(
			@PathVariable("projectId") String projectId,
			@RequestBody GitlabBranchRequest branchRequest
			);
	

	@PostMapping(value = "/api/v4/projects/{projectId}/repository/commits", consumes = "application/json")
	public GitlabCommitResponse sendCommit(
			@PathVariable("projectId") String projectId,
			@RequestBody GitlabCommitRequest codeCommit
			);
	
	@PostMapping(value = "/api/v4/projects/{projectId}/repository/commits/{commitSHA}/cherry_pick", consumes = "application/json")
	public GitlabCommitResponse cherryPick(
			@PathVariable("projectId") String projectId,
			@PathVariable("commitSHA") String commitSHA,
			@RequestBody GitlabCherryPickRequest cherryPick
			);
	
	@GetMapping(value = "/api/v4/projects/{projectId}/repository/tags/{tagname}", consumes = "application/json")
	public GitlabTag getSingleRepositoryTag(
			@PathVariable("projectId") String projectId,
			@PathVariable("tagname") String tagName
			);
	
	@PostMapping(value = "/api/v4/projects/{projectId}/repository/tags", consumes = "application/json")
	public GitlabTag createRepositoryTag(
			@PathVariable("projectId") String projectId,
			@RequestBody GitlabRepositoryTagRequest tagRequest
			);

	@PostMapping(value = "/api/v4/projects/{projectId}/repository/tags/{tagName}/release", consumes = "application/json")
	public GitlabTagRelease createSimpleTagRelease(
			@PathVariable("projectId") String projectId,
			@PathVariable("tagName") String tagName,
			@RequestBody GitlabTagReleaseRequest tagReleaseRequest
			);

	@GetMapping(value = "/api/v4/projects/{projectId}", consumes = "application/json")
	public GitlabProjectExtended getSingleProject(
			@PathVariable("projectId") String projectId
			);
	
	@GetMapping(value = "/api/v4/projects?{search}", consumes = "application/json")
	public List<GitlabProjectExtended> searchProject(
			@SpringQueryMap Map<String, String> search
			);

	@GetMapping(value = "/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}", consumes = "application/json")
	public GitlabMRResponse getSingleMergeRequest(
			@PathVariable("projectId") String projectId,
			@PathVariable("mergeRequestIid") BigDecimal mergeRequestIId
			);

	@GetMapping(value = "/api/v4/projects/{projectId}/merge_requests?{options}", consumes = "application/json")
	public List<GitlabMRResponse> findMergeRequest(
			@PathVariable("projectId") String projectId,
			@SpringQueryMap Map<String, String> options
			);

	@PostMapping(value = "/api/v4/projects/{projectId}/merge_requests", consumes = "application/json")
	public GitlabMRResponse createMergeRequest(
			@PathVariable("projectId") String projectId,
			@RequestBody GitlabMRRequest mergeRequest
			);

	@PutMapping(value = "/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}", consumes = "application/json")
	public GitlabMRResponse updateMergeRequest(
			@PathVariable("projectId") String projectId,
			@PathVariable("mergeRequestIid") BigDecimal mergeRequestIId,
			@RequestBody GitlabMRUpdateRequest updateMerge
			);
	
	@PutMapping(value = "/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}/merge", consumes = "application/json")
	public GitlabMRResponse acceptMergeRequest(
			@PathVariable("projectId") String projectId,
			@PathVariable("mergeRequestIid") BigDecimal mergeRequestIId,
			@RequestBody GitlabAcceptMRRequest acceptMerge
			);
	
	@GetMapping(value = "/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}/pipelines", consumes = "application/json")
	public List<GitlabPipeline> listMRPipelines(
			@PathVariable("projectId") String projectId,
			@PathVariable("mergeRequestIid") BigDecimal mergeRequestIId
			);
	
	@PostMapping(value = "/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}/discussions", consumes = "application/json")
	public GitlabDiscussion createMergeRequestDiscussion(
			@PathVariable("projectId") String projectId,
			@PathVariable("mergeRequestIid") BigDecimal mergeRequestIId,
			@RequestBody GitlabMRCommentRequest mergeRequestDiscussion
			);

	@PostMapping(value = "/api/v4/projects/{projectId}/merge_requests/{mergeRequestIid}/notes", consumes = "application/json")
	public GitlabNote createMergeRequestNote(
			@PathVariable("projectId") String projectId,
			@PathVariable("mergeRequestIid") BigDecimal mergeRequestIId,
			@RequestBody GitlabMRCommentRequest mergeRequestNote
			);

	@GetMapping(value = "/api/v4/projects/{projectId}/repository/compare/?from={fromBranch}&to={toBranch}", consumes = "application/json")
	public GitlabRefCompareResponse compareBranches(
			@PathVariable("projectId") String projectId,
			@PathVariable("fromBranch") String fromBranch,
			@PathVariable("toBranch") String toBranch
			);

	@GetMapping(value = "/api/v4/projects/{projectId}/variables/", consumes = "application/json")
	public List<GitlabProjectVariable> getProjectVariables(
			@PathVariable("projectId") String projectId
			);

	@GetMapping(value = "/api/v4/projects/{projectId}/variables/{variableKey}", consumes = "application/json")
	public GitlabProjectVariable getSingleProjectVariable(
			@PathVariable("projectId") String projectId,
			@PathVariable("variableKey") String variableKey
			);

	@PostMapping(value = "/api/v4/projects/{projectId}/variables/", consumes = "application/json")
	public GitlabProjectVariable createProjectVariable(
			@PathVariable("projectId") String projectId,
			@RequestBody GitlabProjectVariable projectVariable
			);

	@PutMapping(value = "/api/v4/projects/{projectId}/variables/{variableKey}", consumes = "application/json")
	public GitlabProjectVariable changeProjectVariable(
			@PathVariable("projectId") String projectId,
			@PathVariable("variableKey") String variableKey,
			@RequestBody GitlabProjectVariable projectVariable
			);
}