package dev.pje.bots.apoiadorrequisitante.amqp.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AmqpProducer {

    private static final Logger log = LoggerFactory.getLogger(AmqpProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

//    public void sendMessage(Notification msg){
//        System.out.println("Send msg = " + msg.toString());
//        rabbitTemplate.convertAndSend(yawnProperties.getRoutingkeyPrefix(), msg);
//    }

    public void sendMessageGeneric(Object msg, String routingkeyPrefix, String eventType){
    	String routingKey = routingkeyPrefix
    							.concat(".")
    							.concat(eventType);

    	sendMessageGeneric(msg, routingKey);
    }
    
    public void sendMessageGeneric(Object msg, String routingkey){
        log.info("Send Generic msg with routingkey: ["+ routingkey + "]\n" + msg.toString());
        rabbitTemplate.convertAndSend(routingkey ,msg);
    }

}