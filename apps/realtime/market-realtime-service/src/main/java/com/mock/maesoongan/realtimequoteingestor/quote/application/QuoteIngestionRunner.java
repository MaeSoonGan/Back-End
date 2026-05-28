package com.mock.maesoongan.realtimequoteingestor.quote.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QuoteIngestionRunner implements ApplicationRunner {

    private final QuoteIngestionService quoteIngestionService;
    private final boolean autoStart;
    private final List<String> stockCodes;

    public QuoteIngestionRunner(
            QuoteIngestionService quoteIngestionService,
            @Value("${quote.ingestion.auto-start:true}") boolean autoStart,
            @Value("${quote.ingestion.stock-codes:005930,000660}") List<String> stockCodes
    ) {
        this.quoteIngestionService = quoteIngestionService;
        this.autoStart = autoStart;
        this.stockCodes = stockCodes;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!autoStart) {
            return;
        }

        quoteIngestionService.start(stockCodes);
    }
}
