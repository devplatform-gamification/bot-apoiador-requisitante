package dev.pje.bots.apoiadorrequisitante.services;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.devplatform.model.gitlab.GitlabProject;
import com.devplatform.model.gitlab.request.GitlabCommitActionRequest;
import com.devplatform.model.gitlab.request.GitlabCommitActionsEnum;
import com.devplatform.model.gitlab.request.GitlabCommitRequest;
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
			slackService.sendBotMessage(commitMessage);
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

}