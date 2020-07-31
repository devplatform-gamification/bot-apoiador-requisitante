package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.GitlabProject;
import com.devplatform.model.gitlab.event.GitlabEventPush;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;

@Component
public class GitlabEventHandlerGitflow {
	
	private static final Logger logger = LoggerFactory.getLogger(GitlabEventHandlerGitflow.class);

	@Autowired
	private GitlabService gitlabService;
	
	@Autowired
	private TelegramService telegramService;
	
	public void handle(GitlabEventPush gitlabEventPush) {
		if(gitlabEventPush != null && gitlabEventPush.getProjectId() != null) {
			if(gitlabService.isProjectImplementsGitflow(gitlabEventPush.getProjectId().toString())) {
				String[] branchNameSplited = gitlabEventPush.getRef().split("/");
				String branchName = branchNameSplited[branchNameSplited.length - 1];
				List<GitlabCommit> commits = gitlabEventPush.getCommits();
		
				if(commits != null && commits.size() > 0) {
					if(gitlabService.isBranchMaster(gitlabEventPush.getProject(), branchName)
					|| gitlabService.isBranchRelease(gitlabEventPush.getProject(), branchName)) {
						GitlabProject project = gitlabEventPush.getProject();
						if(gitlabService.isDevelopDefaultBranch(project)) {
							
						}
						
						boolean isThereAReleaseBranch = true;
						if(gitlabService.isBranchMaster(project, branchName)) {
							String targetBranch = gitlabService.getActualReleaseBranch(project);
							if(targetBranch == null || targetBranch.isEmpty()) {
								isThereAReleaseBranch = false;
							}else {
								gitlabService.cherryPick(project, targetBranch, commits);
							}
						}
						if(!isThereAReleaseBranch || gitlabService.isBranchRelease(project, branchName)) {
							String targetBranch = GitlabService.BRANCH_DEVELOP;
							gitlabService.cherryPick(project, targetBranch, commits);					
						}
					}
				}
			}
		}
	}
}