package com.gedavocat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration du pool de threads pour les tâches asynchrones (@Async).
 *
 * Utilisé pour les opérations non-critiques au retour HTTP :
 *  - Contrôles LAB-FT lors de la création d'un client
 *  - Envoi d'emails d'invitation (SMTP bloquant)
 *
 * Taille du pool choisie pour un hébergement CAX11 (4 vCPU / 8 GB) :
 *  - corePoolSize  = 4 : threads toujours disponibles
 *  - maxPoolSize   = 16 : burst temporaire
 *  - queueCapacity = 100 : file d'attente avant rejet
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
