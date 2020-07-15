package dev.pje.bots.apoiadorrequisitante.amqp.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.response.GitlabBranchResponse;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;
import com.devplatform.model.jira.JiraEventChangelogItems;
import com.devplatform.model.jira.JiraIssue;
import com.devplatform.model.jira.JiraIssueTransition;
import com.devplatform.model.jira.JiraIssuetype;
import com.devplatform.model.jira.JiraProject;
import com.devplatform.model.jira.JiraVersion;
import com.devplatform.model.jira.JiraVersionReleaseNotes;
import com.devplatform.model.jira.JiraVersionReleaseNotesIssueTypeEnum;
import com.devplatform.model.jira.event.JiraEventIssue;
import com.devplatform.model.jira.request.JiraIssueTransitionUpdate;
import com.fasterxml.jackson.core.JsonProcessingException;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;
import dev.pje.bots.apoiadorrequisitante.services.TelegramService;
import dev.pje.bots.apoiadorrequisitante.utils.ReleaseNotesConverter;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.AsciiDocMarkdown;
import dev.pje.bots.apoiadorrequisitante.utils.markdown.JiraMarkdown;

@Component
public class VersionLaunchHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(VersionLaunchHandler.class);

	@Autowired
	private JiraService jiraService;

	@Autowired
	private GitlabService gitlabService;

	@Autowired
	private TelegramService telegramService;
	
	@Autowired
	private ReleaseNotesConverter releaseNotesConverter;
	
	public static final String MESSAGE_PREFIX = "[VERSION-LAUNCH][JIRA][GITLAB]";
	
	public void handle(JiraEventIssue jiraEventIssue) throws Exception {
		if(jiraEventIssue != null && jiraEventIssue.getIssue() != null) {
		JiraIssue issue = jiraEventIssue.getIssue();
		// 1. verifica se é do tipo "geracao de nova versao"
		if(issue.getFields() != null && issue.getFields().getIssuetype() != null
				&& issue.getFields().getIssuetype().getId() != null
				&& JiraService.ISSUE_TYPE_NEW_VERSION.toString().equals(issue.getFields().getIssuetype().getId().toString())) {
			// 2. verifica se a issue tramitou para a situacao "confirmar" release notes
			boolean releaseNotesConfirmed = false;
			if(jiraEventIssue.getChangelog() != null) {
				List<JiraEventChangelogItems> changeItems = jiraEventIssue.getChangelog().getItems();
				for (JiraEventChangelogItems changedItem : changeItems) {
					if(changedItem != null && JiraService.FIELD_STATUS.equals(changedItem.getField())) {
						if(JiraService.STATUS_RELEASE_NOTES_CONFIRMED_ID.toString().equals(changedItem.getTo())) {
							releaseNotesConfirmed = true;
							break;
						}
					}
				}
			}
			if(releaseNotesConfirmed) {
				// 3. estamos no caminho correto, indicar no log
				telegramService.sendBotMessage(MESSAGE_PREFIX + " - " + issue.getKey() + " - " + jiraEventIssue.getIssueEventTypeName().name());
				// 4.- a pessoa que atuou na issue está dentro do grupo de pessoas que podem atuar?
				if(jiraService.isLancadorVersao(jiraEventIssue.getUser())) {
					// TODO
					// 1. Recupera o anexo com o json do releaseNotes
					String releaseNotesAttachment = jiraService.getAttachmentContent(issue, RELEASE_NOTES_JSON_FILENAME);
					if(releaseNotesAttachment != null) {
						JiraVersionReleaseNotes releaseNotes = Utils.convertJsonToJiraReleaseNotes(releaseNotesAttachment);
						
						if(releaseNotes != null) {
							// 2. Gerar documento asciidoc e incluir no docs.pje.jus.br
							criarDocumentoReleaseNotesNoProjetoDocumentacao(issue, releaseNotes);
							// 3. Gerar release do gitlab para adicionar a tag correspondente
							// 4. Fazer alterações relacionadas ao gitlab
							// 5. Fazer alterações relacionadas ao jira
							// 6. gerar informações para a próxima versão no gitlab
							// 7. fazer comunicação de que a nova versão foi disponibilizada
//							throw new Exception("erro para os testes continuarem!!!");
						}
					}
					}
				}
			}
		}
//		List<String> epicThemeList = jiraEventIssue.getIssue().getFields().getEpicTheme();
//		List<String> superEpicThemeList = jiraService.findSuperEpicTheme(epicThemeList);
//
//		atualizaAreasConhecimento(jiraEventIssue.getIssue(), superEpicThemeList);
	}
	
	public static final String PATHNAME_DOCUMENTACAO_RELEASE_NOTES = "src/main/asciidoc/projetos/";
	public static final String RELEASE_NOTES_PREFIX = "release-notes_";
	public static final String RELEASE_NOTES_SUFFIX = ".adoc";
	
	private void criarDocumentoReleaseNotesNoProjetoDocumentacao(JiraIssue issue, JiraVersionReleaseNotes releaseNotes) {
		AsciiDocMarkdown asciiDocMarkdown = new AsciiDocMarkdown();
		String texto = releaseNotesConverter.convert(releaseNotes, asciiDocMarkdown);
		String filePath = getPathNameReleaseNotes(issue.getFields().getProject(), releaseNotes.getVersion());
		GitlabBranchResponse branch = gitlabService.createBranchProjetoDocumentacao(GitlabService.PROJECT_DOCUMENTACO, issue.getKey());
		// criar um arquivo no projeto docs.pje.jus.br com o conteúdo deste arquivo
		GitlabCommitResponse commitResponse = gitlabService.sendTextAsFileToBranch(GitlabService.PROJECT_DOCUMENTACO, branch, 
				filePath, texto, "Gerando release notes da versão " + releaseNotes.getVersion());

		logger.info(commitResponse.toString());
	}
	
	private String getPathNameReleaseNotes(JiraProject project, String versao) {
		String projectNameInDocumentacao = project.getName();
		if(project.getKey().equalsIgnoreCase("PJEII") || project.getKey().equalsIgnoreCase("PJEVII") 
				|| project.getKey().equalsIgnoreCase("PJELEG") || project.getKey().equalsIgnoreCase("PJEVSII") 
				|| project.getKey().equalsIgnoreCase("TESTE")) {
			projectNameInDocumentacao = "pje-legacy";
		}
		
		StringBuilder pathNameRelease = new StringBuilder(PATHNAME_DOCUMENTACAO_RELEASE_NOTES)
				.append(projectNameInDocumentacao)
				.append("/release-notes/includes/")
				.append(RELEASE_NOTES_PREFIX)
				.append(versao.replaceAll("\\.", "-"))
				.append(RELEASE_NOTES_SUFFIX);
		
		return pathNameRelease.toString();
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
	
	private static final String TRANSICTION_REGENERATE_RELEASE_NOTES = "Regerar release notes"; // TODO buscar por propriedade da transicao
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
			enviaAlteracao(issue, updateFields);
		} catch (Exception e) {
			e.printStackTrace();
			try {
				jiraService.atualizarDescricao(issue, "Erro ao tentar atualizar o campo de descrição: "+ e.getLocalizedMessage(), updateFields);
				enviaAlteracao(issue, updateFields);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	
	private void enviaAlteracao(JiraIssue issue, Map<String, Object> updateFields) throws JsonProcessingException{
		if(!updateFields.isEmpty()) {
			JiraIssueTransition generateReleaseNotes = jiraService.findTransicao(issue, TRANSICTION_REGENERATE_RELEASE_NOTES);
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