package dev.pje.bots.apoiadorrequisitante.amqp.config;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;

@EnableRabbit
@Configuration
public class AmqpConfig {
	
	@Autowired
	private ConnectionFactory connectionFactory;

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory() {
      SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
      factory.setConnectionFactory(connectionFactory);
      factory.setMessageConverter(messageConverter());
      return factory;
    }
    
    @Bean
    public MappingJackson2MessageConverter consumerJackson2MessageConverter() {
        return new MappingJackson2MessageConverter();
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
