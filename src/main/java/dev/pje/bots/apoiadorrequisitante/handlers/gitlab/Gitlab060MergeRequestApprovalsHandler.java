package dev.pje.bots.apoiadorrequisitante.handlers.gitlab;

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
import org.springframework.stereotype.Component;

import com.devplatform.model.bot.approvals.TipoPermissaoMREnum;
import com.devplatform.model.gitlab.GitlabCommit;
import com.devplatform.model.gitlab.GitlabCommitAuthor;
import com.devplatform.model.gitlab.GitlabEventChangedItem;
import com.devplatform.model.gitlab.GitlabLabel;
import com.devplatform.model.gitlab.GitlabMergeRequestActionsEnum;
import com.devplatform.model.gitlab.GitlabMergeRequestAttributes;
import com.devplatform.model.gitlab.GitlabUser;
import com.devplatform.model.gitlab.event.GitlabEventMergeRequest;
import com.devplatform.model.gitlab.response.GitlabMRResponse;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueFieldOption;
import com.devplatform.model.jira.JiraUser;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.GitlabUtils;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.GitlabMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.RocketchatMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.AprovacoesMRTextModel;

@Component
public class Gitlab060MergeRequestApprovalsHandler extends Handler<GitlabEventMergeRequest>{

	private static final Logger logger = LoggerFactory.getLogger(Gitlab060MergeRequestApprovalsHandler.class);

	@Value("${clients.gitlab.url}")
	private String gitlabUrl;

	@Value("${clients.jira.url}")
	private String jiraUrl;

	@Value("${clients.jira.user}")
	private String jiraBotUser;

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Autowired
	private AprovacoesMRTextModel aprovacoesMRTextModel;

