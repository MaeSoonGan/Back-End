package com.mock.maesoongan.realtimequoteingestor.quote.port;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;

public interface QuoteEventPublisher {

    void publishPrice(PriceQuoteEvent event);

    void publishOrderbook(OrderbookQuoteEvent event);

    void publishIndex(IndexQuoteEvent event);

    boolean isEnabled();
}
