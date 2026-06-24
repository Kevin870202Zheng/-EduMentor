package com.edumentor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bkt")
public class BktConfig {
    private boolean enabled = true;
    private double defaultPInit = 0.15;
    private double defaultPLearn = 0.25;
    private double defaultPGuess = 0.15;
    private double defaultPSlip = 0.10;
    private double masteryThreshold = 0.95;
    private boolean useDefaultParams = true;
    private StateConfig state = new StateConfig();
    private String dataPath = "data/bkt";

    @Data
    public static class StateConfig {
        private int persistInterval = 300;
        private int cacheSize = 10000;
    }
}
