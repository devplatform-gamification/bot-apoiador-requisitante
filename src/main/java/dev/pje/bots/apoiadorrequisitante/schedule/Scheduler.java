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
}
