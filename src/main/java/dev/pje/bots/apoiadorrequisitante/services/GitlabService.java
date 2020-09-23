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

import com.devplatform.model.bot.VersionTypeEnum;
import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.GitlabDiscussion;
import com.devplatform.model.gitlab.GitlabMergeRequestAttributes;
import com.devplatform.model.gitlab.GitlabMergeRequestStateEnum;
import com.devplatform.model.gitlab.GitlabNote;
import com.devplatform.model.gitlab.GitlabPipeline;
import com.devplatform.model.gitlab.GitlabProject;
import com.devplatform.model.gitlab.GitlabProjectExtended;
import com.devplatform.model.gitlab.GitlabProjectVariable;
import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.gitlab.GitlabTagRelease;
import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.gitlab.request.GitlabAcceptMRRequest;
import com.devplatform.model.gitlab.request.GitlabBranchRequest;
import com.devplatform.model.gitlab.request.GitlabCherryPickRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionsEnum;
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
import com.devplatform.model.gitlab.vo.GitlabCommitFileVO;
import com.devplatform.model.gitlab.vo.GitlabMergeRequestVO;
import com.devplatform.model.gitlab.vo.GitlabScriptVersaoVO;

import dev.pje.bots.apoiadorrequisitante.clients.GitlabClient;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.utils.GitlabUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import feign.RetryableException;

@Service
public class GitlabService {

	private static final Logger logger = LoggerFactory.getLogger(GitlabService.class);

	@Autowired
	private GitlabClient gitlabClient;
	
	@Autowired
	private TelegramService telegramService;
	
	@Autowired
	private SlackService slackService;

	@Autowired
	private RocketchatService rocketchatService;

	@Value("${clients.gitlab.url}")
	private String gitlabUrl;
	
	public static final String BRANCH_DEVELOP = "develop";
	public static final String BRANCH_MASTER = "master";
	public static final String BRANCH_RELEASE_CANDIDATE_PREFIX = "release-";
	public static final String TAG_RELEASE_CANDIDATE_SUFFIX = "-RC";
	
	public static final String PROJECT_DOCUMENTACAO = "276";
	public static final String PROJECT_PJE = "7";
	
	public static final String SCRIPS_MIGRATION_BASE_PATH = "pje-comum/src/main/resources/migrations/";
	public static final String SCRIPT_EXTENSION = ".sql";
	public static final String FIRST_SCRIPT_PREFIX = "PJE_";
	public static final String FIRST_SCRIPT_SUFFIX = "_001__VERSAO_INICIAL.sql";
	public static final String POMXML = "pom.xml";
	public static final String AUTHOR_NAME = "Bot Revisor do PJe";
	public static final String AUTHOR_EMAIL = "bot.revisor.pje@cnj.jus.br";
	
	public static final String LABEL_MR_LANCAMENTO_VERSAO = "Lancamento de versao";
	public static final String PREFIXO_LABEL_APROVACAO_TRIBUNAL = "Aprovado";
	
	public static final String GITLAB_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS";
	
	public static final String PROJECT_PROPERTY_JIRA_MAIN_RELATED_PROJECT = "JIRA_MAIN_RELATED_PROJECT";
	public static final String PROJECT_PROPERTY_POM_VERSION_TAGNAME = "POM_TAGNAME_PROJECT_VERSION";
	
