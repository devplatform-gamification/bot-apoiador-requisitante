package dev.pje.bots.apoiadorrequisitante.clients;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.gitlab.request.GitlabBranchRequest;
import com.devplatform.model.gitlab.request.GitlabCherryPickRequest;
import com.devplatform.model.gitlab.request.GitlabCommitRequest;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.gitlab.response.GitlabRepositoryFile;
import com.devplatform.model.gitlab.response.GitlabRepositoryTree;

import dev.pje.bots.apoiadorrequisitante.annotations.DecodeSlash;

@FeignClient(name = "gitlab", url = "${clients.gitlab.url}", configuration = GitlabClientConfiguration.class)
public interface GitlabClient {

	@GetMapping(value = "/api/v4/user", consumes = "application/json")
	public GitlabUser whoami();
	
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
	

}