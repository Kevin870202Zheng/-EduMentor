package com.edumentor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EduMentorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduMentorApplication.class, args);
    }
}
