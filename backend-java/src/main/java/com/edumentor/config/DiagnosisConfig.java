package com.edumentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "diagnosis")
public class DiagnosisConfig {
    private double masteryThreshold = 0.80;
    private double weakThreshold = 0.50;
    private double zpdRange = 0.15;
    private int maxDaysBack = 365;
    private String forgettingCurveIntervals = "1,3,7,14,30,60,120";
}
