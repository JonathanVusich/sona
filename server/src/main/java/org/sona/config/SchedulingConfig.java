package org.sona.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Runs the {@code @Scheduled} methods, such as the ingest worker's passes.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
