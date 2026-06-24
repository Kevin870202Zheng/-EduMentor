package com.edumentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alert")
public class AlertConfig {
    private long checkInterval = 3600;
    private double blueThreshold = 0.60;
    private double yellowThreshold = 0.50;
    private double orangeThreshold = 0.40;
    private double redThreshold = 0.30;
}
