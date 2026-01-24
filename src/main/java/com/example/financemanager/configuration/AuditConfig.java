package com.example.financemanager.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditConfig {

    @Bean
    public AuditorAwareImpl auditorProvider() {
        return new AuditorAwareImpl();
    }
}
