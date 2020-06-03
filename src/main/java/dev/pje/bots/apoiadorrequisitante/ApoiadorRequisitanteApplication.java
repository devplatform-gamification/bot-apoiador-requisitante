package dev.pje.bots.apoiadorrequisitante;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ApoiadorRequisitanteApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApoiadorRequisitanteApplication.class, args);
	}

}
