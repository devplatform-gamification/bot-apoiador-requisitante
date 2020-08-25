package dev.pje.bots.apoiadorrequisitante.amqp.handlers.gitlab;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.devplatform.model.gitlab.event.GitlabEventPushTag;
import com.devplatform.model.gitlab.response.GitlabCommitResponse;

import dev.pje.bots.apoiadorrequisitante.amqp.handlers.Handler;
import dev.pje.bots.apoiadorrequisitante.amqp.handlers.MessagesLogger;
import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.utils.Utils;

@Component
public class Gitlab05TagPushPrepareNextVersionHandler extends Handler<GitlabEventPushTag>{

	private static final Logger logger = LoggerFactory.getLogger(Gitlab05TagPushPrepareNextVersionHandler.class);

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
		return "|GITLAB||04||TAG-PUSHED|";
	}

	@Override
	public int getLogLevel() {
		return MessagesLogger.LOGLEVEL_INFO;
	}

	/**
	 * :: Preparação para a próxima versão [lançamento de tag] :: (geral)
	 * 	-- se for criação de tag:
	 * --- identifica como padrão da próxima versão o branch default do projeto
	 * --- altera a versão do POM.XML
	 * --- verifica se é necessário criar pasta de script da versão e já cria
	 * --- Inicializar os metadados da próxima versão no jira
	 * ---- verifica se já existe versão nos projetos relacionados no jira
	 * ---- verifica se já existe "sprint do grupo" para essa nova versão
	 * ---- TODO - pensar melhor - migra as issues da sprint anterior (e que não estejam em um status de homologada) para a nova sprint
	 * --- se for uma tag RC, apenas confirma se realmente o projeto utiliza gitflow e faz:
	 * ---- gera branch de release relacionado à versão
	 * 
	 * -- se for cancelamento de tag:
	 * --- não faz nada!?
	 */
	@Override
	public void handle(GitlabEventPushTag gitlabEventTag) throws Exception {
		messages.clean();
		if (gitlabEventTag != null && gitlabEventTag.getProjectId() != null) {

			String gitlabProjectId = gitlabEventTag.getProjectId().toString();
			Boolean isTagReleaseCandidate = false;
			if(StringUtils.isNotBlank(gitlabEventTag.getRef()) && gitlabEventTag.getRef().endsWith(GitlabService.TAG_RELEASE_CANDIDATE_SUFFIX)) {
				// para confirmar, verifica se o projeto implementa o gitflwo
				isTagReleaseCandidate = gitlabService.isProjectImplementsGitflow(gitlabProjectId);
			}
			Boolean tagCreated = true;
			String referenceCommit = gitlabEventTag.getAfter();
			if(StringUtils.isNotBlank(referenceCommit)) {
				try {
					Integer afterHash = Integer.valueOf(referenceCommit);
					if(afterHash == 0) {
						tagCreated = false;
						referenceCommit = gitlabEventTag.getBefore();
					}
				}catch (Exception e) {
					if(e instanceof NumberFormatException) {
						messages.debug("O hash do commit " + referenceCommit + " parece válido");
					}
				}
			}
			if(StringUtils.isNotBlank(referenceCommit)) {
				String tagVersion = gitlabService.getActualVersion(gitlabProjectId, referenceCommit, true);
				if(StringUtils.isNotBlank(tagVersion)) {
					if(tagCreated) {
						

					}else {
						
					}
					
					
					
					
					
					
					
					
					
					
				}else {
					messages.error("Falhou ao tentar identificar a versão do projeto: " + gitlabProjectId + " no commit: "+ referenceCommit);
				}
			}
		}
	}
}