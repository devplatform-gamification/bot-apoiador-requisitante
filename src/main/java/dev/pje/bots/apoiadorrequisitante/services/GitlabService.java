package dev.pje.bots.apoiadorrequisitante.services;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.GitlabProject;
import com.devplatform.model.gitlab.request.GitlabCherryPickRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionsEnum;
import com.devplatform.model.gitlab.request.GitlabCommitRequest;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.gitlab.response.GitlabRepositoryTree;
import com.devplatform.model.gitlab.vo.GitlabScriptVersaoVO;

import dev.pje.bots.apoiadorrequisitante.clients.GitlabClient;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Service
public class GitlabService {

	private static final Logger logger = LoggerFactory.getLogger(GitlabService.class);

	@Autowired
	private GitlabClient gitlabClient;
	
	@Autowired
	private TelegramService telegramService;
	
	@Autowired
	private SlackService slackService;

	@Value("${clients.gitlab.url}") 
	private String gitlabUrl;
	
	public static final String BRANCH_DEVELOP = "develop";
	public static final String BRANCH_MASTER = "master";
	public static final String BRANCH_RELEASE_CANDIDATE_PREFIX = "release-";
	
	public static final String SCRIPS_MIGRATION_BASE_PATH = "pje-comum/src/main/resources/migrations/";
	public static final String SCRIPT_EXTENSION = ".sql";
	public static final String POMXML = "pom.xml";
	public static final String AUTHOR_NAME = "Bot Revisor do PJe";
	public static final String AUTHOR_EMAIL = "bot.revisor.pje@cnj.jus.br";
	
	public String getBranchVersion(GitlabProject project, String branch) {
		String ref;
		if(BRANCH_MASTER.equals(branch)) {
			ref = branch;
		}else {
			ref = BRANCH_DEVELOP;
		}
		String projectId = project.getId().toString();
		String pomxml = gitlabClient.getRawFile(projectId, POMXML, ref);
		String fullVersion = Utils.getVersionFromPomXML(pomxml);
		
		String version = "";
		if(fullVersion != null && fullVersion.length() > 0) {
			version = fullVersion.split("-")[0];
		}
		return version;
	}
	
	public List<GitlabRepositoryTree> getFilesFromPath(GitlabProject project, String branch, String path) {
		String ref;
		if(BRANCH_MASTER.equals(branch)) {
			ref = branch;
		}else {
			ref = BRANCH_DEVELOP;
		}
		String projectId = project.getId().toString();
		List<GitlabRepositoryTree> listElements = gitlabClient.getRepositoryTree(projectId, path, ref);
		
		List<GitlabRepositoryTree> listFiles = new ArrayList<>();
		for (GitlabRepositoryTree element : listElements) {
			if(!element.getType().equals("tree")) {
				listFiles.add(element);
			}
		}
		
		return listFiles;
	}
	
	public void moveFiles(GitlabProject project, String branch, String lastCommitId,
			List<GitlabScriptVersaoVO> scriptsToChange, String commitMessage) {

		if(scriptsToChange != null && !scriptsToChange.isEmpty()) {
			String projectId = project.getId().toString();
			GitlabCommitRequest commit = new GitlabCommitRequest();
			String id = "";
			try {
				id = URLEncoder.encode(project.getPathWithNamespace(), StandardCharsets.UTF_8.toString());
			} catch (UnsupportedEncodingException e) {
				id = UUID.randomUUID().toString();
			}
			commit.setId(id);
			commit.setBranch(branch);
			commit.commitMessage(commitMessage);
			commit.setAuthorName(AUTHOR_NAME);
			commit.setAuthorEmail(AUTHOR_EMAIL);
			
			List<GitlabCommitActionRequest> actions = new ArrayList<>();
			for (GitlabScriptVersaoVO scriptToChange : scriptsToChange) {
				GitlabCommitActionRequest action = new GitlabCommitActionRequest();
				action.setAction(GitlabCommitActionsEnum.MOVE);
				action.setPrevious_path(scriptToChange.getNameWithPath());
				action.setFile_path(scriptToChange.getNewNameWithPath());
				action.setLast_commit_id(lastCommitId);
				
				actions.add(action);
			}
			commit.setActions(actions);
			
			logger.info(commitMessage);
			telegramService.sendBotMessage(commitMessage);

			try {
				GitlabCommitResponse response = gitlabClient.sendCommit(projectId, commit);
				if(response != null && response.getId() != null) {
					logger.info("ok");
				}
			}catch(Exception e) {
				String errorMessage = "Não foi possível mover os arquivos do commit: " + lastCommitId + "\n"
						+e.getMessage();
				logger.error(errorMessage);
				slackService.sendBotMessage(errorMessage);
				telegramService.sendBotMessage(errorMessage);
			}
		}
	}
	