	@Override
	public String getMessagePrefix() {
		return "|GITLAB||060||MERGE-REQUEST-APPROVALS|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	/***
	 * Monitora as labels de aprovação de MRs do GIT
	 * **NÃO monitora o fechamento dos MRs e não o aceite dos MRs, estes são monitorados pelo handler: gitlab030MergeRequestUpdateHandler
	 * 
	 * ok - há apenas um label alterado?
	 * ok -- se mais de um, o usuário deve ser do grupo de serviços ou lider do projeto
	 * ok - o usuário é do grupo de aprovações do label?
	 * ok - o usuário não é o responsável pela abertura do MR? Nem do último commit, nem é responsável pela codificação da issue?
	 * ok- para cada aprovação ou reprovação deve ser enviada mensagem:
	 * ok-- ao tribunal requisitante indicando a ação
	 * ok-- ao grupo de revisão técnica
	 * ok-- no próprio MR
	 * ok-- para as aprovações
	 * ok - deve-se indicar na issue qual usuário fez a aprovação 
	 * ok - verifica todas as labels relacionadas à aprovação já marcadas no MR e verifica se já existem na issue, se não existir na issue, retira do MR,
	 * informa que houve um problema de identificação do autor e solicita que a label seja colocada novamente
	 * ok - verifica todas as aprovações da issue e verifica se há a marcação do tribunal no MR, se não houver, retira a aprovação da issue
	 * ok - deve-se indicar na issue qual tribunal fez a aprovação
	 * ok - a quantidade de aprovações necessárias foi atingida?
	 * ok -- se sim, aprova o MR (*nada mais é feito, pois há outro handler para isso*)
	 * ok -- se não alcançou o quantitativo previsto:
	 * ok --- manda mensagem ao grupo revisor técnico para verificarem
	 * ok --- verifica se todos os tribunais requisitantes já aprovaram a issue:
	 * ok ---- se ainda não, manda mensagem para aqueles pendentes
	 */
	@Override
	public void handle(GitlabEventMergeRequest gitlabEventMR) throws Exception {
		messages.clean();
		if (gitlabEventMR != null && gitlabEventMR.getObjectAttributes() != null && gitlabEventMR.getObjectAttributes().getAction() != null
				&& GitlabMergeRequestActionsEnum.UPDATE.equals(gitlabEventMR.getObjectAttributes().getAction())
				&& gitlabEventMR.getChanges() != null && gitlabEventMR.getChanges().getLabels() != null 
			) {

			GitlabEventChangedItem<List<GitlabLabel>> alteracaoNasLabels = gitlabEventMR.getChanges().getLabels();
			List<GitlabLabel> labelsAdicionadas = alteracaoNasLabels.getAddedItems();
			List<GitlabLabel> labelsRemovidas = alteracaoNasLabels.getRemovedItems();
			
			List<String> labelsAprovacoesAdicionadas = recuperaLabelsAprovacao(GitlabUtils.translateGitlabLabelListToValueList(labelsAdicionadas));
			List<String> labelsAprovacoesRemovidas = recuperaLabelsAprovacao(GitlabUtils.translateGitlabLabelListToValueList(labelsRemovidas));
			if((labelsAprovacoesAdicionadas != null && !labelsAprovacoesAdicionadas.isEmpty()) ||
					(labelsAprovacoesRemovidas != null && !labelsAprovacoesRemovidas.isEmpty())) {
				String gitlabProjectId = gitlabEventMR.getProject().getId().toString();
				BigDecimal mrIID = gitlabEventMR.getObjectAttributes().getIid();
				
				GitlabMRResponse mergeRequest = gitlabService.getMergeRequest(gitlabProjectId, mrIID);
				JiraIssue issue = getIssue(gitlabEventMR);
				GitlabUser revisorGitlab = gitlabEventMR.getUser();
				JiraUser revisorJira = jiraService.getJiraUserFromGitlabUser(revisorGitlab);
				String tribunalUsuarioRevisor = jiraService.getTribunalUsuario(revisorJira, false);
				
				// verifica se o usuário tem permissao de alterar a label
				// retorno: tem permissao / não tem - mesmo autor do commit / não tem - resposponsavel pela codificacao da issue / não tem - alterou label errada
				TipoPermissaoMREnum permissaoUsuario = verificaPermissaoUsuario(revisorJira, revisorGitlab, tribunalUsuarioRevisor, 
						issue, mergeRequest, labelsAprovacoesAdicionadas, labelsAprovacoesRemovidas);
				
				if(permissaoUsuario.equals(TipoPermissaoMREnum.COM_PERMISSAO)) {
					messages.info("O usuário " + revisorJira.getName() + " possui permissão para a alteração de labels:\n" + alteracaoNasLabels);
					// identifica os dados da issue e do merge
					boolean adicaoDeLabel = (labelsAprovacoesAdicionadas != null && !labelsAprovacoesAdicionadas.isEmpty());
					List<JiraUser> responsaveisRevisao = issue.getFields().getResponsaveisRevisao();
					List<JiraIssueFieldOption> tribunaisResponsaveisRevisao = issue.getFields().getTribunaisResponsaveisRevisao();
					List<String> tribunaisRevisoresIssue = JiraUtils.translateFieldOptionsToValueList(tribunaisResponsaveisRevisao);
					
					/**
					 * se nao tiver sido aprovado por um usuário de serviço, atualiza:
					 * - a lista de pessoas revisoras da issue
					 * - a lista de tribunais revisores da issue
					 */
					if(!jiraService.isServico(revisorJira)) {
						// indica na issue quem é o responsável pela aprovação - na verdade adiciona mais um nome aos já existentes
						responsaveisRevisao = atualizaListaUsuariosRevisores(adicaoDeLabel, responsaveisRevisao, revisorJira);
						// indica na issue qual tribunal é o responsável pela aprovação - de acordo com o responsável pela aprovação
						tribunaisRevisoresIssue = atualizaListaTribunaisRevisores(adicaoDeLabel, tribunaisRevisoresIssue, tribunalUsuarioRevisor);
					}
					
					List<String> labelsList = new ArrayList<>();
					if(mergeRequest != null) {
						labelsList = mergeRequest.getLabels();
					}
					/**
					 * - se houver mais tribunais nas labels do que na issue
					 * -- retira a label do MR, pois não tem como saber quem fez a ação, indica que houve erro na operação
					 */
					List<String> labelsAprovacoesTribunais = recuperaLabelsAprovacao(labelsList);
					try {
						removeLabelsSemTribunalRevisorNaIssue(mergeRequest, issue, labelsAprovacoesTribunais, tribunaisRevisoresIssue, labelsList);
					}catch (Exception e) {
						String errorMsg = "Houve um problema ao tentar remover as labels sem tribunal revisor do MR - erro: " + e.getLocalizedMessage();
						messages.error(errorMsg);
					}
					
					/**
					 * - se houver mais tribunais na issue do que nas labels
					 * -- retira o tribunal da issue
					 */
					if(tribunaisRevisoresIssue != null) {
						List<String> tribunaisAprovadoresSemLabel = identificaTribunalRevisorSemLabelAprovacao(labelsAprovacoesTribunais, tribunaisRevisoresIssue);
						// retirar da lista de tribunais revisores os tribunais sem label
						tribunaisRevisoresIssue.removeAll(tribunaisAprovadoresSemLabel);
						// retirar da lista de usuários revisores os usuários dos tribunais sem label 
						responsaveisRevisao = removeUsuariosDosTribunais(responsaveisRevisao, tribunaisAprovadoresSemLabel);
					}
					
					// faz a recontagem de quantos tribunais aprovaram e preenche o campo: "Aprovações realizadas"
					Integer aprovacoesRealizadas = (tribunaisRevisoresIssue != null ? tribunaisRevisoresIssue.size() : 0);
					// manda mensagem no jira / canais dos tribunais requisitantes / canal do grupo revisor
					// se já completou todas as aprovações, aprova o MR
					
					Integer aprovacoesNecessarias = issue.getFields().getAprovacoesNecessarias();
					List<JiraIssueFieldOption> tribunaisRequisitantes = issue.getFields().getTribunalRequisitante();
					
					List<String> listaTribunaisRequisitantesIssue = JiraUtils.translateFieldOptionsToValueList(tribunaisRequisitantes);
					List<String> listaTribunaisRequisitantesPendentesAprovacao = Utils.getValuesOnlyInA(listaTribunaisRequisitantesIssue, tribunaisRevisoresIssue);
					
					boolean houveAlteracoesNasAprovacoes = verificaSeHouveAlteracaoRevisores(responsaveisRevisao, tribunaisRevisoresIssue, issue);
					Map<String, Object> updateFields = new HashMap<>();
					
					// aprovacoes realizadas
					jiraService.atualizarAprovacoesRealizadas(issue, aprovacoesRealizadas, updateFields);
					// usuarios responsaveis aprovacoes
					jiraService.atualizarResponsaveisRevisao(issue, responsaveisRevisao, updateFields);
					// atualiza tribunais responsaveis aprovacoes
					jiraService.atualizarTribunaisRevisores(issue, tribunaisRevisoresIssue, updateFields);
					
					// adiciona os MR abertos
					String MRsAbertos = issue.getFields().getMrAbertos();
					String urlMR = gitlabEventMR.getObjectAttributes().getUrl();
					MRsAbertos = Utils.concatenaItensUnicosStrings(MRsAbertos, urlMR);
					
					String MrsAbertosConfirmados = gitlabService.checkMRsOpened(MRsAbertos);
					jiraService.atualizarMRsAbertos(issue, MrsAbertosConfirmados, updateFields, true);
					
					aprovacoesMRTextModel.setAprovacoesNecessarias(aprovacoesNecessarias);
					aprovacoesMRTextModel.setAprovacoesRealizadas(aprovacoesRealizadas);
					aprovacoesMRTextModel.setAprovou(adicaoDeLabel);
					aprovacoesMRTextModel.setIssue(issue);
					aprovacoesMRTextModel.setMergeRequest(gitlabEventMR.getObjectAttributes());
					aprovacoesMRTextModel.setUltimoRevisor(revisorJira);
					aprovacoesMRTextModel.setUsuariosResponsaveisAprovacoes(responsaveisRevisao);
					aprovacoesMRTextModel.setTribunaisRequisitantesPendentes(listaTribunaisRequisitantesPendentesAprovacao);

					// se o usuário não é de serviço - publica a altearção como um comentário na issue
					if(!jiraService.isServico(revisorJira)) {
						if(houveAlteracoesNasAprovacoes) {
							// adiciona o comentário
							JiraMarkdown jiraMarkdown = new JiraMarkdown();
							String aprovacoesMRTextJira = aprovacoesMRTextModel.convert(jiraMarkdown);
							jiraService.adicionarComentario(issue, aprovacoesMRTextJira, updateFields);
						}
					}
					
					if(updateFields != null && !updateFields.isEmpty()) {
						enviarAlteracaoJira(issue, updateFields, null, JiraService.TRANSITION_PROPERTY_KEY_EDICAO_AVANCADA, false, false);
					}
					
					if(!jiraService.isServico(revisorJira)) {
						if(houveAlteracoesNasAprovacoes) {
							RocketchatMarkdown rocketchatMarkdown = new RocketchatMarkdown();
							String aprovacoesMRTextRocketchat = aprovacoesMRTextModel.convert(rocketchatMarkdown);
							
							// envia mensagens para os tribunais requisitantes da demanda
							rocketchatService.sendMessageCanaisEspecificos(aprovacoesMRTextRocketchat, listaTribunaisRequisitantesIssue);
							// envia mensagens para o grupo revisor
							rocketchatService.sendMessageGrupoRevisorTecnico(aprovacoesMRTextRocketchat);
							
							// envia para o MR relacionado
							GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
							String aprovacoesMRTextGitlab = aprovacoesMRTextModel.convert(gitlabMarkdown);
							gitlabService.sendMergeRequestComment(gitlabProjectId, mrIID, aprovacoesMRTextGitlab);
						}
					}
					if(aprovacoesRealizadas >= aprovacoesNecessarias && gitlabEventMR.getObjectAttributes() != null) {
						// aprovar o MR
						GitlabMRResponse response = gitlabService.acceptMergeRequest(gitlabProjectId, mrIID);
						if(response == null) {
							messages.error("Houve um erro ao tentar aceitar o MR: "+ mrIID + " - do projeto: " + gitlabProjectId);
						}
					}
					
				}else {
					messages.error("O usuário " + revisorJira.getName() + " não possui permissão para alterar uma ou mais labels do MR, revertendo alterações.");
					
					// reverter a alteração indicando o motivo:
					/**
					 * - usuário não é um revisor habilitado
					 * - usuário é o autor do último commit do branch
					 * - usuário é o responsável pela codificação da issue
					 */
					List<GitlabLabel> labelsAnteriores = alteracaoNasLabels.getPrevious();
					List<String> labels = new ArrayList<>();
					for (GitlabLabel label : labelsAnteriores) {
						labels.add(label.getTitle());
					}
					
					GitlabMRResponse response = gitlabService.atualizaLabelsMR(gitlabEventMR.getObjectAttributes(), labels);
					if(response == null) {
						messages.error("Houve um erro ao tentar reverter a altearção nas labels do MR :: MRIID=" + mrIID 
								+ " - labels:\n" + labels);
					}else {
						StringBuilder mensagemRevertLabels = new StringBuilder();
						GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
						String issueLink = JiraUtils.getPathIssue(issue.getKey(), jiraUrl);
						
						mensagemRevertLabels
						.append(gitlabMarkdown.normal("As labels: "))
						.append(gitlabMarkdown.normal(String.join(", ", labels)))
						.append(gitlabMarkdown.normal(" foram reestabelecidas, pois o usuário"));
						String motivoReversao = "não tem permissão para alterar uma ou todas as labels alteradas";
						if(permissaoUsuario.equals(TipoPermissaoMREnum.SEM_PERMISSAO_AUTOR_COMMIT)) {
							motivoReversao = "é o autor do último commit";
						}else if(permissaoUsuario.equals(TipoPermissaoMREnum.SEM_PERMISSAO_RESPONSAVEL_CODIFICACAO)) {
							motivoReversao = "é o responsável pela codificação na issue " + gitlabMarkdown.link(issueLink, issue.getKey());
						}
						mensagemRevertLabels
						.append(" ")
						.append(gitlabMarkdown.bold(motivoReversao.trim()))
						.append(".");
						gitlabService.sendMergeRequestComment(gitlabProjectId, mrIID, mensagemRevertLabels.toString());
					}
				}
			}else {
				messages.info("A alteração nas labels não afetou as labels de aprovações dos tribunais");
			}
			
		}
	}
	
	private boolean verificaSeHouveAlteracaoRevisores(List<JiraUser> responsaveisRevisaoAtualizado, List<String> tribunaisRevisoresAtualizado, JiraIssue issue) {
		List<JiraUser> responsaveisRevisaoAnteriores = issue.getFields().getResponsaveisRevisao();
		List<JiraIssueFieldOption> tribunaisResponsaveisRevisaoAnteriores = issue.getFields().getTribunaisResponsaveisRevisao();
		List<String> tribunaisRevisoresAnteriores = JiraUtils.translateFieldOptionsToValueList(tribunaisResponsaveisRevisaoAnteriores);
		
		boolean houveAlteracao = false;
		if(responsaveisRevisaoAnteriores == null) {
			responsaveisRevisaoAnteriores = new ArrayList<>();
		}
		if(responsaveisRevisaoAtualizado == null) {
			responsaveisRevisaoAtualizado = new ArrayList<>();
		}
		if(responsaveisRevisaoAnteriores.size() != responsaveisRevisaoAtualizado.size()) {
			houveAlteracao = true;
		}else {
			if(tribunaisRevisoresAnteriores == null) {
				tribunaisRevisoresAnteriores = new ArrayList<>();
			}
			if(tribunaisRevisoresAtualizado == null) {
				tribunaisRevisoresAtualizado = new ArrayList<>();
			}
			if(tribunaisRevisoresAnteriores.size() != tribunaisRevisoresAtualizado.size()) {
				houveAlteracao = true;
			}else {
				if(!responsaveisRevisaoAtualizado.containsAll(responsaveisRevisaoAnteriores)) {
					houveAlteracao = true;
				}else {
					if(tribunaisRevisoresAtualizado.containsAll(tribunaisRevisoresAnteriores)) {
						houveAlteracao = true;
					}
				}
			}
		}
		
		return houveAlteracao;
	}
	
	/**
	 * 1. verifica qual a permissão do usuário:
	 * > admin de labels
	 * -- pode fazer qualquer alteração
	 * > revisor label X
	 * -- só permite que ele indique ou retire a sua label própria label (apenas uma), qualquer outra alteração deve ser revertida
	 * > sem permissões sobre labels
	 * -- qualquer alteração deve ser revertida
	 */
	private TipoPermissaoMREnum verificaPermissaoUsuario(
				JiraUser revisorJira, GitlabUser revisorGitlab, String tribunalRevisor,
				JiraIssue issue, GitlabMergeRequestAttributes mergeRequest,
				List<String> labelsAdicionadas, List<String> labelsRemovidas) {
		TipoPermissaoMREnum permissaoUsuario = null;
		
		if(revisorJira != null && revisorGitlab != null) {
			if((jiraService.isServico(revisorJira) || jiraService.isLiderProjeto(revisorJira))) {
				permissaoUsuario = TipoPermissaoMREnum.COM_PERMISSAO;
			}else if(jiraService.isRevisorCodigo(revisorJira)) {
				String labelAprovacaoUsuario = jiraService.getLabelAprovacaoCodigoDeUsuario(revisorJira);
				Boolean usuarioAlterouSuaLabel = false;
				if(StringUtils.isNotBlank(labelAprovacaoUsuario) && StringUtils.isNotBlank(tribunalRevisor) 
						&& labelAprovacaoUsuario.endsWith(tribunalRevisor)) {
					
					if(labelsAdicionadas != null && labelsAdicionadas.size() == 1
							&& (labelsRemovidas == null || labelsRemovidas.isEmpty())
							&& Utils.compareAsciiIgnoreCase(labelsAdicionadas.get(0), labelAprovacaoUsuario)) {
						usuarioAlterouSuaLabel = true;
					}else if(labelsRemovidas != null && labelsRemovidas.size() == 1
							&& (labelsAdicionadas == null || labelsAdicionadas.isEmpty())
							&& Utils.compareAsciiIgnoreCase(labelsRemovidas.get(0), labelAprovacaoUsuario)) {
						usuarioAlterouSuaLabel = true;
					}
				}else if(StringUtils.isNotBlank(labelAprovacaoUsuario) && StringUtils.isNotBlank(tribunalRevisor)) {
					messages.error("O usuário revisor: " + revisorJira.getName() + " está configurado para indicar aprovação: " + labelAprovacaoUsuario 
							+ ", mas pertence ao tribunal: " + tribunalRevisor);
				}
				if(usuarioAlterouSuaLabel) {
					// verifica se o usuário não é o autor do MR, nem é o autor do último commit, nem é o responsável pela codificação da issue
					GitlabUser autorUltimoCommit = getLastCommitAuthor(mergeRequest);
					if(autorUltimoCommit != null && autorUltimoCommit.getId().equals(revisorGitlab.getId())) {
						permissaoUsuario = TipoPermissaoMREnum.SEM_PERMISSAO_AUTOR_COMMIT;
					}else {
						JiraUser responsavelCodificacao = issue.getFields().getResponsavelCodificacao();
						if(responsavelCodificacao.getKey().equalsIgnoreCase(revisorJira.getKey())) {
							permissaoUsuario = TipoPermissaoMREnum.SEM_PERMISSAO_RESPONSAVEL_CODIFICACAO;
						}
					}
					if(permissaoUsuario == null) {
						permissaoUsuario = TipoPermissaoMREnum.COM_PERMISSAO;
					}
				}else {
					permissaoUsuario = TipoPermissaoMREnum.SEM_PERMISSAO_ALTEROU_LABEL_ERRADA;
				}
			}
		}
		return permissaoUsuario;
	}
	
	private JiraIssue getIssue(GitlabEventMergeRequest gitlabEventMR) {
		JiraIssue issue = null;
		if(gitlabEventMR != null && gitlabEventMR.getObjectAttributes() != null) {
			String mergeTitle = gitlabEventMR.getObjectAttributes().getTitle();
			GitlabCommit lastCommit = gitlabEventMR.getObjectAttributes().getLastCommit();
			String lastCommitTitle = null;
			if(lastCommit != null) {
				lastCommitTitle = lastCommit.getTitle();
			}
			
			String issueKey = Utils.getIssueKeyFromCommitMessage(mergeTitle);
			if(StringUtils.isBlank(issueKey)) {
				issueKey = Utils.getIssueKeyFromCommitMessage(lastCommitTitle);
			}
			
			if(StringUtils.isNotBlank(issueKey)) {
				issue = jiraService.recuperaIssueDetalhada(issueKey);
			}
		}
		
		return issue;
	}
	
	private GitlabUser getLastCommitAuthor(GitlabMergeRequestAttributes mergeRequest) {
		GitlabUser user = null;
		if(mergeRequest != null) {
			GitlabCommit lastCommit = mergeRequest.getLastCommit();
			if(lastCommit != null) {
				GitlabCommitAuthor commitAuthor = lastCommit.getAuthor();
				if(commitAuthor != null && StringUtils.isNotBlank(commitAuthor.getEmail())) {
					user = gitlabService.findUserByEmail(commitAuthor.getEmail());
				}
			}
		}
		
		return user;
	}

	private List<JiraUser> atualizaListaUsuariosRevisores(boolean adicionaRevisor, List<JiraUser> usuariosRevisores, JiraUser revisorAtual){
		List<JiraUser> novaListaResponsaveisRevisao = new ArrayList<>();
		
		if(usuariosRevisores == null) {
			usuariosRevisores = new ArrayList<>();
		}
		
		boolean revisorJaContabilizado = false;
		for (JiraUser revisor : usuariosRevisores) {
			if(revisor.getKey().equalsIgnoreCase(revisorAtual.getKey())) {
				if(adicionaRevisor) {
					novaListaResponsaveisRevisao.add(revisor);
					revisorJaContabilizado = true;
				}else {
					// não faz nada, pois estamos removendo da lista
				}
			}else {
				novaListaResponsaveisRevisao.add(revisor);
			}
		}
		if(adicionaRevisor && !revisorJaContabilizado) {
			novaListaResponsaveisRevisao.add(revisorAtual);
		}
		return novaListaResponsaveisRevisao;
	}
	
	private List<String> atualizaListaTribunaisRevisores(boolean adicionaRevisor, List<String> tribunaisRevisores, String tribunalRevisorAtual){
		if(StringUtils.isNotBlank(tribunalRevisorAtual)) {
			if(tribunaisRevisores == null) {
				tribunaisRevisores = new ArrayList<>();
			}
			if(adicionaRevisor) {
				if(!tribunaisRevisores.contains(tribunalRevisorAtual)) {
					tribunaisRevisores.add(tribunalRevisorAtual);
				}
			}else {
				if(tribunaisRevisores.contains(tribunalRevisorAtual)) {
					tribunaisRevisores.remove(tribunalRevisorAtual);
				}
			}
		}
		return tribunaisRevisores;
	}
	
	private List<String> recuperaLabelsAprovacao(List<String> labelsMR){
		List<String> labelsAprovacao = new ArrayList<>();
		if(labelsMR != null) {
			for (String labelMR : labelsMR) {
				if(StringUtils.isNotBlank(labelMR) && (labelMR.startsWith(GitlabService.PREFIXO_LABEL_APROVACAO_TRIBUNAL)) ) {
					String siglaTribunal = labelMR.replaceAll(GitlabService.PREFIXO_LABEL_APROVACAO_TRIBUNAL, "").replaceAll("_", "").trim();
					labelsAprovacao.add(siglaTribunal);
				}
			}
		}
		return labelsAprovacao;
	}
	
	private List<String> identificaLabelsAprovacaoPeloSufixo(List<String> sufixos, List<String> labelsOriginais){
		List<String> labelsAprovacao = new ArrayList<>();
		if(labelsOriginais != null && sufixos != null) {
			for (String sufixo : sufixos) {
				if(StringUtils.isNotBlank(sufixo)) {
					for (String labelOriginal : labelsOriginais) {
						if(StringUtils.isNotBlank(labelOriginal) && labelOriginal.startsWith(GitlabService.PREFIXO_LABEL_APROVACAO_TRIBUNAL) && labelOriginal.endsWith(sufixo)) {
							labelsAprovacao.add(labelOriginal);
							break;
						}
					}
				}
			}
		}
		return labelsAprovacao;
	}
	
	private List<String> identificaLabelsSemTribunalRevisorNaIssue(List<String> labelsAprovacao, List<String> tribunaisRevisores){
		if(labelsAprovacao == null) {
			labelsAprovacao = new ArrayList<>();
		}
		if(tribunaisRevisores == null) {
			tribunaisRevisores = new ArrayList<>();
		}
		List<String> labelsSemTribunaisAprovadores = new ArrayList<>();
		if(!tribunaisRevisores.containsAll(labelsAprovacao)) {
			for (String labelAprovacao : labelsAprovacao) {
				if(!tribunaisRevisores.contains(labelAprovacao)) {
					labelsSemTribunaisAprovadores.add(labelAprovacao);
				}
			}
		}
		return labelsSemTribunaisAprovadores;
	}
	
	private List<String> identificaTribunalRevisorSemLabelAprovacao(List<String> labelsAprovacao, List<String> tribunaisRevisores){
		if(labelsAprovacao == null) {
			labelsAprovacao = new ArrayList<>();
		}
		if(tribunaisRevisores == null) {
			tribunaisRevisores = new ArrayList<>();
		}
		List<String> tribunaisRevisoresSemLabel = new ArrayList<>();
		if(!labelsAprovacao.containsAll(tribunaisRevisores)) {
			for (String tribunal : tribunaisRevisores) {
				if(!labelsAprovacao.contains(tribunal)) {
					tribunaisRevisoresSemLabel.add(tribunal);
				}
			}
		}
		return tribunaisRevisoresSemLabel;
	}
	
	private List<JiraUser> removeUsuariosDosTribunais(List<JiraUser> usuarios, List<String> tribunais){
		List<JiraUser> novaListaUsuarios = new ArrayList<>();
		if(usuarios != null && tribunais != null && !tribunais.isEmpty()) {
			for (JiraUser usuario : usuarios) {
				String tribunalUsuario = jiraService.getTribunalUsuario(usuario, false);
				if(StringUtils.isBlank(tribunalUsuario) || !tribunais.contains(tribunalUsuario)) {
					novaListaUsuarios.add(usuario);
				}
			}
		}else {
			novaListaUsuarios = usuarios;
		}
		return novaListaUsuarios;
	}
	
	private void removeLabelsSemTribunalRevisorNaIssue(GitlabMergeRequestAttributes mergeRequest, JiraIssue issue,
			List<String> labelsAprovacoesTribunais, List<String> tribunaisRevisores, List<String> labelsOriginais) throws Exception {
		
		List<String> labelsSemTribunaisAprovadores = identificaLabelsSemTribunalRevisorNaIssue(labelsAprovacoesTribunais, tribunaisRevisores);

		// retirar estas labels do MR
		if(mergeRequest != null && labelsSemTribunaisAprovadores != null && !labelsSemTribunaisAprovadores.isEmpty()) {
			BigDecimal mrIID = mergeRequest.getIid();
			String gitlabProjectId = mergeRequest.getTargetProjectId().toString();

			List<String> nomesLabelsRemover = identificaLabelsAprovacaoPeloSufixo(labelsSemTribunaisAprovadores, labelsOriginais);
			GitlabMRResponse response = gitlabService.removeLabelsMR(mergeRequest, nomesLabelsRemover);
			if(response == null) {
				messages.error("Houve um erro ao tentar remover as labels do MR :: MRIID=" + mrIID 
						+ " - labels:\n" + nomesLabelsRemover);
			}else {
				StringBuilder mensagemRemocaoLabels = new StringBuilder();
				GitlabMarkdown gitlabMarkdown = new GitlabMarkdown();
				String issueURL = JiraUtils.getPathIssue(issue.getKey(), jiraUrl);
				
				boolean isPlural = (nomesLabelsRemover.size() > 1);
				if(isPlural) {
					mensagemRemocaoLabels
						.append(gitlabMarkdown.normal("As labels: "));
				}else {
					mensagemRemocaoLabels
						.append(gitlabMarkdown.normal("A label: "));
				}
				mensagemRemocaoLabels
					.append(gitlabMarkdown.normal(String.join(", ", nomesLabelsRemover)));
				if(isPlural) {
					mensagemRemocaoLabels
						.append(gitlabMarkdown.normal(" foram removidas "));
				}else {
					mensagemRemocaoLabels
						.append(gitlabMarkdown.normal(" foi removida "));
				}
				mensagemRemocaoLabels
					.append(gitlabMarkdown.normal(" por não haver correspondência na issue "))
					.append(gitlabMarkdown.link(issueURL, issue.getKey()));
				gitlabService.sendMergeRequestComment(gitlabProjectId, mrIID, mensagemRemocaoLabels.toString());
			}
		}
	}
}