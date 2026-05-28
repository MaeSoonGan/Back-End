package com.mock.maesoongan.realtimequoteingestor.quote.adapter;

import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventHandler;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "quote.source", name = "mode", havingValue = "noop")
public class NoopQuoteSource implements QuoteSource {

    private boolean connected;

    @Override
    public void start(QuoteEventHandler handler) {
        connected = true;
    }

    @Override
    public void stop() {
        connected = false;
    }

    @Override
    public void subscribe(List<String> stockCodes) {
    }

    @Override
    public void unsubscribe(List<String> stockCodes) {
    }

    @Override
    public boolean isConnected() {
        return connected;
    }
}