	public void cherryPick(GitlabProject project, String branchName, List<GitlabCommit> commits) {
		if(project != null && (branchName != null && !branchName.isEmpty()) && commits != null && !commits.isEmpty()) {
			String projectName = project.getName();
			String projectId = project.getId().toString();
			for (GitlabCommit commit : commits) {
				String commitSHA = commit.getId();
				
				GitlabCherryPickRequest cherryPick = new GitlabCherryPickRequest();
				cherryPick.setId(projectId);
				cherryPick.setBranch(branchName);
				cherryPick.setSha(commitSHA);

				String messageToCherryPick = "[GITFLOW][GITLAB] - Project: " + projectName + " - applying commit [" + commitSHA + "] into branch:" + branchName;
				logger.info(messageToCherryPick);
				telegramService.sendBotMessage(messageToCherryPick);

				try {
					GitlabCommitResponse response = gitlabClient.cherryPick(projectId, commitSHA, cherryPick);
					if(response != null && response.getId() != null) {
						logger.info("ok");
					}
				}catch(Exception e) {
					String errorMessage = "[GITFLOW][GITLAB] - Project: " + projectName + " - error trying to apply commit [" + commitSHA + "] into branch "+ branchName +": \n"
							+e.getMessage();
					logger.error(errorMessage);
					slackService.sendBotMessage(errorMessage);
					telegramService.sendBotMessage(errorMessage);
				}
			}
		}
	}
	
	public boolean isMonitoredBranch(GitlabProject project, String branchName) {
		return BRANCH_DEVELOP.equals(branchName) 
				|| branchName.toLowerCase().startsWith(BRANCH_RELEASE_CANDIDATE_PREFIX)
				|| BRANCH_MASTER.equals(branchName);
	}
	
	public boolean isBranchMaster(GitlabProject project, String branchName) {
		return BRANCH_MASTER.equals(branchName);
	}
	
	public boolean isBranchRelease(GitlabProject project, String branchName) {
		return branchName.toLowerCase().startsWith(BRANCH_RELEASE_CANDIDATE_PREFIX);
	}
	
	public String getActualReleaseBranch(GitlabProject project) {
		String actualReleaseBranch = null;
		List<GitlabBranchResponse> branches = gitlabClient.searchBranches(project.getId().toString(), BRANCH_RELEASE_CANDIDATE_PREFIX);
		if(branches != null && !branches.isEmpty()) {
			GitlabBranchResponse lastBranch = null;
			List<Integer> lastVersionNumbers = null;;
			for (GitlabBranchResponse branch : branches) {
				String versionStr = branch.getBranchName().replace(BRANCH_RELEASE_CANDIDATE_PREFIX, "");
				List<Integer> versionNumbers = getVersionFromString(versionStr);
				if(versionNumbers != null && !versionNumbers.isEmpty() && !branch.getMerged()) {
					if(lastBranch == null) {
						lastVersionNumbers = versionNumbers;
						lastBranch = branch;
					}else {
						int diff = 0;
				    	if(versionNumbers != null && lastVersionNumbers != null) {
				    		if(versionNumbers.size() >= lastVersionNumbers.size()) {
				    			diff = compareVersions(versionNumbers, lastVersionNumbers);
				    		}else {
				    			diff = (-1) * compareVersions(lastVersionNumbers, versionNumbers);
				    		}
				    	}
				    	if(diff > 0) {
							lastVersionNumbers = versionNumbers;
							lastBranch = branch;
				    	}
					}
				}
			}
			if(lastBranch != null) {
				actualReleaseBranch = lastBranch.getBranchName();
			}
		}
		return actualReleaseBranch;
	}
	
	public int compareVersions(List<Integer> versionNumbersA, List<Integer> versionNumbersB) {
		int diff = 0;
		if(versionNumbersA != null && versionNumbersB != null) {
			for (int i=0; i < versionNumbersA.size(); i++) {
				if(i < versionNumbersB.size()) {
					diff = versionNumbersA.get(i) - versionNumbersB.get(i);
				}else {
					diff = versionNumbersA.get(i) - 0; // versionB will be considered 0 in this case
				}
				if(diff != 0) {
					break;
				}
			}
		}
		return diff;
	}
	
	public List<Integer> getVersionFromString(String version){
		List<Integer> versionNumbers = new ArrayList<>();
		boolean isValid = false;
		if(version != null && !version.isEmpty()) {
			String[] versionParts = version.split("\\.");
			for(int i=0; i < versionParts.length; i++) {
				if(!StringUtils.isNumericSpace(versionParts[i])) {
					isValid = false;
					break;
				}
				versionNumbers.add(Integer.valueOf(versionParts[i]));
				isValid = true;
			}
		}
		return isValid ? versionNumbers : null;
	}
}