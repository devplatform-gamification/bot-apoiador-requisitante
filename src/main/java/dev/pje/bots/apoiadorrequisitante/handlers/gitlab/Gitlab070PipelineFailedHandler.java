package dev.pje.bots.apoiadorrequisitante.handlers.gitlab;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabMergeRequestAttributes;
import com.devplatform.model.gitlab.GitlabPipeline;
import com.devplatform.model.gitlab.GitlabPipelineStatusEnum;
import com.devplatform.model.gitlab.event.GitlabEventPipeline;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraUser;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.GitlabMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.RocketchatMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.AbstractTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.FalhaPipelineFechamentoMRTextModel;

@Component
public class Gitlab070PipelineFailedHandler extends Handler<GitlabEventPipeline>{

	private static final Logger logger = LoggerFactory.getLogger(Gitlab070PipelineFailedHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|GITLAB||070||PIPELINE-FAILED|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	@Autowired
	private FalhaPipelineFechamentoMRTextModel falhaPipelineFechamentoMRTextModel;

	/***
	 * :: Monitora a execucao do pipeline para os merge requests ::
	 * - se o pipeline falhar:
	 * -- verifica se o último pipeline para o merge está com uma situação de em execução ou sucesso
	 * -- se não houver nada melhor, fecha o merge
	 * 
	 */
	@Override
	public void handle(GitlabEventPipeline gitlabEventPipeline) throws Exception {
		messages.clean();
		if (gitlabEventPipeline != null && gitlabEventPipeline.getObjectAttributes() != null 
				&& gitlabEventPipeline.getObjectAttributes().getStatus() != null
				&& GitlabPipelineStatusEnum.statusFailed(gitlabEventPipeline.getObjectAttributes().getStatus())
				&& StringUtils.isNotBlank(gitlabEventPipeline.getObjectAttributes().getRef()) && gitlabEventPipeline.getProject() != null
		) {
			String gitlabProjectId = gitlabEventPipeline.getProject().getId().toString();
			
			GitlabMRResponse mergeRequest = null;
			if(gitlabEventPipeline.getMergeRequest() != null && gitlabEventPipeline.getMergeRequest().getIid() != null) {
				GitlabMergeRequestAttributes mergeEvent = gitlabEventPipeline.getMergeRequest();
				mergeRequest = gitlabService.getMergeRequest(gitlabProjectId, mergeEvent.getIid());
			}else {
				String sourceBranch = gitlabEventPipeline.getObjectAttributes().getRef();
				List<GitlabMRResponse> mergeRequests = gitlabService.findOpenMergeRequestsFromProjectBranch(gitlabProjectId, sourceBranch);
				if(mergeRequests != null && !mergeRequests.isEmpty()) {
					mergeRequest = mergeRequests.get(0);
				}
			}
			
			if(mergeRequest != null) {
				BigDecimal mrIID = mergeRequest.getIid();
				
				List<GitlabPipeline> pipelines = gitlabService.mergePipelines(gitlabProjectId, mrIID);
				GitlabPipeline lastPipeLine = null;
				boolean lastPipelineSuccess = true;
				String lastFailedPipelineUrl = null;
				if(pipelines != null && !pipelines.isEmpty()) {
					lastPipeLine = pipelines.get(0);
					if(GitlabPipelineStatusEnum.statusFailed(lastPipeLine.getStatus())) {
						lastPipelineSuccess = false;
						lastFailedPipelineUrl = lastPipeLine.getWebUrl();
					}
				}
				if(!lastPipelineSuccess) {
					JiraIssue issue = jiraService.getIssue(mergeRequest);
					JiraUser desenvolvedor = null;
					if(issue != null && issue.getFields().getResponsavelCodificacao() != null) {
						desenvolvedor = issue.getFields().getResponsavelCodificacao();
					}
					falhaPipelineFechamentoMRTextModel.setDesenvolvedor(desenvolvedor);
					falhaPipelineFechamentoMRTextModel.setIssue(issue);
					falhaPipelineFechamentoMRTextModel.setMergeRequest(mergeRequest);
					falhaPipelineFechamentoMRTextModel.setPipelineWebUrl(lastFailedPipelineUrl);
					
					fechaMRsEnviaMensagem(falhaPipelineFechamentoMRTextModel, gitlabProjectId, mrIID, issue, desenvolvedor);
				}
			}
		}
	}
	
	private void fechaMRsEnviaMensagem(AbstractTextModel modeloMensagem, String gitlabProjectId, BigDecimal mrIID, JiraIssue issue, JiraUser desenvolvedor) throws Exception {
		GitlabMRResponse mergeRequestClosed = gitlabService.closeMergeRequest(gitlabProjectId, mrIID);
		if(mergeRequestClosed != null) {
			// envia para o MR relacionado
			GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
			String falhaPipelineFechamentoMRTextGitlab = modeloMensagem.convert(gitlabMarkdown);
			gitlabService.sendMergeRequestComment(gitlabProjectId, mrIID, falhaPipelineFechamentoMRTextGitlab);
			
			// envia para a issue relacionada
			if(issue != null) {
				JiraMarkdown jiraMarkdown = new JiraMarkdown();
				String falhaPipelineFechamentoMRTextJira = modeloMensagem.convert(jiraMarkdown);
				Map<String, Object> updateFields = new HashMap<>();
				jiraService.adicionarComentario(issue, falhaPipelineFechamentoMRTextJira, updateFields);
				enviarAlteracaoJira(issue, updateFields, null, null, false, true);
			}
			
			if(desenvolvedor != null && StringUtils.isNotBlank(desenvolvedor.getEmailAddress())) {
				RocketchatMarkdown rocketchatMarkdown = new RocketchatMarkdown();
				String falhaPipelineFechamentoMRTextRocketchat = modeloMensagem.convert(rocketchatMarkdown);
				rocketchatService.sendMessageToUsername(desenvolvedor.getEmailAddress(), falhaPipelineFechamentoMRTextRocketchat, false);						
			}
		}		
	}
}