	public static final String JIRA_MAIN_RELATED_PROJECT_DEFAULT = "PJEII";
	public static final String POM_TAGNAME_PROJECT_VERSION_DEFAULT = "project/version";
	
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
				rocketchatService.sendBotMessage(errorMessage);
				telegramService.sendBotMessage(errorMessage);
			}
		}
		return response;
	}
	
	public String getScriptsMigrationBasePath(String projectId) {
		String scriptsMigrationBasePath = null;
		if(StringUtils.isNotBlank(projectId)) {
			if(projectId.equals(PROJECT_PJE)) {
				scriptsMigrationBasePath = GitlabService.SCRIPS_MIGRATION_BASE_PATH;
			}
		}
		return scriptsMigrationBasePath;
	}
	
	public GitlabCommitResponse createScriptsDir(String projectId, String branchName, String lastCommitId, String version, String commitMessage) {
		GitlabCommitResponse response = null;
		// monta o path dos scripts
		String scriptsMigrationBasePath = getScriptsMigrationBasePath(projectId);
		if(StringUtils.isNotBlank(scriptsMigrationBasePath)) {
			String destinationPath = scriptsMigrationBasePath + version;
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
					rocketchatService.sendBotMessage(errorMessage);
					telegramService.sendBotMessage(errorMessage);
				}
			}else {
				String message = "Já existe a pasta de scripts da versão: " + version + "";
				logger.info(message);
				slackService.sendBotMessage(message);
				rocketchatService.sendBotMessage(message);
				telegramService.sendBotMessage(message);
				
			}
		}else {
			String message = "Não há pasta de sciprts para o projeto: " + projectId + "";
			logger.info(message);
			slackService.sendBotMessage(message);
			rocketchatService.sendBotMessage(message);
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
					rocketchatService.sendBotMessage(errorMessage);
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
	
	public GitlabCommitResponse sendTextAsFileToBranch(String projectId, String branchName, Map<String, String> mapFiles, String commitMessage) {
		List<GitlabCommitFileVO> files = new ArrayList<>();
		if(mapFiles != null && mapFiles.size() > 0) {
			for (Map.Entry<String, String> mapFile : mapFiles.entrySet()) {
				String filePath = mapFile.getKey();
				String content = mapFile.getValue();
				
				GitlabCommitFileVO file = new GitlabCommitFileVO(filePath, content, false);
				files.add(file);
			}
		}
		return sendFilesToBranch(projectId, branchName, files, commitMessage);
	}
	
	public GitlabCommitResponse sendFilesToBranch(String projectId, String branchName, List<GitlabCommitFileVO> files, String commitMessage) {
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
				for (GitlabCommitFileVO file : files) {
					String filePath = file.getPath();
					String content = file.getContent();
					boolean isBase64 = file.getBase64();
					
					GitlabCommitActionRequest actionRequest = new GitlabCommitActionRequest();
					GitlabCommitActionsEnum commitAction = GitlabCommitActionsEnum.CREATE;
					// verifica se o arquivo já existe, se já existir o substitui
					GitlabRepositoryFile releaseFile = getFile(projectId, filePath, branchName);
					if(releaseFile != null){
						commitAction = GitlabCommitActionsEnum.UPDATE;
					}
					actionRequest.setAction(commitAction);
					
					if(!isBase64 && StringUtils.isNotBlank(content)) {
						content = Utils.textToBase64(content);
						isBase64 = true;
					}
					
					actionRequest.setContent(content);
					actionRequest.setFilePath(filePath);
					if(isBase64) {
						actionRequest.setEncoding("base64");
					}else {
						actionRequest.setEncoding("text");
					}
					actionRequestList.add(actionRequest);
				}
			}
			commit.setActions(actionRequestList);
			logger.info("commit string: " + Utils.convertObjectToJson(commit));
			response = sendCommit(projectId, commit);
		}
		return response;
	}
	
	public GitlabCommitResponse sendCommit(String projectId, GitlabCommitRequest commit) {
		GitlabCommitResponse response = null;
		try {
			response = gitlabClient.sendCommit(projectId, commit);	
		}catch (Exception e) {
			String errorMessage = "[GITLAB] - Project: " + projectId + " - error trying to send commit [" + commit.getCommitMessage() + "]: \n"
					+e.getMessage();
			logger.error(errorMessage);
			slackService.sendBotMessage(errorMessage);
			rocketchatService.sendBotMessage(errorMessage);
			telegramService.sendBotMessage(errorMessage);
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
	
	public String getDefaultBranch(String projectId) {
		String branchDefault = BRANCH_MASTER;
		GitlabProjectExtended project = getProjectDetails(projectId);
		if(project != null && StringUtils.isNotBlank(project.getDefaultBranch())) {
			branchDefault = project.getDefaultBranch();
		}
		return branchDefault;
	}
	
	public String getRawFileFromDefaultBranch(String projectId, String filePath){
		String branchDefault = getDefaultBranch(projectId);
		return getRawFile(projectId, filePath, branchDefault);
	}
	
	public String getRawFile(String projectId, String filePath, String ref){
		String fileRawContent = null;
		try{
			String filePathEncoded = Utils.urlEncode(filePath);
			fileRawContent = gitlabClient.getRawFile(projectId, filePathEncoded, ref);
		}catch (Exception e) {
			if(e instanceof UnsupportedEncodingException){
				logger.error("Filepath could not be used: " + filePath + " - error: " + e.getLocalizedMessage());
			}else if(e instanceof RetryableException) {
				logger.error(e.getLocalizedMessage()); // conexao recusada
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
					rocketchatService.sendBotMessage(errorMessage);
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
		boolean isDevelopDefaultBranch = false;
		if(project != null) {
			if(StringUtils.isNotBlank(project.getDefaultBranch())) {
				isDevelopDefaultBranch = BRANCH_DEVELOP.equals(project.getDefaultBranch());
			}
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
		
		GitlabBranchResponse response = null;
		try {
			response = getSingleRepositoryBranch(projectId, branchName);
			if(response == null) {
				response = gitlabClient.createRepositoryBranch(projectId, branch);
			}
		}catch (Exception e) {
			String errorMessage = "Erro ao tentar criar o branch: " + branchName 
					+ "no projeto: " + projectId+": \n"
					+e.getMessage();
			logger.error(errorMessage);
			slackService.sendBotMessage(errorMessage);
			rocketchatService.sendBotMessage(errorMessage);
			telegramService.sendBotMessage(errorMessage);
		}
		
		return response;
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
	
	@Cacheable(cacheNames = "project-gitflow")
	public boolean isProjectImplementsGitflow(String projectId) {
		Boolean implementsGitflow = false;
		String defaultBranch = getDefaultBranch(projectId);
		implementsGitflow = BRANCH_DEVELOP.equalsIgnoreCase(defaultBranch);
		return implementsGitflow;
	}
	
	@Cacheable(cacheNames = "project-details")
	public GitlabProjectExtended getProjectDetails(String projectId) {
		GitlabProjectExtended project = null;
		try {
			project = gitlabClient.getSingleProject(projectId);
		}catch (Exception e) {
			String msgError = "Falhou ao tentar buscar o projeto: " + projectId + "  - erro: " + e.getLocalizedMessage();
			logger.error(msgError);
			telegramService.sendBotMessage(msgError);
		}
		return project;
	}

	@Cacheable(cacheNames = "search-projects")
	public List<GitlabProjectExtended> searchProjectByNamespace(String projectNamespace) {
		List<GitlabProjectExtended> projects = null;
		Map<String, String> searchData = new HashMap<>();
		try {
			searchData.put("search_namespaces", Boolean.TRUE.toString());
			searchData.put("search", projectNamespace);
			projects = gitlabClient.searchProject(searchData);
		}catch (Exception e) {
			String msgError = "Falhou ao tentar buscar o projeto: " + projectNamespace + "  - erro: " + e.getLocalizedMessage();
			logger.error(msgError);
			telegramService.sendBotMessage(msgError);
		}
		return projects;
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
	
	public String checkMRsOpened(String mergesWebUrls) {
		List<String> mrAbertosConfirmados = new ArrayList<>();
		
		List<GitlabMergeRequestVO> MRsVO = GitlabUtils.getMergeRequestVOListFromString(mergesWebUrls, gitlabUrl);
		// pesquisar MR com a identificacao do projeto + número do MR
		if(MRsVO != null && !MRsVO.isEmpty()) {
			Map<String, GitlabProjectExtended> projectsCache = new HashMap<>();
			for (GitlabMergeRequestVO MR : MRsVO) {
				String projectNamespace = MR.getProjectNamespace();
				if(StringUtils.isNotBlank(projectNamespace)) {
					if(projectsCache.get(projectNamespace) == null) {
						List<GitlabProjectExtended> projects = searchProjectByNamespace(projectNamespace);
						if(projects != null && !projects.isEmpty()) {
							for (GitlabProjectExtended project : projects) {
								String pathWithNamespace = project.getPathWithNamespace();
								projectsCache.put(pathWithNamespace, project);
							}
						}
					}
					if(projectsCache.get(projectNamespace) != null) {
						GitlabProjectExtended project = projectsCache.get(projectNamespace);
						BigDecimal mrIID = MR.getMrIId();
						// pesquisa pelo MR
						if(project != null && project.getId() != null && mrIID != null) {
							GitlabMRResponse response = getMergeRequest(project.getId().toString(), mrIID);
							if(response != null && GitlabMergeRequestStateEnum.OPENED.equals(response.getState())) {
								if(StringUtils.isNotBlank(response.getWebUrl()) && !mrAbertosConfirmados.contains(response.getWebUrl())) {
									mrAbertosConfirmados.add(response.getWebUrl());
								}
							}
						}
					}
				}
			}	
		}
		return String.join(", ", mrAbertosConfirmados);
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
		String defaultBranch = getDefaultBranch(projectId);
		Boolean squashCommits = true;
		Boolean removeSourceBranch = true;

		return mergeSourceBranchIntoBranchTarget(projectId, featureBranch, defaultBranch, mergeMessage, null, squashCommits, removeSourceBranch);
	}

	public GitlabMRResponse openMergeRequestIntoBranchDefault(String projectId, String featureBranch, String mergeMessage) throws Exception {
		String defaultBranch = getDefaultBranch(projectId);
		Boolean squashCommits = true;
		Boolean removeSourceBranch = true;

		return openMergeRequest(projectId, featureBranch, defaultBranch, mergeMessage, null, squashCommits, removeSourceBranch);
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
		GitlabMRResponse mrAccepted = null;
		GitlabMRResponse mrOpened = openMergeRequest(projectId, sourceBranch, targetBranch, mergeMessage, labels, squashCommits, removeSourceBranch);
		if(mrOpened != null) {
			mrAccepted = acceptMergeRequest(projectId, mrOpened.getIid(), mergeMessage, squashCommits, removeSourceBranch);
		}
		return mrAccepted;
	}
	
	public GitlabMRResponse openMergeRequest(String projectId, String sourceBranch, String targetBranch, 
			String mergeMessage, String labels, Boolean squashCommits, Boolean removeSourceBranch) throws Exception {
		
		GitlabMRResponse mrOpened = null;
		
		if(squashCommits == null) {
			squashCommits = true;
		}
		if(removeSourceBranch == null) {
			removeSourceBranch = true;
		}
		// verifica se o banch da release já foi mergeado no branch target
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
					}
				}
			}
		}else {
			logger.info("No projeto: " + projectId + " - o branch "+ sourceBranch + " - já foi integrado ao branch: " + targetBranch);
		}
		return mrOpened;
	}
	
	public GitlabMRResponse acceptMergeRequest(String projectId, BigDecimal mrIID) throws Exception {
		return acceptMergeRequest(projectId, mrIID, null, true, true);
	}
	
	public GitlabMRResponse acceptMergeRequest(String projectId, BigDecimal mrIID, String mergeMessage, Boolean squashCommits, Boolean removeSourceBranch) throws Exception {
		GitlabMRResponse mrAccepted = null;
		GitlabMRResponse mrOpened = getMergeRequest(projectId, mrIID);
		if(mrOpened != null && mrOpened.getHasConflicts()) {
			String msgError = "No projeto: " + projectId + " há um conflito no MR: " + mrOpened.getIid().toString() + "";
			logger.error(msgError);
			telegramService.sendBotMessage(msgError);
			throw new Exception(msgError);
		}else if(!mrOpened.getState().equals(GitlabMergeRequestStateEnum.OPENED)){
			String msgError = "No projeto: " + projectId + " o MR: " + mrOpened.getIid().toString() + " não está aberto, está com o status: " + mrOpened.getState().toString();
			logger.error(msgError);
			telegramService.sendBotMessage(msgError);
			throw new Exception(msgError);			
		}else{
			Boolean hasPipelines = projectHasPipelines(projectId, mrOpened.getIid());
			
			GitlabAcceptMRRequest acceptMerge = new GitlabAcceptMRRequest();
			acceptMerge.setMergeRequestIid(mrOpened.getIid());
			acceptMerge.setId(projectId);
			acceptMerge.setMergeWhenPipelineSucceeds(hasPipelines);
			acceptMerge.setSquash(squashCommits);
			acceptMerge.setShouldRemoveSourceBranch(removeSourceBranch);
			if(StringUtils.isNotBlank(mergeMessage)) {
				acceptMerge.setMergeCommitMessage(mergeMessage);
			}
					
			try {
				mrAccepted = sendAcceptMergeRequest(projectId, mrIID, acceptMerge);
				if(mrAccepted != null && !hasPipelines) {
					// aguarda o MR ser finalizado, pois pode ser que tenha que passar por um pipeline ainda
					Integer maxTries = 10;
					Integer timeToWaitInSeconds = 20;
					Integer numTries = 0;
					while (mrAccepted != null && GitlabMergeRequestStateEnum.OPENED.equals(mrAccepted.getState()) && numTries < maxTries) {
						logger.info("waitting "+timeToWaitInSeconds+" seconds before to check if merge was accepted....");
						Utils.waitSeconds(timeToWaitInSeconds);
						mrAccepted = getMergeRequest(projectId, mrOpened.getIid());
					}
				}
			}catch (Exception e) {
				throw new Exception(e.getLocalizedMessage());
			}
		}
		return mrAccepted;
	}
	
	public GitlabMRResponse atualizaLabelsMR(GitlabMergeRequestAttributes mergeRequest, List<String> labels) {
		GitlabMRResponse mergeResponse = null;
		if(labels != null && mergeRequest != null) {
			String projectId = mergeRequest.getTargetProjectId().toString();
			BigDecimal mergeRequestIId = mergeRequest.getIid();
			GitlabMRUpdateRequest updateMerge = new GitlabMRUpdateRequest();
			updateMerge.setMergeRequestIid(mergeRequestIId);
			updateMerge.setId(mergeRequest.getId());
			updateMerge.setLabels(String.join(",", labels));
			
			mergeResponse = updateMergeRequest(projectId, mergeRequestIId, updateMerge);
		}
		
		return mergeResponse;
	}

	public GitlabMRResponse removeLabelsMR(GitlabMergeRequestAttributes mergeRequest, List<String> removerLabels) {
		GitlabMRResponse mergeResponse = null;
		if(removerLabels != null && mergeRequest != null) {
			String projectId = mergeRequest.getTargetProjectId().toString();
			BigDecimal mergeRequestIId = mergeRequest.getIid();
			GitlabMRUpdateRequest updateMerge = new GitlabMRUpdateRequest();
			updateMerge.setMergeRequestIid(mergeRequestIId);
			updateMerge.setId(mergeRequest.getId());
			updateMerge.setRemoveLabels(String.join(",", removerLabels));
			
			mergeResponse = updateMergeRequest(projectId, mergeRequestIId, updateMerge);
		}
		
		return mergeResponse;
	}
	
	public GitlabMRResponse updateMergeRequest(String projectId, BigDecimal mergeRequestIId, GitlabMRUpdateRequest updateMerge) {
		GitlabMRResponse mergeResponse = null;
		try {
			mergeResponse = gitlabClient.updateMergeRequest(projectId, mergeRequestIId, updateMerge);
		} catch (Exception e) {
			String errorMessage = "Falhou ao tentar atualizar o merge: "+ mergeRequestIId 
				+ " - no projeto: " + projectId + " erro: " + e.getLocalizedMessage();
			logger.error(errorMessage);
			telegramService.sendBotMessage(errorMessage);
		}
		
		return mergeResponse;
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
	
	public GitlabMRResponse sendAcceptMergeRequest(String projectId, BigDecimal mergeRequestIId, GitlabAcceptMRRequest acceptMerge) throws Exception {
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

	public GitlabDiscussion sendMergeRequestDiscussionThread(String projectId, BigDecimal mergeRequestIId, String body) throws Exception {
		GitlabDiscussion mergeResponse = null;
		try {
			GitlabMRCommentRequest mergeComment = new GitlabMRCommentRequest(new BigDecimal(projectId), mergeRequestIId, body);
			mergeResponse = gitlabClient.createMergeRequestDiscussion(projectId, mergeRequestIId, mergeComment);
		}catch (Exception e) {
			String errorMessage = "Falhou ao tentar criar uma discussão no MR: !"+ mergeRequestIId 
				+ " - no projeto: " + projectId + " erro: " + e.getLocalizedMessage();
			logger.error(errorMessage);
			telegramService.sendBotMessage(errorMessage);
			
			throw new Exception(errorMessage);
		}
		return mergeResponse;
	}

	public GitlabNote sendMergeRequestComment(String projectId, BigDecimal mergeRequestIId, String body) throws Exception {
		GitlabNote mergeResponse = null;
		try {
			GitlabMRCommentRequest mergeComment = new GitlabMRCommentRequest(new BigDecimal(projectId), mergeRequestIId, body);
			mergeResponse = gitlabClient.createMergeRequestNote(projectId, mergeRequestIId, mergeComment);
		}catch (Exception e) {
			String errorMessage = "Falhou ao tentar criar uma discussão no MR: !"+ mergeRequestIId 
				+ " - no projeto: " + projectId + " erro: " + e.getLocalizedMessage();
			logger.error(errorMessage);
			telegramService.sendBotMessage(errorMessage);
			
			throw new Exception(errorMessage);
		}
		return mergeResponse;
	}

	public GitlabCommitResponse atualizaVersaoPom(String projectId, String branchName, String newVersion, 
			String actualVersion, String commitMessage) {
		GitlabCommitResponse response = null;
		
		if(StringUtils.isNotBlank(newVersion) && StringUtils.isNotBlank(actualVersion) && !actualVersion.equalsIgnoreCase(newVersion)) {
			String pomFilePath = POMXML;
			String pomContent = getRawFile(projectId, pomFilePath, branchName);
			if (StringUtils.isNotBlank(pomContent)) {
				String projectVersionTagname = getProjectVersionTagName(projectId);
				String pomContentChanged = GitlabUtils.changePomXMLVersion(actualVersion, newVersion, pomContent, projectVersionTagname);
				if(!pomContent.equals(pomContentChanged)) {
					response = sendTextAsFileToBranch(projectId, branchName, pomFilePath, pomContentChanged, commitMessage);
				}else {
					String msgError = " Não houve alteração entre os arquivos POM.xml do projeto (" + projectId + ").";
					logger.error(msgError);
					telegramService.sendBotMessage(msgError);
				}
			} else {
				String msgError = " Error trying to get pom (" + pomFilePath + ") content.";
				logger.error(msgError);
				telegramService.sendBotMessage(msgError);
			}
		}
		return response;
	}

	public String getActualVersion(GitlabProject project, String branchName, Boolean onlyNumbers) {
		String projectId = project.getId().toString();
		
		return getActualVersion(projectId, branchName, onlyNumbers);
	}
	
	public String getActualVersion(String projectId, String ref, Boolean onlyNumbers) {
		String actualVersion = null;
		String pomContent = getRawFile(projectId, POMXML, ref);
		if (StringUtils.isNotBlank(pomContent)) {
			String projectVersionTagname = getProjectVersionTagName(projectId);
			actualVersion = Utils.getElementFromXML(pomContent, projectVersionTagname);
		}
		
		if(StringUtils.isNotBlank(actualVersion) && onlyNumbers != null && onlyNumbers) {
			actualVersion = Utils.clearVersionNumber(actualVersion);
		}
		
		return actualVersion;
	}
	
	public void atualizaNumeracaoPastaScriptsVersao(String projectId, String branchName, String lastCommitId, String newVersion, String actualVersion, String commitMessage) {
		if(StringUtils.isNotBlank(newVersion) && StringUtils.isNotBlank(actualVersion)) {
			actualVersion = Utils.clearVersionNumber(actualVersion);
			if(!actualVersion.equalsIgnoreCase(newVersion)) {
				String previousPath = GitlabService.SCRIPS_MIGRATION_BASE_PATH + actualVersion;
				String newPath = GitlabService.SCRIPS_MIGRATION_BASE_PATH + newVersion;
				
				renameDir(projectId, branchName, lastCommitId, previousPath, newPath, commitMessage);
			}
		}
	}

	/**
	 * Consulta a parametrização do projeto no gitlab para saber qual é a tagname do pom.xml que corresponde à versão do projeto, caso
	 * não haja configuração, será utilizado project:version
	 * @return
	 */
	public String getProjectVersionTagName(String projectId) {
		String tagName = null;
		GitlabProjectVariable projectVariable = getProjectVariable(projectId, PROJECT_PROPERTY_POM_VERSION_TAGNAME);
		if(projectVariable != null) {
			tagName = projectVariable.getValue();
		}else {
			tagName = POM_TAGNAME_PROJECT_VERSION_DEFAULT;
		}
		
		return tagName;
	}
	
	public String getJiraRelatedProjectKey(String projectId){
		String jiraProjectKey = null;
		GitlabProjectVariable projectVariable = getProjectVariable(projectId, PROJECT_PROPERTY_JIRA_MAIN_RELATED_PROJECT);
		if(projectVariable != null) {
			jiraProjectKey = projectVariable.getValue();
		}else {
			jiraProjectKey = JIRA_MAIN_RELATED_PROJECT_DEFAULT;
		}
		
		return jiraProjectKey;
	}
	
	public GitlabProjectVariable getProjectVariable(String projectId, String variableKey) {
		GitlabProjectVariable projectVariable = null;
		try {
			projectVariable = gitlabClient.getSingleProjectVariable(projectId, variableKey);
		}catch (Exception e) {
			String errorMessage = "Não foi possível recuperar a variavel: " + variableKey + " do projeto: " + projectId + "\n"
					+ e.getMessage();
			logger.error(errorMessage);
			slackService.sendBotMessage(errorMessage);
			rocketchatService.sendBotMessage(errorMessage);
			telegramService.sendBotMessage(errorMessage);
		}
		return projectVariable;
	}
	
	public String changePomVersion(String projectId, String branchRef, String versaoAtual, String nextVersion, VersionTypeEnum versionType, 
			String commitMessage, MessagesLogger messages) throws Exception {
		String lastCommitId = null;
		// verifica se é necessário alterar o POM
		if(StringUtils.isNotBlank(versaoAtual) && StringUtils.isNotBlank(nextVersion)) {
			// é necessário alterar o POM
			String nextVersionPom = nextVersion;
			switch(versionType) {
			case SNAPSHOT:
				nextVersionPom += "-SNAPSHOT";
				break;
			case RELEASECANDIDATE:
				nextVersionPom += "-RELEASE-CANDIDATE";
				break;
			default:
				nextVersionPom = nextVersion;
			}
			if(!versaoAtual.equalsIgnoreCase(nextVersionPom)) {
				GitlabCommitResponse response = atualizaVersaoPom(projectId, branchRef, 
						nextVersionPom, versaoAtual, commitMessage);
				if(response != null) {
					logger.info("Atualizada a versão do POM.XML de: " + versaoAtual + " - para: " + nextVersionPom);
					lastCommitId = response.getId();
				}else {
					String errorMessage = "Falhou ao tentar atualizar o POM.XML de: " + versaoAtual + " - para: " + nextVersionPom;
					logger.error(errorMessage);
					slackService.sendBotMessage(errorMessage);
					rocketchatService.sendBotMessage(errorMessage);
					telegramService.sendBotMessage(errorMessage);
					throw new Exception(errorMessage);
				}
			}else {
				logger.info("O POM.XML já está atualizado");
			}
		}
		return lastCommitId;
	}

	public GitlabUser findUserByEmail(String email) {
		Map<String, String> options = new HashMap<>();
		if(StringUtils.isNotBlank(email)) {
			options.put("search", email);
		}
		List<GitlabUser> users = findUsers(options);
		GitlabUser user = (users != null && !users.isEmpty()) ? users.get(0) : null;
		return user;
	}

	public GitlabUser findUserById(String id) {
		Map<String, String> options = new HashMap<>();
		if(StringUtils.isNotBlank(id) && StringUtils.isNumeric(id)) {
			options.put("id", id);
		}
		List<GitlabUser> users = findUsers(options);
		GitlabUser user = (users != null && !users.isEmpty()) ? users.get(0) : null;
		return user;
	}
	
	public List<GitlabUser> findGitlabUsers(String searchValue) {
		Map<String, String> options = new HashMap<>();
		if(StringUtils.isNotBlank(searchValue)) {
			if(StringUtils.isNumeric(searchValue)) {
				options.put("id", searchValue);
			}else {
				options.put("search", searchValue);
			}
		}
		
		return findUsers(options);
	}

	public List<GitlabUser> findUsers(Map<String, String> options){
		List<GitlabUser> users = null;
		try {
			users = gitlabClient.findUser(options);
		}catch (Exception e) {
		}
		return users;
	}
}