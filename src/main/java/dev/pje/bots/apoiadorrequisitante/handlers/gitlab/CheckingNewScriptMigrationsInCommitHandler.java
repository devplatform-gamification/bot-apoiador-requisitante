package dev.pje.bots.apoiadorrequisitante.handlers.gitlab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.GitlabProject;
import com.devplatform.model.gitlab.event.GitlabEventPush;
import com.devplatform.model.gitlab.response.GitlabRepositoryTree;
import com.devplatform.model.gitlab.vo.GitlabScriptVersaoVO;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Component
public class CheckingNewScriptMigrationsInCommitHandler {

	private static final Logger logger = LoggerFactory.getLogger(CheckingNewScriptMigrationsInCommitHandler.class);

	@Autowired
	private GitlabService gitlabService;

	@Autowired
	private TelegramService telegramService;

	public void handle(GitlabEventPush gitlabEventPush) {
		if(gitlabEventPush != null) {
			String[] branchNameSplited = gitlabEventPush.getRef().split("/");
			String branchName = branchNameSplited[branchNameSplited.length - 1];

			if(gitlabService.isMonitoredBranch(gitlabEventPush.getProject(), branchName)
					&& gitlabEventPush.getTotalCommitsCount() > 0) {
				String projectName = gitlabEventPush.getProject().getName();

				String lastCommitId = gitlabEventPush.getCommits().get(0).getId();
				String commitMessage = gitlabEventPush.getCommits().get(0).getMessage();
				String issueKey = Utils.getIssueKeyFromCommitMessage(commitMessage);

				telegramService.sendBotMessage("[COMMIT][GITLAB] - project: " + projectName + " branch: " + branchName + " - " + commitMessage);

				ArrayList<String> addedScripts = getAddedFiles(gitlabEventPush.getCommits());
				ArrayList<GitlabScriptVersaoVO> scriptsToChange = new ArrayList<>();
				if(addedScripts != null && !addedScripts.isEmpty()) {
					// identificar qual é o número da versão atual
					String currentVersion = gitlabService.getActualVersion(gitlabEventPush.getProject(), branchName, true);
					if(StringUtils.isNotBlank(currentVersion)) {
						// verificar se todos os arquivos adicionados estão no caminho correto
						String destinationPath = GitlabService.SCRIPS_MIGRATION_BASE_PATH + currentVersion;
						// buscar todos os scripts que estao na basta de migrations
						List<GitlabRepositoryTree> currentScriptList = gitlabService.getFilesFromPath(gitlabEventPush.getProject(), branchName, destinationPath);
						boolean scriptsOutOfOrder = false;
						for (String addedScript : addedScripts) {
							GitlabScriptVersaoVO addedScriptObj = new GitlabScriptVersaoVO(addedScript);
							if(addedScript.startsWith(GitlabService.SCRIPS_MIGRATION_BASE_PATH)) {
								if(!addedScript.startsWith(destinationPath)) {
									scriptsToChange.add(addedScriptObj);
								}else{
									// check if some added script is in the same order of a current script
									if(addedScriptObj.getSpecificName() != null && !currentScriptList.isEmpty() && currentScriptList.size() > 0) {
										for (GitlabRepositoryTree elmentFromTree : currentScriptList) {
											GitlabScriptVersaoVO currentScriptObj = new GitlabScriptVersaoVO(elmentFromTree.getPath());
											if(!currentScriptObj.getName().equals(addedScriptObj.getName())) {
												if(addedScriptObj.getVersion().equals(currentScriptObj.getVersion()) 
														&& addedScriptObj.getOrder().equals(currentScriptObj.getOrder())) {
													scriptsOutOfOrder = true;
													break;
												}
											}
										}
										if(scriptsOutOfOrder) {
											break;
										}
									}
								}
							}
						}
						if(scriptsOutOfOrder) {
							scriptsToChange = new ArrayList<>();
							for (String addedScript : addedScripts) {
								GitlabScriptVersaoVO addedScriptObj = new GitlabScriptVersaoVO(addedScript);
								scriptsToChange.add(addedScriptObj);
							}
						}
						logger.info(scriptsToChange.toString());
						this.changeScriptsPath(
								gitlabEventPush.getProject(), branchName, issueKey, lastCommitId,
								scriptsToChange, destinationPath, currentScriptList);
					}
				}
			}
		}
	}

