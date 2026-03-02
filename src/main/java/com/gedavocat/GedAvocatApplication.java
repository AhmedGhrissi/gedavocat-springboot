package com.gedavocat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application principale GED Avocat
 * Système de Gestion Électronique de Documents pour Avocats
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class GedAvocatApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(GedAvocatApplication.class, args);
    }
}
