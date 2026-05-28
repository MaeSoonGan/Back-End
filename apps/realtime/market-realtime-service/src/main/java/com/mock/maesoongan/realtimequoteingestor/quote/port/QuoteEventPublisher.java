package com.mock.maesoongan.realtimequoteingestor.quote.port;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;

public interface QuoteEventPublisher {

    void publishPrice(PriceQuoteEvent event);

    void publishOrderbook(OrderbookQuoteEvent event);

    boolean isEnabled();
}