	private ArrayList<String> getAddedFiles(List<GitlabCommit> commits) {
		ArrayList<String> listScriptFiles = new ArrayList<>();
		if(!commits.isEmpty()) {
			for (GitlabCommit commit : commits) {
				if(!commit.getAdded().isEmpty()) {
					for (String addedFile : commit.getAdded()) {
						if(addedFile.toLowerCase().endsWith(GitlabService.SCRIPT_EXTENSION.toLowerCase())) {
							if(!listScriptFiles.contains(addedFile)) {
								listScriptFiles.add(addedFile);
							}
						}
					}
				}
			}
		}

		return listScriptFiles;
	}

	private void changeScriptsPath(GitlabProject project, String branchName, String issueKey, String lastCommitId,
			List<GitlabScriptVersaoVO> scriptsToChange, String destinationPath, List<GitlabRepositoryTree> currentScriptList) {
		if(scriptsToChange != null && !scriptsToChange.isEmpty()) {
			// identify target version
			String[] pathArray = destinationPath.split("/");
			String targetVersion = pathArray[pathArray.length - 1];
			// get last current order
			Integer lastOrder = 0;
			for (GitlabRepositoryTree currentScript : currentScriptList) {
				GitlabScriptVersaoVO currentScriptObj = new GitlabScriptVersaoVO(currentScript.getPath());
				if(currentScriptObj.getOrder() > lastOrder) {
					// can not count with scripts to change
					boolean hasToChange = false;
					for (GitlabScriptVersaoVO scriptToChange : scriptsToChange) {
						if(scriptToChange.getName().equals(currentScriptObj.getName())) {
							hasToChange = true;
							break;
						}
					}
					if(!hasToChange) {
						lastOrder = currentScriptObj.getOrder();
					}
				}
			}
			// sort scriptsToChange list
			Collections.sort(scriptsToChange, new SortbyOrder());
			for(int i=0; i < scriptsToChange.size(); i++) {
				GitlabScriptVersaoVO element = scriptsToChange.get(i);
				String numOrderStr = numOrderToString(++lastOrder);
				String newNameWithPath = destinationPath + "/" 
						+ "PJE_" + targetVersion + "_" + numOrderStr
						+ "__";
				if(element.getType() != null) {
					newNameWithPath += element.getType() + "_";
				}
				newNameWithPath += element.getSpecificName();
				element.setNewNameWithPath(newNameWithPath);
				scriptsToChange.set(i, element);
			}
			// encaminha para alteracao no gitlab
			String identificadorCommit = StringUtils.isNotBlank(issueKey) ? issueKey : "[RELEASE]";
			String commitMessage = identificadorCommit + " Reordenando scripts do commit (" + lastCommitId + ")";
			gitlabService.moveFiles(project, branchName, lastCommitId, scriptsToChange, commitMessage);
		}
	}

	public String numOrderToString(Integer order) {
		String orderStr = order.toString();
		if(order < 10) {
			orderStr = "00" + orderStr;
		}else if(order < 100) {
			orderStr = "0" + orderStr;
		}

		return orderStr;
	}

	class SortbyOrder implements Comparator<GitlabScriptVersaoVO>{ 
		// Used for sorting in ascending order of 
		// order 
		public int compare(GitlabScriptVersaoVO a, GitlabScriptVersaoVO b) 
		{ 
			return a.getOrder() - b.getOrder(); 
		} 
	}
}


