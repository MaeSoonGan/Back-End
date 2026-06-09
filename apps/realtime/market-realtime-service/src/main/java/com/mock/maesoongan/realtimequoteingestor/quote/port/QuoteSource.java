package com.mock.maesoongan.realtimequoteingestor.quote.port;

import java.util.List;

public interface QuoteSource {

    void start(QuoteEventHandler handler);

    void stop();

    void subscribe(List<String> stockCodes);

    void unsubscribe(List<String> stockCodes);

    default void subscribeIndexes(List<String> markets) {
    }

    default void unsubscribeIndexes(List<String> markets) {
    }

    boolean isConnected();
}
