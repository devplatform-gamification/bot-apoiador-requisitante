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

import com.devplatform.model.bot.VersionReleaseNoteIssues;
import com.devplatform.model.bot.VersionReleaseNotes;
import com.devplatform.model.bot.VersionReleaseNotesIssueTypeEnum;
import com.devplatform.model.gitlab.GitlabTag;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraUser;
import com.devplatform.model.jira.event.JiraEventIssue;

import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.utils.JiraUtils;
import dev.pje.bots.apoiadorrequisitante.utils.ReleaseNotesTextModel;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class LanVersion04GenerateReleaseNotesHandler extends Handler<JiraEventIssue>{

	private static final Logger logger = LoggerFactory.getLogger(LanVersion04GenerateReleaseNotesHandler.class);

	@Override
	protected Logger getLogger() {
		return logger;
	}
	
	@Override
	public String getMessagePrefix() {
		return "|VERSION-LAUNCH||04||GENERATE-RELEASE-NOTES|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}
	
	@Autowired
	private ReleaseNotesTextModel releaseNotesModel;
	
	private static final String TRANSITION_ID_IMPEDIMENTO = "151"; // TODO buscar por propriedade da transicao
	private static final String TRANSITION_ID_SOLICITAR_CONFIRMACAO_RELEASE_NOTES = "141"; // TODO buscar por propriedade da transicao

	/**
	 * :: Gerando release notes ::
	 *    Verifica se a versao já foi lançada
	 *    Verifica se foi preenchido o campo: versao a ser lancada
	 *    Verifica se foi preenchido o campo: proxima versao
	 *    Gera o release notes RN
	 *    Salva o RN como um anexo .json da issue
	 *    Atualiza a descricao da issue com a previa da RN
	 *    Atualiza os campos pendentes/encontrados
	 */
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		messages.clean();
		if (jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
			JiraIssue issue = jiraEventIssue.getIssue();
			// 1. verifica se é do tipo "geracao de nova versao"
			if(JiraUtils.isIssueFromType(issue, JiraService.ISSUE_TYPE_NEW_VERSION) &&
					JiraUtils.isIssueInStatus(issue, JiraService.STATUS_RELEASE_NOTES_ID) &&
					JiraUtils.isIssueChangingToStatus(jiraEventIssue, JiraService.STATUS_RELEASE_NOTES_ID)) {

				messages.setId(issue.getKey());
				messages.debug(jiraEventIssue.getIssueEventTypeName().name());
				
				Map<String, Object> updateFields = new HashMap<>();

				String versaoASerLancada = issue.getFields().getVersaoSeraLancada();
				String versaoAfetada = JiraUtils.getVersaoAfetada(issue.getFields().getVersions());
				String proximaVersao = issue.getFields().getProximaVersao();

				// 4.- a pessoa que solicitou a operacao está dentro do grupo de pessoas que podem abrir?
				if(jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
					boolean tagJaExistente = false;
					String dataLancamentoVersao = null;
					JiraUser usuarioCommiter = null;
					String gitlabProjectId = jiraService.getGitlabProjectFromIssue(issue);
					
					if(StringUtils.isNotBlank(versaoAfetada)) {
						if(StringUtils.isBlank(versaoASerLancada)){
							versaoASerLancada = versaoAfetada;
						}
						// verifica se a tag da versão já existe
						if(StringUtils.isNotBlank(gitlabProjectId)) {
							GitlabTag tag = gitlabService.getVersionTag(gitlabProjectId, versaoASerLancada);
							if(tag != null && tag.getCommit() != null) {
								tagJaExistente = true;
								if(tag.getCommit().getCommittedDate() != null) {
									dataLancamentoVersao = tag.getCommit().getCommittedDate();
								}
								if(tag.getCommit().getCommitterName() != null) {
									usuarioCommiter = jiraService.getUserFromUserName(tag.getCommit().getCommitterEmail());
								}
								messages.info("A tag: " + versaoASerLancada + " já foi lançada em: " + dataLancamentoVersao);
							}
							if(StringUtils.isBlank(proximaVersao)){
								// calcula com base no incremento de 1 dígito do terceiro número da "versão a ser lançada"
								proximaVersao = Utils.calculateNextOrdinaryVersion(versaoASerLancada, 2);
							}

						}
						// busca as issues cujo "fixversion" = "versão afetada" e gera um release notes
						String jql = jiraService.getJqlIssuesFromFixVersion(versaoAfetada, true, null);
						List<JiraIssue> issues = jiraService.getIssuesFromJql(jql);
						if(issues != null && issues.size() > 0){
							VersionReleaseNotes releaseNotes = new VersionReleaseNotes();
							releaseNotes.setGitlabProjectId(gitlabProjectId);
							releaseNotes.setIssueKey(issue.getKey());
							releaseNotes.setJql(jql);
							releaseNotes.setAuthor(jiraEventIssue.getUser());
							if(usuarioCommiter != null) {
								releaseNotes.setAuthor(usuarioCommiter);
							}
							releaseNotes.setVersion(versaoASerLancada);
							releaseNotes.setAffectedVersion(versaoAfetada);
							releaseNotes.setNextVersion(proximaVersao);
							if(tagJaExistente && dataLancamentoVersao != null) {
								releaseNotes.setReleaseDate(dataLancamentoVersao.toString());
							}
							releaseNotes.setProject(issue.getFields().getProject().getName());
							if(issue.getFields().getDestaquesReleaseNotes() != null && !issue.getFields().getDestaquesReleaseNotes().isEmpty()){
								releaseNotes.setVersionHighlights(issue.getFields().getDestaquesReleaseNotes());
							}
							if(issue.getFields().getTipoVersao() != null && issue.getFields().getTipoVersao().getValue() != null){
								releaseNotes.setVersionType(issue.getFields().getTipoVersao().getValue());
							}
							List<VersionReleaseNoteIssues> newFeatures = new ArrayList<>();
							List<VersionReleaseNoteIssues> improvements = new ArrayList<>();
							List<VersionReleaseNoteIssues> bugsFixes = new ArrayList<>();
							List<VersionReleaseNoteIssues> minorChanges = new ArrayList<>();
							for (JiraIssue issueItem : issues) {
								VersionReleaseNoteIssues releaseItem = new VersionReleaseNoteIssues();
								JiraUser author = issueItem.getFields().getResponsavelCodificacao();
								releaseItem.setAuthor(author);
								releaseItem.setIssueKey(issueItem.getKey());
								releaseItem.setSummary(Utils.cleanSummary(issueItem.getFields().getSummary()));
								
								releaseItem.setPriority(issueItem.getFields().getPriority().getId().intValue());
								if(issueItem.getFields().getNotasRelease() != null){
									releaseItem.setReleaseObservation(issueItem.getFields().getNotasRelease());
								}
								JiraIssuetype issueType = issueItem.getFields().getIssuetype();
								VersionReleaseNotesIssueTypeEnum releaseIssueType = getIssueTypeEnum(issueType);
								releaseItem.setIssueType(releaseIssueType.name());
								
								if(VersionReleaseNotesIssueTypeEnum.BUGFIX.equals(releaseIssueType)){
									bugsFixes.add(releaseItem);
								}else if(VersionReleaseNotesIssueTypeEnum.NEW_FEATURE.equals(releaseIssueType)){
									newFeatures.add(releaseItem);
								}else if(VersionReleaseNotesIssueTypeEnum.IMPROVEMENT.equals(releaseIssueType)){
									improvements.add(releaseItem);
								}else{
									minorChanges.add(releaseItem);
								}
							}
							releaseNotes.setBugs(bugsFixes);
							releaseNotes.setNewFeatures(newFeatures);
							releaseNotes.setImprovements(improvements);
							releaseNotes.setMinorChanges(minorChanges);
							
							// 1. envia o json do release-notes para a issue
							String releaseNotesJson = Utils.convertObjectToJson(releaseNotes);
							jiraService.sendTextAsAttachment(issue, JiraService.RELEASE_NOTES_JSON_FILENAME, releaseNotesJson);

							JiraMarkdown jiraMarkdown = new JiraMarkdown();
							releaseNotesModel.setReleaseNotes(releaseNotes);
							String jiraMarkdownRelease = releaseNotesModel.convert(jiraMarkdown);
							jiraService.atualizarDescricao(issue, jiraMarkdownRelease, updateFields);

							String dataTagStr = null;
							if(releaseNotes.getReleaseDate() != null) {
								Date dataReleaseNotes = Utils.stringToDate(releaseNotes.getReleaseDate(), null);
								dataTagStr = Utils.dateToStringPattern(dataReleaseNotes, JiraService.JIRA_DATETIME_PATTERN);
							}
							jiraService.atualizarDataGeracaoTag(issue, dataTagStr, updateFields);
							messages.info("Por favor, confirme o texto e os detalhes do release notes a ser gerado.");
						}else{
							messages.error("Não foram encontradas issues finalizadas para a versão " + versaoAfetada);
						}
					}else {
						messages.error("Não foi identificada uma versão afetada, por favor, valide as informações da issue.");
					}
				}else {
					messages.error("O usuário [~" + jiraEventIssue.getUser().getKey() + "] não possui permissão para lançar a versão.");
				}
				if(messages.hasSomeError()) {
					// tramita para o impedmento, enviando as mensagens nos comentários
					Map<String, Object> updateFieldsErrors = new HashMap<>();
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFieldsErrors);
					enviarAlteracaoJira(issue, updateFieldsErrors, TRANSITION_ID_IMPEDIMENTO);
				}else {
					// tramita automaticamente, enviando as mensagens nos comentários
					jiraService.atualizarVersaoASerLancada(issue, versaoASerLancada, updateFields);
					jiraService.atualizarProximaVersao(issue, proximaVersao, updateFields);
					jiraService.adicionarComentario(issue, messages.getMessagesToJira(), updateFields);
					enviarAlteracaoJira(issue, updateFields, TRANSITION_ID_SOLICITAR_CONFIRMACAO_RELEASE_NOTES);
				}
			}
		}
	}
	
	public static final int ISSUE_TYPE_HOTFIX = 10202;
	public static final int ISSUE_TYPE_BUGFIX = 10201;
	public static final int ISSUE_TYPE_BUG = 1;
	public static final int ISSUE_TYPE_NEWFEATURE = 2;
	public static final int ISSUE_TYPE_IMPROVEMENT = 4;
	public static final int ISSUE_TYPE_MINOR_CHANGES = 5;
	
	public VersionReleaseNotesIssueTypeEnum getIssueTypeEnum(JiraIssuetype issueType){
		VersionReleaseNotesIssueTypeEnum releaseNotesIssueType = null;
		switch (issueType.getId().intValue()) {
			case ISSUE_TYPE_HOTFIX:
			case ISSUE_TYPE_BUGFIX:
			case ISSUE_TYPE_BUG:
				releaseNotesIssueType = VersionReleaseNotesIssueTypeEnum.BUGFIX;
				break;
			case ISSUE_TYPE_NEWFEATURE:
				releaseNotesIssueType = VersionReleaseNotesIssueTypeEnum.NEW_FEATURE;
				break;
			case ISSUE_TYPE_IMPROVEMENT:
				releaseNotesIssueType = VersionReleaseNotesIssueTypeEnum.IMPROVEMENT;
				break;
			default:
				releaseNotesIssueType = VersionReleaseNotesIssueTypeEnum.MINOR_CHANGES;
				break;
		}
		
		return releaseNotesIssueType;
	}		
}