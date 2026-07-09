package com.consultorprocessos.scheduler.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
@EnableScheduling
public class SchedulerConfig {
}