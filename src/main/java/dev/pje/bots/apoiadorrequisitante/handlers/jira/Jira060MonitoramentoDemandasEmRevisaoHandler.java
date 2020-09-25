package dev.pje.bots.apoiadorrequisitante.handlers.jira;

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

import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueFieldOption;

import dev.pje.bots.apoiadorrequisitante.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.RocketchatMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.textModels.TribunalRevisaoMRPendenteTextModel;

@Component
public class Jira060MonitoramentoDemandasEmRevisaoHandler extends Handler<String>{
	private static final Logger logger = LoggerFactory.getLogger(Jira060MonitoramentoDemandasEmRevisaoHandler.class);
	
	@Value("${clients.gitlab.url}")
	private String gitlabUrl;
	
	@Value("${clients.jira.user}")
	private String jiraBotUser;
	
	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|JIRA||060||MONITORAMENTO-MRs|";
	}
	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	@Autowired
	private TribunalRevisaoMRPendenteTextModel tribunalRevisaoMRPendenteTextModel;
	
	private Map<String, List<JiraIssue>> demandasPorTribunal = new HashMap<>();
	
	/**
	 * 	:: criar bot que alerte os tribunais requisitantes de que a issue ainda não foi aprovada por ele mesmo ::
	 * - criar rotina periódica que verifique as issues solicitadas por cada tribunal e que estejam em "homologação técnica"
	 * 	>> rodar toda segunda-feira
	 * - verifica se o próprio tribunal já aprovou a issue:
	 * -- se ainda não tiver aprovado a issue, manda mensagem indicando que o tribunal deve aprová-la já que fez a requisição
	 */
	@Override
	public void handle(String migrarDados) throws Exception {
		messages.clean();
		
		String jql = "category in (PJE) AND project not in (EV, 10110, 11012) AND \"Raia do fluxo\" in (\"Grupo revisor técnico\") AND \"Tribunal requisitante\" is not EMPTY ORDER BY cf[13835] DESC, \"Business Value\" DESC";
		List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
		
		if(issues != null && !issues.isEmpty()) {
			for (JiraIssue issue : issues) {
				List<JiraIssueFieldOption> tribunaisRequisitantes = issue.getFields().getTribunalRequisitante();
				List<String> tribunaisRequisitantesStr = JiraUtils.translateFieldOptionsToValueList(tribunaisRequisitantes);

				List<JiraIssueFieldOption> tribunaisRevisores = issue.getFields().getTribunaisResponsaveisRevisao();
				List<String> tribunaisRevisoresStr = JiraUtils.translateFieldOptionsToValueList(tribunaisRevisores);

				List<String> tribunaisRequisitantesPendentesRevisao = Utils.getValuesOnlyInA(tribunaisRequisitantesStr, tribunaisRevisoresStr);
				if(!tribunaisRequisitantesPendentesRevisao.isEmpty()) {
					for (String tribunalRequisitantePendenteRevisao : tribunaisRequisitantesPendentesRevisao) {
						adicionaIssueAoTribunal(tribunalRequisitantePendenteRevisao, issue);
					}
				}
			}
			comunicaTribunaisDemandasPendentes();
		}
	}
	
	private void adicionaIssueAoTribunal(String tribunal, JiraIssue issue) {
		if(StringUtils.isNotBlank(tribunal) && issue != null && StringUtils.isNotBlank(issue.getKey())) {
			List<JiraIssue> issuesTribunal = new ArrayList<>();
			if(this.demandasPorTribunal.get(tribunal) != null) {
				issuesTribunal = this.demandasPorTribunal.get(tribunal);
			}
			boolean issueJahContabilizada = false;
			for (JiraIssue issueTribunal : issuesTribunal) {
				if(issueTribunal.getKey().equals(issue.getKey())) {
					issueJahContabilizada = true;
					break;
				}
			}
			if(!issueJahContabilizada) {
				issuesTribunal.add(issue);
			}
			this.demandasPorTribunal.put(tribunal, issuesTribunal);
		}
	}
	
	private void comunicaTribunaisDemandasPendentes() {
		if(this.demandasPorTribunal != null && !this.demandasPorTribunal.isEmpty()) {
			RocketchatMarkdown rocketchatMarkdown = new RocketchatMarkdown();
			for (String tribunal : this.demandasPorTribunal.keySet()) {
				List<JiraIssue> issuesTribunal = this.demandasPorTribunal.get(tribunal);
				tribunalRevisaoMRPendenteTextModel.setIssues(issuesTribunal);
				tribunalRevisaoMRPendenteTextModel.setSiglaTribunal(tribunal);
				
				String demandasRevisaoPendenteTextRocketchat = tribunalRevisaoMRPendenteTextModel.convert(rocketchatMarkdown);
				tribunal = "tjmt_r";
				rocketchatService.sendSimpleMessage(tribunal, demandasRevisaoPendenteTextRocketchat, false);
			}
		}
	}
}