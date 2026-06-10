package com.mock.maesoongan.realtimequoteingestor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RealtimeQuoteIngestorApplication {

    public static void main(String[] args) {
        SpringApplication.run(RealtimeQuoteIngestorApplication.class, args);
    }
}
