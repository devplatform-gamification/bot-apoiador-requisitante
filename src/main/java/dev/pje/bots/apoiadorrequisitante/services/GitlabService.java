package dev.pje.bots.apoiadorrequisitante.services;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.GitlabMergeRequestStateEnum;
import com.devplatform.model.gitlab.GitlabPipeline;
import com.devplatform.model.gitlab.GitlabProject;
import com.devplatform.model.gitlab.GitlabProjectExtended;
import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.GitlabTagRelease;
import com.devplatform.model.gitlab.request.GitlabAcceptMRRequest;
import com.devplatform.model.gitlab.request.GitlabBranchRequest;
import com.devplatform.model.gitlab.request.GitlabCherryPickRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionsEnum;
import com.devplatform.model.gitlab.request.GitlabCommitRequest;
import com.devplatform.model.gitlab.request.GitlabMRRequest;
import com.devplatform.model.gitlab.request.GitlabRepositoryTagRequest;
import com.devplatform.model.gitlab.request.GitlabTagReleaseRequest;
import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.gitlab.response.GitlabRefCompareResponse;
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
	public static final String TAG_RELEASE_CANDIDATE_SUFFIX = "-RC";
	
	public static final String PROJECT_DOCUMENTACO = "276";
	
	public static final String SCRIPS_MIGRATION_BASE_PATH = "pje-comum/src/main/resources/migrations/";
	public static final String SCRIPT_EXTENSION = ".sql";
	public static final String FIRST_SCRIPT_PREFIX = "PJE_";
	public static final String FIRST_SCRIPT_SUFFIX = "_001__VERSAO_INICIAL.sql";
	public static final String POMXML = "pom.xml";
	public static final String AUTHOR_NAME = "Bot Revisor do PJe";
	public static final String AUTHOR_EMAIL = "bot.revisor.pje@cnj.jus.br";
	
	public static final String LABEL_MR_LANCAMENTO_VERSAO = "Lancamento de versao";
	
	public static final String GITLAB_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	
	public String getBranchVersion(GitlabProject project, String branch) {
		String projectId = project.getId().toString();
		String pomxml = gitlabClient.getRawFile(projectId, POMXML, branch);
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
		List<GitlabRepositoryTree> listFiles = new ArrayList<>();
		try {
			List<GitlabRepositoryTree> listElements = gitlabClient.getRepositoryTree(projectId, path, branch);
			for (GitlabRepositoryTree element : listElements) {
				if(!element.getType().equals("tree")) {
					listFiles.add(element);
				}
			}
		}catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		}
		
		return listFiles;
	}

	public GitlabCommitResponse moveFiles(GitlabProject project, String branch, String lastCommitId,
			List<GitlabScriptVersaoVO> scriptsToChange, String commitMessage) {
		
		return moveFiles(project.getId().toString(), branch, lastCommitId, scriptsToChange, commitMessage);
	}
	
	public GitlabCommitResponse moveFiles(String projectId, String branch, String lastCommitId,
			List<GitlabScriptVersaoVO> scriptsToChange, String commitMessage) {

		GitlabCommitResponse response = null;
		if(scriptsToChange != null && !scriptsToChange.isEmpty()) {
			GitlabCommitRequest commit = new GitlabCommitRequest();
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
				response = gitlabClient.sendCommit(projectId, commit);
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
		return response;
	}
	
	public GitlabCommitResponse createScriptsDir(String projectId, String branchName, String lastCommitId, String version, String commitMessage) {
		GitlabCommitResponse response = null;
		// monta o path dos scripts
		String destinationPath = GitlabService.SCRIPS_MIGRATION_BASE_PATH + version;
		List<GitlabRepositoryTree> currentScriptList = getFilesFromPath(projectId, branchName, destinationPath);
		// verifica se a pasta ainda não existe
		if(currentScriptList == null || currentScriptList.isEmpty()) {
			// cria a pasta com o novo arquivo
			GitlabCommitRequest commit = new GitlabCommitRequest();
			commit.setBranch(branchName);
			commit.commitMessage(commitMessage);
			commit.setAuthorName(AUTHOR_NAME);
			commit.setAuthorEmail(AUTHOR_EMAIL);
			
			List<GitlabCommitActionRequest> actions = new ArrayList<>();
			
			String firstScriptPath = destinationPath + "/" + FIRST_SCRIPT_PREFIX + version + FIRST_SCRIPT_SUFFIX;

			GitlabCommitActionRequest action = new GitlabCommitActionRequest();
			action.setAction(GitlabCommitActionsEnum.CREATE);
			action.setFilePath(firstScriptPath);
			action.setContent("-- Arquivo inicial dos scripts da versao " + version);
			action.setLastCommitId(lastCommitId);
			actions.add(action);

			commit.setActions(actions);
			
			logger.info(commitMessage);
			telegramService.sendBotMessage(commitMessage);

			try {
				response = gitlabClient.sendCommit(projectId, commit);
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
		}else {
			String message = "Já existe a pasta de scripts da versão: " + version + "";
			logger.info(message);
			slackService.sendBotMessage(message);
			telegramService.sendBotMessage(message);
			
		}
		return response;
	}
	
	public GitlabCommitResponse renameDir(String projectId, String branchName, String lastCommitId,
			String previousPath, String newPath, String commitMessage) {
		
		GitlabCommitResponse response = null;
		if(StringUtils.isNotBlank(previousPath) && StringUtils.isNotBlank(newPath) && !previousPath.equals(newPath)) {
			// busca todos os arquivos de uma determinada pasta previouspath
			List<GitlabRepositoryTree> currentScriptList = getFilesFromPath(projectId, branchName, previousPath);
			// move esses arquivos para a pasta destino newpath

			GitlabCommitRequest commit = new GitlabCommitRequest();
			commit.setBranch(branchName);
			commit.commitMessage(commitMessage);
			commit.setAuthorName(AUTHOR_NAME);
			commit.setAuthorEmail(AUTHOR_EMAIL);
			
			List<GitlabCommitActionRequest> actions = new ArrayList<>();

			if(currentScriptList != null && !currentScriptList.isEmpty()) {
				for (GitlabRepositoryTree currentScript : currentScriptList) {
					String currentFilePath = currentScript.getPath();
					String newFilePath = newPath + "/" + currentScript.getName();
					
					GitlabCommitActionRequest action = new GitlabCommitActionRequest();
					action.setAction(GitlabCommitActionsEnum.MOVE);
					action.setPreviousPath(currentFilePath);
					action.setFilePath(newFilePath);
//					action.setLastCommitId(lastCommitId);
					
					actions.add(action);
				}
				// the folder will be automatically deleted once it is an empty folder
				commit.setActions(actions);
				
				
				try {
					logger.info(commitMessage);
					telegramService.sendBotMessage(commitMessage);
					response = gitlabClient.sendCommit(projectId, commit);
					if(response != null && response.getId() != null) {
						logger.info("ok");
					}
				}catch(Exception e) {
					String errorMessage = "Não foi possível renomear o diretorio: " + previousPath + " para: " + newPath + " do commit: " + lastCommitId + "\n"
							+e.getMessage();
					logger.error(errorMessage);
					slackService.sendBotMessage(errorMessage);
					telegramService.sendBotMessage(errorMessage);
				}
			}
		}
		return response;
	}
	
	public GitlabCommitResponse sendTextAsFileToBranch(String projectId, GitlabBranchResponse branch, String filePath, String content, String commitMessage) {
		String branchName = branch.getBranchName();
		return sendTextAsFileToBranch(projectId, branchName, filePath, content, commitMessage);
	}
	
	public GitlabCommitResponse sendTextAsFileToBranch(String projectId, String branchName, String filePath, String content, String commitMessage) {
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
			if(commitMessage != null){
				sb.append(commitMessage);
			}else{
				sb
				.append("[")
				.append(branchName)
				.append("] ")
				.append("Adicionando arquivos");
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
					// verifica se o arquivo já existe, se já existir o substitui
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
				
				// TODO - verifica se o commit já existe no target branch
				
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
	
	@Cacheable(cacheNames = "project-tag")
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
	
	public GitlabTag createVersionTag(String projectId, String version, String branchName, String tagMessage, String releaseText) {
		GitlabTag tag = null;
		if(StringUtils.isNotBlank(version) && StringUtils.isNotBlank(projectId)) {
			try {
				if(StringUtils.isBlank(branchName)) {
					branchName = BRANCH_MASTER;
				}
				GitlabRepositoryTagRequest tagRequest = new GitlabRepositoryTagRequest();
				tagRequest.setTagName(version);
				tagRequest.setRef(branchName);
				tagRequest.setMessage(tagMessage);
				tagRequest.setReleaseDescription(releaseText);
				
				tag = gitlabClient.createRepositoryTag(projectId, tagRequest);
			}catch (Exception e) {
				logger.error(e.getLocalizedMessage());
			}
		}
		return tag;
	}
	
	public GitlabTagRelease createTagRelease(String projectId, String tagName, String releaseText) {
		GitlabTagRelease tagRelease = null;
		if(StringUtils.isNotBlank(tagName) && StringUtils.isNotBlank(projectId)) {
			try {
				GitlabTagReleaseRequest tagReleaseRequest = new GitlabTagReleaseRequest(releaseText);
				tagRelease = gitlabClient.createSimpleTagRelease(projectId, tagName, tagReleaseRequest);
			}catch (Exception e) {
				logger.error(e.getLocalizedMessage());
			}
		}
		return tagRelease;
		
	}

	public boolean isDevelopDefaultBranch(GitlabProject project) {
		String projectId = project.getId().toString();
		GitlabBranchResponse develop = getSingleRepositoryBranch(projectId, BRANCH_DEVELOP);
		boolean isDevelopDefaultBranch = false;
		if(develop != null) {
			isDevelopDefaultBranch = develop.getBranchDefault();
		}
		return isDevelopDefaultBranch;
	}
	
	public boolean isMonitoredBranch(GitlabProject project, String branchName) {
		return BRANCH_DEVELOP.equals(branchName) 
				|| isBranchRelease(project, branchName)
				|| isBranchMaster(project, branchName);
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
	
	public GitlabBranchResponse createFeatureBranch(String projectId, String branchName) {
		// verifica se já existe o branch, se já existir
		GitlabBranchResponse featureBranch = getSingleRepositoryBranch(projectId, branchName);
		if(featureBranch == null) {
			// verifica se o projeto implementa gitflow, em caso positivo - cria novo branch baseado na develop, caso contrário baseado na master
			if(isProjectImplementsGitflow(projectId)) {
				featureBranch = createBranch(projectId, branchName, BRANCH_DEVELOP);
			}else {
				featureBranch = createBranch(projectId, branchName, BRANCH_MASTER);
			}
		}
		return featureBranch;
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
				    			diff = Utils.compareVersionsDesc(lastVersionNumbers, versionNumbers);
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
	
	public boolean isBranchMergedIntoTarget(String projectId, String branchSource, String branchTarget) {
		boolean branchMerged = false;
		try {
			GitlabRefCompareResponse compareResponse = gitlabClient.compareBranches(projectId, branchTarget, branchSource);
			if(compareResponse != null && compareResponse.getDiffs() != null && compareResponse.getDiffs().isEmpty()) {
				branchMerged = true;
			}
		}catch (Exception e) {
			String msgError = "Erro ao comparar branches do projeto: " + projectId + " - erro: " + e.getLocalizedMessage();
			logger.error(msgError);
			telegramService.sendBotMessage(msgError);
		}
		return branchMerged;
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

	public GitlabMRResponse getMergeRequest(String projectId, BigDecimal mergeRequestIId){
		GitlabMRResponse MR = null;
		try {
			MR = gitlabClient.getSingleMergeRequest(projectId, mergeRequestIId);
		}catch (Exception e) {
			String msgError = "Erro ao buscar MR: " + mergeRequestIId.toString() + " do projeto: " + projectId + " - erro: " + e.getLocalizedMessage();
			logger.error(msgError);
			telegramService.sendBotMessage(msgError);
		}
		return MR;
	}

	/**
	 * 
	 * @param projectId
	 * @param branchReleaseName
	 * @param commitMessage
	 * @throws Exception 
	 */
	public GitlabMRResponse mergeBranchReleaseIntoMaster(String projectId, String sourceBranch, String mergeMessage) throws Exception {
		Boolean squashCommits = false;
		Boolean removeSourceBranch = true;
		
		return mergeSourceBranchIntoBranchTarget(projectId, sourceBranch, BRANCH_MASTER, mergeMessage, 
				LABEL_MR_LANCAMENTO_VERSAO, squashCommits, removeSourceBranch);
	}

	public GitlabMRResponse mergeFeatureBranchIntoBranchDefault(String projectId, String featureBranch, String mergeMessage) throws Exception {

		String targetBranch = BRANCH_MASTER;
		if(isProjectImplementsGitflow(projectId)) {
			targetBranch = BRANCH_DEVELOP;
		}
		Boolean squashCommits = true;
		Boolean removeSourceBranch = true;

		return mergeSourceBranchIntoBranchTarget(projectId, featureBranch, targetBranch, mergeMessage, null, squashCommits, removeSourceBranch);
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
	 * @param sourceBranch
	 * @param targetBranch
	 * @param commitMessage
	 * @param labels
	 * @param squashCommits
	 * @param removeSourceBranch
	 * @return
	 * @throws Exception
	 */
	public GitlabMRResponse mergeSourceBranchIntoBranchTarget(String projectId, String sourceBranch, String targetBranch, 
			String mergeMessage, String labels, Boolean squashCommits, Boolean removeSourceBranch) throws Exception {
		GitlabMRResponse mrOpened = null;
		GitlabMRResponse mrAccepted = null;
		
		if(squashCommits == null) {
			squashCommits = true;
		}
		if(removeSourceBranch == null) {
			removeSourceBranch = true;
		}
		// verifica se o banch da release já foi mergeado no branch master
		boolean releaseBranchMerged = isBranchMergedIntoTarget(projectId, sourceBranch, targetBranch);
		if(!releaseBranchMerged) {
			Map<String, String> options = new HashMap<String, String>();
			options.put("state", GitlabMergeRequestStateEnum.OPENED.toString());
			options.put("source_branch", sourceBranch);
			options.put("target_branch", targetBranch);
			List<GitlabMRResponse> MRs = findMergeRequest(projectId, options);
			if(MRs != null && !MRs.isEmpty()) {
				mrOpened = MRs.get(0);
			}else {
				GitlabMRRequest mergeRequest = new GitlabMRRequest();
				mergeRequest.setSourceBranch(sourceBranch);
				mergeRequest.targetBranch(targetBranch);
				mergeRequest.setLabels(labels);
				mergeRequest.title(mergeMessage);
				mergeRequest.setSquash(squashCommits);
				mergeRequest.setRemoveSourceBranch(removeSourceBranch);
				
				mrOpened = openMergeRequest(projectId, mergeRequest);
			}
			if(mrOpened != null) {
				Integer numChanges = null;
				if(StringUtils.isNotBlank(mrOpened.getChangesCount())) {
					numChanges = Integer.valueOf(mrOpened.getChangesCount());
				}
				if((numChanges != null && numChanges.equals(0))) {
					logger.error("Não há alteracoes entre o branch: " + sourceBranch + " e o branch: " + targetBranch);
				}else {
					if(mrOpened.getHasConflicts()) {
						String msgError = "No projeto: " + projectId + " há um conflito no MR: " + mrOpened.getIid().toString() + "";
						logger.error(msgError);
						telegramService.sendBotMessage(msgError);
						throw new Exception(msgError);
					}else {
						Boolean hasPipelines = projectHasPipelines(projectId, mrOpened.getIid());
						
						GitlabAcceptMRRequest acceptMerge = new GitlabAcceptMRRequest();
						acceptMerge.setMergeRequestIid(mrOpened.getIid());
						acceptMerge.setId(projectId);
						acceptMerge.setMergeWhenPipelineSucceeds(hasPipelines);
						acceptMerge.setSquash(squashCommits);
						acceptMerge.setShouldRemoveSourceBranch(removeSourceBranch);
						acceptMerge.setMergeCommitMessage(mergeMessage);
						
						try {
							mrAccepted = acceptMergeRequest(projectId, mrOpened.getIid(), acceptMerge);
							if(mrAccepted != null && !hasPipelines) {
								// aguarda o MR ser finalizado, pois pode ser que tenha que passar por um pipeline ainda
								Integer maxTries = 10;
								Integer timeToWaitInSeconds = 20;
								Integer numTries = 0;
								while (mrAccepted != null && GitlabMergeRequestStateEnum.OPENED.equals(mrAccepted.getState()) && numTries < maxTries) {
									logger.info("waitting "+timeToWaitInSeconds+" seconds before to check if merge was accepted....");
									Utils.waitSeconds(timeToWaitInSeconds);
									// check merge request again - findMergeRequest
									options = new HashMap<String, String>();
									options.put("source_branch", sourceBranch);
									options.put("target_branch", targetBranch);
									
									mrAccepted = getMergeRequest(projectId, mrOpened.getIid());
								}
							}
						}catch (Exception e) {
							throw new Exception(e.getLocalizedMessage());
						}
					}
				}
			}
		}else {
			logger.info("No projeto: " + projectId + " - o branch "+ sourceBranch + " - já foi integrado ao branch: " + targetBranch);
		}
		return mrAccepted;
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
	
	public GitlabMRResponse acceptMergeRequest(String projectId, BigDecimal mergeRequestIId, GitlabAcceptMRRequest acceptMerge) throws Exception {
		GitlabMRResponse mergeResponse = null;
		try {
			mergeResponse = gitlabClient.acceptMergeRequest(projectId, mergeRequestIId, acceptMerge);
		}catch (Exception e) {
			String errorMessage = "Falhou ao tentar aceitar o MR: !"+ mergeRequestIId 
				+ " - no projeto: " + projectId + " erro: " + e.getLocalizedMessage();
			logger.error(errorMessage);
			telegramService.sendBotMessage(errorMessage);
			
			throw new Exception(errorMessage);
		}
		return mergeResponse;
	}
	
	public GitlabCommitResponse atualizaVersaoPom(String gitlabProjectId, String branchName, List<String> pomsList, 
			String newVersion, String actualVersion, String commitMessage) {
		GitlabCommitResponse response = null;
		
		if(StringUtils.isNotBlank(newVersion) && StringUtils.isNotBlank(actualVersion) && !actualVersion.equalsIgnoreCase(newVersion)) {
			if (pomsList != null && !pomsList.isEmpty()) {
				Map<String, String> poms = new HashMap<>();
				for (String pomFilePath : pomsList) {
					String pomContent = getRawFile(gitlabProjectId, pomFilePath, branchName);
					if (StringUtils.isNotBlank(pomContent)) {
						String pomContentChanged = Utils.changePomXMLVersion(actualVersion, newVersion, pomContent);
						poms.put(pomFilePath, pomContentChanged);
					} else {
						String msgError = " Error trying to get pom (" + pomFilePath + ") content.";
						logger.error(msgError);
						telegramService.sendBotMessage(msgError);
					}
				}
				if (poms != null && poms.size() > 0 && poms.size() == pomsList.size()) {
					response = sendTextAsFileToBranch(gitlabProjectId, branchName,
							poms, commitMessage);
				}
			}
		}
		return response;
	}
	
	public List<String> getListPathPoms(String projectId) {
		List<String> listaPoms = new ArrayList<String>();
		if ("7".equals(projectId)) { // TODO - colocar isso na parametrizacao do projeto no gitlab
			listaPoms.add("pom.xml");
			listaPoms.add("pje-comum/pom.xml");
			listaPoms.add("pje-web/pom.xml");
		} else {
			listaPoms.add("pom.xml"); // FIXME - verificar para os outros projetos
		}
		return listaPoms;
	}
	
	public String getActualVersion(String gitlabProjectId, String branchName) {
		String defaultPomFile = "pom.xml"; // FIXME
		String actualVersion = null;
		String pomContent = getRawFile(gitlabProjectId, defaultPomFile, branchName);
		if (StringUtils.isNotBlank(pomContent)) {
			actualVersion = Utils.getVersionFromPomXML(pomContent);
		}
		
		return actualVersion;
	}
	
	public void atualizaNumeracaoPastaScriptsVersao(String projectId, String branchName, String lastCommitId, String newVersion, String actualVersion, String commitMessage) {
		if(StringUtils.isNotBlank(newVersion) && StringUtils.isNotBlank(actualVersion)) {
			actualVersion = actualVersion.replaceAll("\\-SNAPSHOT", "");
			if(!actualVersion.equalsIgnoreCase(newVersion)) {
				String previousPath = GitlabService.SCRIPS_MIGRATION_BASE_PATH + actualVersion;
				String newPath = GitlabService.SCRIPS_MIGRATION_BASE_PATH + newVersion;
				
				renameDir(projectId, branchName, lastCommitId, previousPath, newPath, commitMessage);
			}
		}
	}



}