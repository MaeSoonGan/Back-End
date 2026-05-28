package com.mock.maesoongan.realtimequoteingestor.quote.adapter;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingQuoteEventPublisher implements QuoteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingQuoteEventPublisher.class);

    @Override
    public void publishPrice(PriceQuoteEvent event) {
        log.info("Price quote event. code={}, price={}, sequence={}", event.code(), event.price(), event.sequence());
    }

    @Override
    public void publishOrderbook(OrderbookQuoteEvent event) {
        log.info("Orderbook quote event. code={}, asks={}, bids={}, sequence={}",
                event.code(), event.asks().size(), event.bids().size(), event.sequence());
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
