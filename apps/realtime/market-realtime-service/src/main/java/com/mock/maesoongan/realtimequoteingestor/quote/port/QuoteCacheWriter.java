package com.mock.maesoongan.realtimequoteingestor.quote.port;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;

public interface QuoteCacheWriter {

    void savePrice(PriceQuoteEvent event);

    void saveOrderbook(OrderbookQuoteEvent event);

    void saveIndex(IndexQuoteEvent event);

    boolean isEnabled();
}
