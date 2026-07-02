package com.consultorprocessos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
@Slf4j
public class ConsultorProcessosApplication {

    private final Environment environment;

    public ConsultorProcessosApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(ConsultorProcessosApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String[] profiles = environment.getActiveProfiles();
        String activeProfile = profiles.length > 0 ? profiles[0] : "default";

        if ("prod".equals(activeProfile)) {
            boolean devModeEnabled = environment.getProperty(
                "app.dev-mode.enabled", Boolean.class, false);
            if (devModeEnabled) {
                log.error("CONFIGURAÇÃO INVÁLIDA: dev-mode está habilitado no perfil prod. Encerrando.");
                System.exit(1);
            }
            boolean realRequestsEnabled = environment.getProperty(
                "app.courts.real-requests-enabled", Boolean.class, false);
            log.info("Aplicação iniciada em modo PRODUÇÃO.");
        } else {
            log.info("Aplicação iniciada. Perfil ativo: {}", activeProfile);
            log.info("Consultas reais aos tribunais: {}",
                environment.getProperty("app.courts.real-requests-enabled", "false"));
        }
    }
}