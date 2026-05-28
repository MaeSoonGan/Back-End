package com.mock.maesoongan.realtimequoteingestor.quote.domain;

import java.time.LocalDateTime;

public interface QuoteEvent {

    QuoteEventType type();

    String code();

    String name();

    LocalDateTime sourceTimestamp();

    LocalDateTime receivedAt();

    long sequence();
}
