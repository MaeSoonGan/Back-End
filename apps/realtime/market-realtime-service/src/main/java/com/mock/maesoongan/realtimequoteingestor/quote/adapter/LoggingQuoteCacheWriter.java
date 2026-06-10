package com.mock.maesoongan.realtimequoteingestor.quote.adapter;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteCacheWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingQuoteCacheWriter implements QuoteCacheWriter {

    private static final Logger log = LoggerFactory.getLogger(LoggingQuoteCacheWriter.class);

    @Override
    public void savePrice(PriceQuoteEvent event) {
        log.debug("Skip saving price quote to cache. code={}, sequence={}", event.code(), event.sequence());
    }

    @Override
    public void saveOrderbook(OrderbookQuoteEvent event) {
        log.debug("Skip saving orderbook quote to cache. code={}, sequence={}", event.code(), event.sequence());
    }

    @Override
    public void saveIndex(IndexQuoteEvent event) {
        log.debug("Skip saving index quote to cache. market={}, sequence={}", event.name(), event.sequence());
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
