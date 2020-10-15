package dev.pje.bots.apoiadorrequisitante.handlers.gitlab;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.GitlabMergeRequestStateEnum;
import com.devplatform.model.gitlab.GitlabMergeRequestStatusEnum;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraUser;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.GitlabMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.RocketchatMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.AbstractTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.CodigoComConflitoTextModel;

@Component
public class Gitlab080UpdateMergeRequestsHandler extends Handler<String>{

	private static final Logger logger = LoggerFactory.getLogger(Gitlab080UpdateMergeRequestsHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|GITLAB||080||UPDATE-MERGE-REQUESTS|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	@Autowired
	private CodigoComConflitoTextModel codigoComConflitoTextModel;
	
	private static long diferencaMinimaEntreAtualizacoes = 7;
	
	/***
	 * :: criar job para revalidação dos MRs abertos ::
	 * 
	 * ok- recupera todos os MRs dos projetos identificados
	 * ok- filtra apenas os MRs de projetos monitorados
	 * - se não puder ser mergeado >> fecha o MR
	 * - se puder ser mergeado, faz rebase >> se não puder fazer rebase >> fecha o MR
	 * - depois do rebase, força a execução de um novo pipeline, se ao final o pipeline falhar >> fecha o MR (isso é feito em outro bot)
	 * 
	 */
	@Override
	public void handle(String dataExecucao) throws Exception {
		messages.clean();
		Map<String, String> options = new HashMap<>();
		options.put("state", GitlabMergeRequestStateEnum.OPENED.toString());
		options.put("scope", "all");

		List<GitlabMRResponse> todosMergeRequests = gitlabService.findMergeRequestAllProjects(options);
		Map<BigDecimal, List<GitlabMRResponse>> mergeRequestPorProjeto = new HashMap<>();
		if(todosMergeRequests != null) {
			for (GitlabMRResponse mergeRequest : todosMergeRequests) {
				if(mergeRequest != null && mergeRequest.getProjectId() != null) {
					BigDecimal gitlabProjectId = mergeRequest.getProjectId();
					List<GitlabMRResponse> mergesDoProjeto = mergeRequestPorProjeto.get(gitlabProjectId);
					if(mergesDoProjeto == null) {
						mergesDoProjeto = new ArrayList<>();
					}
					mergesDoProjeto.add(mergeRequest);
					// ordenar a lista de merges em ordem de última atualizacao
					mergeRequestPorProjeto.put(gitlabProjectId, mergesDoProjeto);
				}
			}
		}
		messages.info(mergeRequestPorProjeto.keySet().toString());
		Map<BigDecimal, List<GitlabMRResponse>> mergeRequestPorProjetosMonitorados = new HashMap<>();
		for (BigDecimal projetoId : mergeRequestPorProjeto.keySet()) {
			messages.info("Projeto: " + projetoId + " - num MRs:" + mergeRequestPorProjeto.get(projetoId).size());
			String jiraProjectKey = gitlabService.getJiraRelatedProjectKey(projetoId.toString(), false);
			if(StringUtils.isNotBlank(jiraProjectKey)) {
				mergeRequestPorProjetosMonitorados.put(projetoId, mergeRequestPorProjeto.get(projetoId));
			}
		}

		List<GitlabMRResponse> mergesComProblemas = new ArrayList<>();
		List<GitlabMRResponse> rebasedMergesComProblemas = new ArrayList<>();
		Date dataMinAtualizacao = Utils.calculateDaysFromNow(-diferencaMinimaEntreAtualizacoes);
		for (BigDecimal projetoId : mergeRequestPorProjetosMonitorados.keySet()) {
			if(mergeRequestPorProjeto.get(projetoId) != null) {
				for (GitlabMRResponse mergeRequest : mergeRequestPorProjeto.get(projetoId)) {
					if(mergeRequest.getMergeStatus() != null && mergeRequest.getMergeStatus().equals(GitlabMergeRequestStatusEnum.CAN_NOT_BE_MERGED)) {
						mergesComProblemas.add(mergeRequest);
					}else if(mergeRequest.getHasConflicts()) {
						mergesComProblemas.add(mergeRequest);
					}else {
						// verifica se a data de atualização é de pelo menos 7 dias, se for, solicita o rebase
						// solicita o rebase do merge
						String dataUltimaAtualizacao = mergeRequest.getUpdatedAt();
						if(StringUtils.isNotBlank(dataUltimaAtualizacao)) {
							Date dataAtualizacao = Utils.getDateFromString(dataUltimaAtualizacao);

							if(Utils.checkDifferenceInDaysBetweenTwoDates(dataAtualizacao, dataMinAtualizacao) <= 0) {
								GitlabMRResponse rebasedMR = gitlabService.rebaseMergeRequest(projetoId.toString(), mergeRequest.getIid());
								if(rebasedMR != null && StringUtils.isNotBlank(rebasedMR.getMergeError())) {
									rebasedMergesComProblemas.add(mergeRequest);
								}
							}
						}
					}
				}
			}
		}
		if(mergesComProblemas != null && !mergesComProblemas.isEmpty()) {
			for (GitlabMRResponse mergeComProblema : mergesComProblemas) {
				JiraIssue issue = jiraService.getIssue(mergeComProblema);
				if(issue != null) {
					JiraUser desenvolvedor = null;
					if(issue.getFields().getResponsavelCodificacao() != null) {
						desenvolvedor = issue.getFields().getResponsavelCodificacao();
					}
					String gitlabProjectId = mergeComProblema.getProjectId().toString();
					BigDecimal mrIId = mergeComProblema.getIid();
					
					codigoComConflitoTextModel.setDesenvolvedor(desenvolvedor);
					codigoComConflitoTextModel.setIssue(issue);
					codigoComConflitoTextModel.setMergeRequest(mergeComProblema);
					
					fechaMRsEnviaMensagem(codigoComConflitoTextModel, gitlabProjectId, mrIId, issue, desenvolvedor);
				}else {
					messages.error("Issue não identificada");
				}
			}
		}
		if(rebasedMergesComProblemas != null && !rebasedMergesComProblemas.isEmpty()) {
			messages.info("Erros ao tentar fazer o rebase das issues: ");
			messages.info(rebasedMergesComProblemas.toString());
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