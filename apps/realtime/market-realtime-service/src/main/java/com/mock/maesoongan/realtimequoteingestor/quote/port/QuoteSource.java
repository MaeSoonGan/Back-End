package com.mock.maesoongan.realtimequoteingestor.quote.port;

import java.util.List;

public interface QuoteSource {

    void start(QuoteEventHandler handler);

    void stop();

    void subscribePrices(List<String> stockCodes);

    void unsubscribePrices(List<String> stockCodes);

    void subscribeOrderbooks(List<String> stockCodes);

    void unsubscribeOrderbooks(List<String> stockCodes);

    default void subscribeIndexes(List<String> markets) {
    }

    default void unsubscribeIndexes(List<String> markets) {
    }

    // 소스 연결 초기화(재연결). KIS의 경우 등록 한도 누적을 비우는 용도.
    default void reset() {
    }

    boolean isConnected();
}
