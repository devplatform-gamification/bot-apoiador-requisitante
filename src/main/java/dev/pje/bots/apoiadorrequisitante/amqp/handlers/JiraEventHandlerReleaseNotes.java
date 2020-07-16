package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

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

import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.jira.JiraEventChangelogItems;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.JiraVersionReleaseNoteIssues;
import com.devplatform.model.jira.JiraVersionReleaseNotes;
import com.devplatform.model.jira.JiraVersionReleaseNotesIssueTypeEnum;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.event.JiraEventIssue.IssueEventTypeNameEnum;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;
import dev.pje.bots.apoiadorrequisitante.utils.ReleaseNotesConverter;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class JiraEventHandlerReleaseNotes {
	
	private static final Logger logger = LoggerFactory.getLogger(JiraEventHandlerReleaseNotes.class);

	@Autowired
	private JiraService jiraService;
	
	@Autowired
	private GitlabService gitlabService;
	
	@Autowired
	private TelegramService telegramService;
	
	@Autowired
	private ReleaseNotesConverter releaseNotesConverter;
	
	public static final String MESSAGE_PREFIX = "[RELEASE-NOTES][JIRA]";
	
	public void handle(JiraEventIssue jiraEventIssue) {
			if(jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if(issue.getFields() != null && issue.getFields().getIssuetype() != null
					&& issue.getFields().getIssuetype().getId() != null
					&& JiraService.ISSUE_TYPE_NEW_VERSION.toString().equals(issue.getFields().getIssuetype().getId().toString())) {
				// 2. verifica se é nova issue ou se tramitou solicitando nova release notes
				boolean askToNewReleaseNotes = false;
				if(IssueEventTypeNameEnum.ISSUE_CREATED.equals(jiraEventIssue.getIssueEventTypeName())) {
					askToNewReleaseNotes = true;
				}else if(jiraEventIssue.getChangelog() != null) {
					List<JiraEventChangelogItems> changeItems = jiraEventIssue.getChangelog().getItems();
					for (JiraEventChangelogItems changedItem : changeItems) {
						if(changedItem != null && JiraService.FIELD_STATUS.equals(changedItem.getField())) {
							if(JiraService.STATUS_RELEASE_NOTES_ID.toString().equals(changedItem.getTo())) {
								askToNewReleaseNotes = true;
								break;
							}
						}
					}
				}
				if(askToNewReleaseNotes) {
					// 3. estamos no caminho correto, indicar no log
					telegramService.sendBotMessage(MESSAGE_PREFIX + " - " + jiraEventIssue.getIssue().getKey() + " - " + jiraEventIssue.getIssueEventTypeName().name());
					// 4.- a pessoa que abriu a issue está dentro do grupo de pessoas que podem abrir?
					if(jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
						String versaoASerLancada = issue.getFields().getVersaoSeraLancada();
						Boolean versaoLancada = false;
						String dataLancamentoVersao = null;
						JiraUser usuarioCommiter = null;
						if(issue.getFields().getVersaoSeraLancada() == null){
							versaoASerLancada = getVersaoAfetada(issue.getFields().getVersions());
						}
						if(!versaoASerLancada.isEmpty()){
							// verifica se a versão a ser lançada já não foi lançada - identifica isso no gitlab, tentando identificar uma tag relacionada
							String gitlabProjectId = jiraService.getGitlabProjectFromIssue(issue);
							if(StringUtils.isNotBlank(gitlabProjectId)) {
								GitlabTag tag = gitlabService.getVersionTag(gitlabProjectId, versaoASerLancada);
								if(tag != null && tag.getCommit() != null) {
									versaoLancada = true;
									if(tag.getCommit().getCommittedDate() != null) {
										dataLancamentoVersao = tag.getCommit().getCommittedDate();
									}
									if(tag.getCommit().getCommitterName() != null) {
										usuarioCommiter = jiraService.getUserFromUserName(tag.getCommit().getCommitterEmail());
									}
								}
							}
						}
						if(!versaoASerLancada.isEmpty()){
							String proximaVersao = issue.getFields().getProximaVersao();
							if(proximaVersao == null || proximaVersao.isEmpty()){
								// calcula com base no incremento de 1 dígito do terceiro número da "versão a ser lançada"
								proximaVersao = Utils.calculateNextOrdinaryVersion(versaoASerLancada, 2);
							}
							
							String versaoAfetada = getVersaoAfetada(issue.getFields().getVersions());
							// - se tudo certo, busca as issues cujo "fixversion" = "versão afetada" e gera um release notes
							String jql = jiraService.getJqlIssuesFromFixVersion(versaoAfetada, true);
							List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
							if(issues != null && issues.size() > 0){
								JiraVersionReleaseNotes releaseNotes = new JiraVersionReleaseNotes();
								releaseNotes.setJql(jql);
								releaseNotes.setAuthor(jiraEventIssue.getUser());
								if(usuarioCommiter != null) {
									releaseNotes.setAuthor(usuarioCommiter);
								}
								releaseNotes.setVersion(versaoASerLancada);
								releaseNotes.setNextVersion(proximaVersao);
								if(versaoLancada && dataLancamentoVersao != null) {
									releaseNotes.setReleaseDate(dataLancamentoVersao.toString());
								}
								releaseNotes.setProject(issue.getFields().getProject().getName());
								if(issue.getFields().getDestaquesReleaseNotes() != null && !issue.getFields().getDestaquesReleaseNotes().isEmpty()){
									releaseNotes.setVersionHighlights(issue.getFields().getDestaquesReleaseNotes());
								}
								if(issue.getFields().getTipoVersao() != null && issue.getFields().getTipoVersao().getValue() != null){
									releaseNotes.setVersionType(issue.getFields().getTipoVersao().getValue());
								}
								List<JiraVersionReleaseNoteIssues> newFeatures = new ArrayList<>();
								List<JiraVersionReleaseNoteIssues> improvements = new ArrayList<>();
								List<JiraVersionReleaseNoteIssues> bugsFixes = new ArrayList<>();
								List<JiraVersionReleaseNoteIssues> minorChanges = new ArrayList<>();
								for (JiraIssue issueItem : issues) {
									JiraVersionReleaseNoteIssues releaseItem = new JiraVersionReleaseNoteIssues();
									JiraUser author = issueItem.getFields().getResponsavelCodificacao();
									releaseItem.setAuthor(author);
									releaseItem.setIssueKey(issueItem.getKey());
									releaseItem.setSummary(Utils.cleanSummary(issueItem.getFields().getSummary()));
									
									releaseItem.setPriority(issueItem.getFields().getPriority().getId().intValue());
									if(issueItem.getFields().getNotasRelease() != null){
										releaseItem.setReleaseObservation(issueItem.getFields().getNotasRelease());
									}
									JiraIssuetype issueType = issueItem.getFields().getIssuetype();
									JiraVersionReleaseNotesIssueTypeEnum releaseIssueType = getIssueTypeEnum(issueType);
									releaseItem.setIssueType(releaseIssueType.name());
									
									if(JiraVersionReleaseNotesIssueTypeEnum.BUGFIX.equals(releaseIssueType)){
										bugsFixes.add(releaseItem);
									}else if(JiraVersionReleaseNotesIssueTypeEnum.NEW_FEATURE.equals(releaseIssueType)){
										newFeatures.add(releaseItem);
									}else if(JiraVersionReleaseNotesIssueTypeEnum.IMPROVEMENT.equals(releaseIssueType)){
										improvements.add(releaseItem);
									}else{
										minorChanges.add(releaseItem);
									}
								}
								releaseNotes.setBugs(bugsFixes);
								releaseNotes.setNewFeatures(newFeatures);
								releaseNotes.setImprovements(improvements);
								releaseNotes.setMinorChanges(minorChanges);
								
								this.atualizarDescricao(issue, releaseNotes);
							}else{
								try {
									Map<String, Object> updateFields = new HashMap<>();
									jiraService.atualizarDescricao(issue, "Não foram encontradas issues finalizadas para a versão " + versaoAfetada, updateFields);
									enviaAlteracao(issue, updateFields, TRANSITION_ID_RELEASE_NOTES_FAILED);
								} catch (Exception e1) {
									e1.printStackTrace();
								}
							}
							/*
							
								- gerar um release notes bem simples, organizando por tipo de issues: Novas funcionalidades > Melhorias > Correçoes - Descrição da issue - ([numero|link] @nome_desenvolvedor)
								- salvar o release notes na descrição da demanda em formato json
								- tramitar para a saída "Gerar release notes"
							 */

						}else{
							String message = "Não foi possível identificar a versao a ser lançada, verifique a informação.";
							telegramService.sendBotMessage(MESSAGE_PREFIX + " - " + jiraEventIssue.getIssue().getKey() + " - " + message);
							try {
								Map<String, Object> updateFields = new HashMap<>();
								jiraService.atualizarDescricao(issue, message, updateFields);
								enviaAlteracao(issue, updateFields, TRANSITION_ID_RELEASE_NOTES_FAILED);
							} catch (Exception e1) {
								e1.printStackTrace();
							}
						}						
					}else {
						// TODO - indicar pendencia - transitar para cancelar
						String message = "O usuário [~" + jiraEventIssue.getUser().getKey() + "] não possui permissão para lançar a versão.";
						logger.error(MESSAGE_PREFIX + " - " + jiraEventIssue.getIssue().getKey() + " - " + message);
						telegramService.sendBotMessage(MESSAGE_PREFIX + " - " + jiraEventIssue.getIssue().getKey() + " - " + message);
//						try {
//							Map<String, Object> updateFields = new HashMap<>();
//							jiraService.atualizarDescricao(issue, message, updateFields);
//							enviaAlteracao(issue, updateFields, TRANSITION_ID_RELEASE_NOTES_FAILED);
//						} catch (Exception e1) {
//							e1.printStackTrace();
//						}
					}
				}
			}
		}
//		List<String> epicThemeList = jiraEventIssue.getIssue().getFields().getEpicTheme();
//		List<String> superEpicThemeList = jiraService.findSuperEpicTheme(epicThemeList);
//
//		atualizaAreasConhecimento(jiraEventIssue.getIssue(), superEpicThemeList);
	}
	
	public static final int ISSUE_TYPE_HOTFIX = 10202;
	public static final int ISSUE_TYPE_BUGFIX = 10201;
	public static final int ISSUE_TYPE_BUG = 1;
	public static final int ISSUE_TYPE_NEWFEATURE = 2;
	public static final int ISSUE_TYPE_IMPROVEMENT = 4;
	public static final int ISSUE_TYPE_MINOR_CHANGES = 5;
	
	public JiraVersionReleaseNotesIssueTypeEnum getIssueTypeEnum(JiraIssuetype issueType){
		JiraVersionReleaseNotesIssueTypeEnum releaseNotesIssueType = null;
		switch (issueType.getId().intValue()) {
			case ISSUE_TYPE_HOTFIX:
			case ISSUE_TYPE_BUGFIX:
			case ISSUE_TYPE_BUG:
				releaseNotesIssueType = JiraVersionReleaseNotesIssueTypeEnum.BUGFIX;
				break;
			case ISSUE_TYPE_NEWFEATURE:
				releaseNotesIssueType = JiraVersionReleaseNotesIssueTypeEnum.NEW_FEATURE;
				break;
			case ISSUE_TYPE_IMPROVEMENT:
				releaseNotesIssueType = JiraVersionReleaseNotesIssueTypeEnum.IMPROVEMENT;
				break;
			default:
				releaseNotesIssueType = JiraVersionReleaseNotesIssueTypeEnum.MINOR_CHANGES;
				break;
		}
		
		return releaseNotesIssueType;
	}
	
	private String getVersaoAfetada(List<JiraVersion> versions){
		String versaoAfetada = null;
		if(versions != null && versions.size() == 1){
			JiraVersion version = versions.get(0);
			if(!version.getArchived() && !version.getReleased()){
				versaoAfetada = version.getName();
			}
		}
		return versaoAfetada;
	}
	
	private static final String TRANSITION_ID_RELEASE_NOTES_GENERATED = "141"; // TODO buscar por propriedade da transicao
	private static final String TRANSITION_ID_RELEASE_NOTES_FAILED = "151"; // TODO buscar por propriedade da transicao
	private static final String RELEASE_NOTES_JSON_FILENAME = "release-notes.json";
	
	private void atualizarDescricao(JiraIssue issue, JiraVersionReleaseNotes releaseNotes) {
		Map<String, Object> updateFields = new HashMap<>();
		try {
			String releaseNotesJson = Utils.convertObjectToJson(releaseNotes);
			// 1. envia o json do release-notes para a issue
			jiraService.sendTextAsAttachment(issue, RELEASE_NOTES_JSON_FILENAME, releaseNotesJson);
			
			// 2. converte o release-notes em um markdown do próprio jira e preenche o campo descrição
			JiraMarkdown jiraMarkdown = new JiraMarkdown();
			String jiraMarkdownRelease = releaseNotesConverter.convert(releaseNotes, jiraMarkdown);
			// 3. gera comentário na issue, solicitando confirmação
			jiraService.atualizarDescricao(issue, jiraMarkdownRelease, updateFields);
			jiraService.atualizarVersaoASerLancada(issue, releaseNotes.getVersion(), updateFields);
			jiraService.atualizarProximaVersao(issue, releaseNotes.getNextVersion(), updateFields);
			String dataReleaseNotesStr = null;
			if(releaseNotes.getReleaseDate() != null) {
				Date dataReleaseNotes = Utils.stringToDate(releaseNotes.getReleaseDate(), null);
				dataReleaseNotesStr = Utils.dateToStringPattern(dataReleaseNotes, JiraService.JIRA_DATETIME_PATTERN);
			}
			jiraService.atualizarDataReleaseNotes(issue, dataReleaseNotesStr, updateFields);
			jiraService.adicionarComentario(issue, "Por favor, confirme o texto e os detalhes do release notes a ser gerado.", updateFields);
			enviaAlteracao(issue, updateFields, TRANSITION_ID_RELEASE_NOTES_GENERATED);
		} catch (Exception e) {
			e.printStackTrace();
			try {
				updateFields = new HashMap<>();
				jiraService.atualizarDescricao(issue, "Erro ao tentar atualizar o campo de descrição: "+ e.getLocalizedMessage(), updateFields);
				enviaAlteracao(issue, updateFields, TRANSITION_ID_RELEASE_NOTES_FAILED);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	
	private void enviaAlteracao(JiraIssue issue, Map<String, Object> updateFields, String transictionId) throws JsonProcessingException{
		if(!updateFields.isEmpty()) {
			JiraIssueTransition generateReleaseNotes = jiraService.findTransitionById(issue, transictionId);
			if(generateReleaseNotes != null) {
				JiraIssueTransitionUpdate issueTransitionUpdate = new JiraIssueTransitionUpdate(generateReleaseNotes, updateFields);
				logger.info("update string: " + Utils.convertObjectToJson(issueTransitionUpdate));
				jiraService.updateIssue(issue, issueTransitionUpdate);
				telegramService.sendBotMessage(MESSAGE_PREFIX + "[" + issue.getKey() + "] Issue atualizada");
				logger.info("Issue atualizada");
			}else {
				telegramService.sendBotMessage("*" + MESSAGE_PREFIX + "[" + issue.getKey() + "] Erro!!* \n Não há transição para realizar esta alteração");
				logger.error("Não há transição para realizar esta alteração");
			}
		}
	}
}