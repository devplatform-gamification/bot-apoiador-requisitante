package dev.pje.bots.apoiadorrequisitante.schedule;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.pje.bots.apoiadorrequisitante.amqp.config.AmqpProducer;
import dev.pje.bots.apoiadorrequisitante.handlers.gitlab.Gitlab080UpdateMergeRequestsHandler;
import dev.pje.bots.apoiadorrequisitante.handlers.jira.Jira060MonitoramentoDemandasEmRevisaoHandler;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;

@Component
public class Scheduler {
	
    private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);

    @Autowired
    private AmqpProducer amqpProducer;
    
    @Value("${spring.rabbitmq.template.custom.gamification010-classificar-areas-conhecimento.routing-key-start}")
    private String routingKey;

    /**
     * Executa todas as últimas sextas, sábado e domingo do mês à 1 hora, 5 minutos da manhã
     * Reference: https://crontab.cronhub.io/
     * @throws Exception 
     */
	@Scheduled(cron = "0 5 1 24-31 * 5-7")
	public void atualizacaoPontuacoesAreasConhecimento() throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat(JiraService.JIRA_DATETIME_PATTERN);
		Date now = new Date();
		String strDate = sdf.format(now);
		String msg = "Indicando a necessidade de reclassificação das áreas de conehcimento em :: " + strDate;
		logger.info(msg);
		
		amqpProducer.sendMessageGeneric(msg, routingKey);
   }	

	@Autowired
	Jira060MonitoramentoDemandasEmRevisaoHandler jira060MonitoramentoDemandasEmRevisao;
    /**
     * Executa todas as segundas-feiras às 5h20 da manhã
     * Reference: https://crontab.cronhub.io/
     * @throws Exception 
     */
	@Scheduled(cron = "0 20 5 * * 1")
	public void verificaDemandasRevisaoPendenteDeTribunalRequisitante() throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat(JiraService.JIRA_DATETIME_PATTERN);
		Date now = new Date();
		String strDate = sdf.format(now);
		String msg = "Verificando as demandas em revisão que estão pendentes de homologação pelo tribunal requisitante :: " + strDate;
		logger.info(msg);
		
		jira060MonitoramentoDemandasEmRevisao.handle(msg);
   }	

	@Autowired
	Gitlab080UpdateMergeRequestsHandler gitlab080UpdateMergeRequests;
    /**
     * Executa de 15 em 15 dias aos sábados e domingos, às 4h30 da manhã
     * Reference: https://crontab.cronhub.io/
     * @throws Exception 
     */
	@Scheduled(cron = "0 30 4 11-17,25-31 * 6-7")
	public void atualizaListaMergesRequestsAbertos() throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat(JiraService.JIRA_DATETIME_PATTERN);
		Date now = new Date();
		String strDate = sdf.format(now);
		String msg = "Verificando os merge requests abertos, para saber se podem ser atualizados automaticamente :: " + strDate;
		logger.info(msg);
		
		gitlab080UpdateMergeRequests.handle(msg);
   }	

}
