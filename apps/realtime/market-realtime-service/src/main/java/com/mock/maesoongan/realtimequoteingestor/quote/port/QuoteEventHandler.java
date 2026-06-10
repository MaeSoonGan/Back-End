package com.mock.maesoongan.realtimequoteingestor.quote.port;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;

public interface QuoteEventHandler {

    void handlePrice(PriceQuoteEvent event);

    void handleOrderbook(OrderbookQuoteEvent event);

    void handleIndex(IndexQuoteEvent event);
}
