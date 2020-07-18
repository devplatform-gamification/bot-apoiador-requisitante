package dev.pje.bots.apoiadorrequisitante.services;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.GitlabMergeRequestStateEnum;
import com.devplatform.model.gitlab.GitlabPipeline;
import com.devplatform.model.gitlab.GitlabProject;
import com.devplatform.model.gitlab.GitlabProjectExtended;
import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.request.GitlabAcceptMRRequest;
import com.devplatform.model.gitlab.request.GitlabBranchRequest;
import com.devplatform.model.gitlab.request.GitlabCherryPickRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionsEnum;
import com.devplatform.model.gitlab.request.GitlabCommitRequest;
import com.devplatform.model.gitlab.request.GitlabMRRequest;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.gitlab.response.GitlabRepositoryFile;
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
	
	public static final String PROJECT_DOCUMENTACO = "276";
	
	public static final String SCRIPS_MIGRATION_BASE_PATH = "pje-comum/src/main/resources/migrations/";
	public static final String SCRIPT_EXTENSION = ".sql";
	public static final String POMXML = "pom.xml";
	public static final String AUTHOR_NAME = "Bot Revisor do PJe";
	public static final String AUTHOR_EMAIL = "bot.revisor.pje@cnj.jus.br";
	
	public static final String LABEL_MR_LANCAMENTO_VERSAO = "Lancamento de versao";
	
	public static final String GITLAB_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	
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
		String projectId = project.getId().toString();
		return getFilesFromPath(projectId, branch, path);
	}
	public List<GitlabRepositoryTree> getFilesFromPath(String projectId, String branch, String path) {
		String ref;
		if(BRANCH_MASTER.equals(branch)) {
			ref = branch;
		}else {
			ref = BRANCH_DEVELOP;
		}
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
				action.setPreviousPath(scriptToChange.getNameWithPath());
				action.setFilePath(scriptToChange.getNewNameWithPath());
				action.setLastCommitId(lastCommitId);
				
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
	public GitlabCommitResponse sendTextAsFileToBranch(String projectId, GitlabBranchResponse branch, String filePath, String content, String commitMessage) {
		String branchName = branch.getBranchName();

		Map<String, String> files = new HashMap<>();
		files.put(filePath, content);
		return sendTextAsFileToBranch(projectId, branchName, files, commitMessage);
	}
	
	public GitlabCommitResponse sendTextAsFileToBranch(String projectId, String branchName, Map<String, String> files, String commitMessage) {
		GitlabCommitResponse response = null;
		if(files != null && files.size() > 0) {
			GitlabCommitRequest commit = new GitlabCommitRequest();
			commit.setBranch(branchName);
			StringBuilder sb = new StringBuilder();
			sb
				.append("[")
				.append(branchName)
				.append("] ");
			if(commitMessage != null){
				sb.append(commitMessage);
			}else{
				sb.append("Adicionando arquivos");
			}
			commit.setCommitMessage(sb.toString());
			commit.setAuthorName(AUTHOR_NAME);
			commit.setAuthorEmail(AUTHOR_EMAIL);
			
			List<GitlabCommitActionRequest> actionRequestList = new ArrayList<>();
			if(files != null && files.size() > 0) {
				for (Map.Entry<String, String> file : files.entrySet()) {
					String filePath = file.getKey();
					String content = file.getValue();
					
					GitlabCommitActionRequest actionRequest = new GitlabCommitActionRequest();
					GitlabCommitActionsEnum commitAction = GitlabCommitActionsEnum.CREATE;
					// verifica se o arquivo já existe, se já existir substitui
					GitlabRepositoryFile releaseFile = getFile(projectId, filePath, branchName);
					if(releaseFile != null){
						commitAction = GitlabCommitActionsEnum.UPDATE;
					}
					actionRequest.setAction(commitAction);
					actionRequest.setContent(content);
					actionRequest.setFilePath(filePath);
					actionRequestList.add(actionRequest);
				}
			}
			commit.setActions(actionRequestList);
			response = gitlabClient.sendCommit(projectId, commit);
		}
		return response;
	}
	
	public GitlabRepositoryFile getFile(String projectId, String filePath, String ref){
		GitlabRepositoryFile file = null;
		try{
			String filePathEncoded = Utils.urlEncode(filePath);
			file = gitlabClient.getFile(projectId, filePathEncoded, ref);
		}catch (Exception e) {
			if(e instanceof UnsupportedEncodingException){
				logger.error("Filepath could not be used: " + filePath + " - error: " + e.getLocalizedMessage());
			}else{
				logger.error("File not found " + e.getLocalizedMessage());
			}
		}
		return file;
	}
	
	public String getRawFile(String projectId, String filePath, String ref){
		String fileRawContent = null;
		try{
			String filePathEncoded = Utils.urlEncode(filePath);
			fileRawContent = gitlabClient.getRawFile(projectId, filePathEncoded, ref);
		}catch (Exception e) {
			if(e instanceof UnsupportedEncodingException){
				logger.error("Filepath could not be used: " + filePath + " - error: " + e.getLocalizedMessage());
			}else{
				logger.error("File not found " + e.getLocalizedMessage());
			}
		}
		return fileRawContent;
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
	
	public GitlabTag getVersionTag(String projectId, String version) {
		GitlabTag tag = null;
		if(StringUtils.isNotBlank(version) && StringUtils.isNotBlank(projectId)) {
			try {
				tag = gitlabClient.getSingleRepositoryTag(projectId, version);
			}catch (Exception e) {
				logger.error(e.getLocalizedMessage());
			}
		}
		return tag;
	}
	
	public boolean isDevelopDefaultBranch(GitlabProject project) {
		String projectId = project.getId().toString();
		GitlabBranchResponse develop = gitlabClient.getSingleRepositoryBranch(projectId, BRANCH_DEVELOP);
		return develop.getBranchDefault();
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
	
	public GitlabBranchResponse createBranch(String projectId, String branchName, String branchRef) {
		GitlabBranchRequest branch = new GitlabBranchRequest();

		branch.setBranch(branchName);
		branch.setRef(branchRef);
		
		return gitlabClient.createRepositoryBranch(projectId, branch);
	}
	
	public GitlabBranchResponse getSingleRepositoryBranch(String projectId, String branchName){
		GitlabBranchResponse branch = null;
		try{
			branch = gitlabClient.getSingleRepositoryBranch(projectId, branchName);
		}catch (Exception e) {
			logger.error("Branch Not Found: " + e.getLocalizedMessage());
		}
		return branch;
	}
	
	public GitlabBranchResponse createBranchProjetoDocumentacao(String projectId, String branchName) {
		// verifica se já existe o branch, se já existir
		GitlabBranchResponse branch = getSingleRepositoryBranch(projectId, branchName);
		if(branch == null) {
			branch = createBranch(projectId, branchName, BRANCH_MASTER);
		}
		return branch;
	}
	
	public String getActualReleaseBranch(GitlabProject project) {
		return getActualReleaseBranch(project.getId().toString());
	}
	
	public String getActualReleaseBranch(String projectId) {
		String actualReleaseBranch = null;
		List<GitlabBranchResponse> branches = gitlabClient.searchBranches(projectId, BRANCH_RELEASE_CANDIDATE_PREFIX);
		if(branches != null && !branches.isEmpty()) {
			GitlabBranchResponse lastBranch = null;
			List<Integer> lastVersionNumbers = null;;
			for (GitlabBranchResponse branch : branches) {
				String versionStr = branch.getBranchName().replace(BRANCH_RELEASE_CANDIDATE_PREFIX, "");
				List<Integer> versionNumbers = Utils.getVersionFromString(versionStr);
				if(versionNumbers != null && !versionNumbers.isEmpty() && !branch.getMerged()) {
					if(lastBranch == null) {
						lastVersionNumbers = versionNumbers;
						lastBranch = branch;
					}else {
						int diff = 0;
				    	if(versionNumbers != null && lastVersionNumbers != null) {
				    		if(versionNumbers.size() >= lastVersionNumbers.size()) {
				    			diff = Utils.compareVersionsDesc(versionNumbers, lastVersionNumbers);
				    		}else {
				    			diff = (-1) * Utils.compareVersionsDesc(lastVersionNumbers, versionNumbers);
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
	
	public boolean isProjectImplementsGitflow(String projectId) {
		Boolean doesGitflow = false;
		GitlabProjectExtended project = gitlabClient.getSingleProject(projectId);
		if(project != null & StringUtils.isNotBlank(project.getDefaultBranch())) {
			doesGitflow = BRANCH_DEVELOP.equalsIgnoreCase(project.getDefaultBranch());
		}
		return doesGitflow;
	}
	
	public List<GitlabMRResponse> findMergeRequest(String projectId, Map<String, String> options){
		List<GitlabMRResponse> MRs = null;
		try {
			MRs = gitlabClient.findMergeRequest(projectId, options);
		}catch (Exception e) {
			String msgError = "Erro ao buscar MRs do projeto: " + projectId + " - erro: " + e.getLocalizedMessage();
			logger.error(msgError);
			telegramService.sendBotMessage(msgError);
		}
		return MRs;
	}
	
	/**
	 * Pesquisa para saber se o MR já foi pedido
	 * - se não, abre o MR
	 * Com o MR, verifica se o MR possui algum pipeline
	 * - se não, marca: mergeWhenPipelineSucceeds = false
	 * - se sim, marca: mergeWhenPipelineSucceeds = true
	 * Aceita o MR
	 * 
	 * @param projectId
	 * @param branchReleaseName
	 * @param commitMessage
	 */
	public void mergeBranchReleaseIntoMaster(String projectId, String branchReleaseName, String commitMessage) {
		GitlabMRResponse mrOpened = null;
		
		Map<String, String> options = new HashMap<String, String>();
		options.put("state", GitlabMergeRequestStateEnum.OPENED.toString());
		options.put("source_branch", branchReleaseName);
		options.put("target_branch", BRANCH_MASTER);
		List<GitlabMRResponse> MRs = findMergeRequest(projectId, options);
		if(MRs != null && !MRs.isEmpty()) {
			mrOpened = MRs.get(0);
		}else {
			GitlabMRRequest mergeRequest = new GitlabMRRequest();
			mergeRequest.setSourceBranch(branchReleaseName);
			mergeRequest.targetBranch(BRANCH_MASTER);
			mergeRequest.setLabels(LABEL_MR_LANCAMENTO_VERSAO);
			mergeRequest.title(commitMessage);
			mergeRequest.setSquash(false);
			mergeRequest.setRemoveSourceBranch(true);
			
			mrOpened = openMergeRequest(projectId, mergeRequest);
		}
		if(mrOpened != null) {
			Integer numChanges = null;
			if(StringUtils.isNotBlank(mrOpened.getChangesCount())) {
				numChanges = Integer.valueOf(mrOpened.getChangesCount());
			}
			if((numChanges != null && numChanges.equals(0)) || mrOpened.getHasConflicts()) {
				logger.error("Não há alteracoes entre o branch: " + branchReleaseName + " e o branch: " + BRANCH_MASTER);
			}else {
				Boolean hasPipelines = projectHasPipelines(projectId, mrOpened.getIid());
				
				GitlabAcceptMRRequest acceptMerge = new GitlabAcceptMRRequest();
				acceptMerge.setMergeRequestIid(mrOpened.getIid());
				acceptMerge.setId(projectId);
				acceptMerge.setShouldRemoveSourceBranch(true);
				acceptMerge.setMergeWhenPipelineSucceeds(hasPipelines);
				acceptMerge.setSquash(false);
				acceptMerge.setMergeCommitMessage(commitMessage);
				
				GitlabMRResponse mrAccepted = acceptMergeRequest(projectId, mrOpened.getIid(), acceptMerge);
			}
		}
	}
	
	public void changePJePOMVersion(String projectId, String branchName, String version) {
		
	}
	
	public boolean projectHasPipelines(String projectId, BigDecimal mergeRequestIId) {
		List<GitlabPipeline> pipelines = null;
		try {
			pipelines = gitlabClient.listMRPipelines(projectId, mergeRequestIId);
		} catch (Exception e) {
			String errorMessage = "Falhou ao tentar buscar os pipelines do: "+ mergeRequestIId 
				+ " - no projeto: " + projectId + " erro: " + e.getLocalizedMessage();
			logger.error(errorMessage);
			telegramService.sendBotMessage(errorMessage);
		}
		
		return (pipelines != null && !pipelines.isEmpty());
	}
	
	public GitlabMRResponse openMergeRequest(String projectId, GitlabMRRequest mergeRequest) {
		GitlabMRResponse mergeResponse = null;
		try{
			mergeResponse = gitlabClient.createMergeRequest(projectId, mergeRequest);
		}catch (Exception e) {
			String errorMessage = "Falhou ao tentar abrir o MR do branch: "+ mergeRequest.getSourceBranch() 
				+ " - no projeto: " + projectId + " erro: " + e.getLocalizedMessage();
			logger.error(errorMessage);
			telegramService.sendBotMessage(errorMessage);
		}
		
		return mergeResponse;
	}
	
	public GitlabMRResponse acceptMergeRequest(String projectId, BigDecimal mergeRequestIId, GitlabAcceptMRRequest acceptMerge) {
		GitlabMRResponse mergeResponse = null;
		try {
			mergeResponse = gitlabClient.acceptMergeRequest(projectId, mergeRequestIId, acceptMerge);
		}catch (Exception e) {
			String errorMessage = "Falhou ao tentar aceitar o MR: !"+ mergeRequestIId 
				+ " - no projeto: " + projectId + " erro: " + e.getLocalizedMessage();
			logger.error(errorMessage);
			telegramService.sendBotMessage(errorMessage);
		}
		return mergeResponse;
	}
}