package com.mock.maesoongan.tradesyncworker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class TradeSyncWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeSyncWorkerApplication.class, args);
    }
}
