package com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KisRealtimeParserTest {

    @Test
    void parsesPriceRealtimeMessage() {
        KisRealtimeParser parser = new KisRealtimeParser(properties());
        String payload = String.join("^", priceFields());

        KisRealtimeParser.ParsedRealtimeMessage parsed = parser.parse(
                "0|H0STCNT0|001|" + payload,
                null
        );

        PriceQuoteEvent event = parsed.priceEvents().get(0);
        assertEquals("005930", event.code());
        assertEquals(new BigDecimal("73100"), event.price());
        assertEquals(new BigDecimal("-500"), event.change());
        assertEquals(new BigDecimal("-0.68"), event.changeRate());
        assertEquals(12345678L, event.volume());
        assertEquals("2026-05-27T12:39:29", event.sourceTimestamp().toString());
        assertEquals(0, parsed.orderbookEvents().size());
    }

    @Test
    void parsesOrderbookRealtimeMessage() {
        KisRealtimeParser parser = new KisRealtimeParser(properties());
        String payload = String.join("^", orderbookFields());

        KisRealtimeParser.ParsedRealtimeMessage parsed = parser.parse(
                "0|H0STASP0|001|" + payload,
                null
        );

        OrderbookQuoteEvent event = parsed.orderbookEvents().get(0);
        assertEquals("005930", event.code());
        assertEquals(10, event.asks().size());
        assertEquals(10, event.bids().size());
        assertEquals(new BigDecimal("73200"), event.asks().get(0).price());
        assertEquals(1000L, event.asks().get(0).quantity());
        assertEquals(new BigDecimal("73100"), event.bids().get(0).price());
        assertEquals(2000L, event.bids().get(0).quantity());
        assertEquals(0, parsed.priceEvents().size());
    }

    @Test
    void parsesIndexRealtimeMessage() {
        KisRealtimeParser parser = new KisRealtimeParser(properties());
        String payload = String.join("^", indexFields());

        KisRealtimeParser.ParsedRealtimeMessage parsed = parser.parse(
                "0|H0UPCNT0|001|" + payload,
                null
        );

        IndexQuoteEvent event = parsed.indexEvents().get(0);
        assertEquals("0001", event.code());
        assertEquals("KOSPI", event.name());
        assertEquals(new BigDecimal("2847.15"), event.value());
        assertEquals(new BigDecimal("15.42"), event.change());
        assertEquals(new BigDecimal("0.54"), event.changeRate());
        assertEquals(123456L, event.volume());
        assertEquals(0, parsed.priceEvents().size());
        assertEquals(0, parsed.orderbookEvents().size());
    }

    @Test
    void parsesKosdaqIndexCode() {
        KisRealtimeParser parser = new KisRealtimeParser(properties());
        List<String> fields = indexFields();
        fields.set(0, "1001");
        fields.set(2, "962.15");
        String payload = String.join("^", fields);

        KisRealtimeParser.ParsedRealtimeMessage parsed = parser.parse(
                "0|H0UPCNT0|001|" + payload,
                null
        );

        IndexQuoteEvent event = parsed.indexEvents().get(0);
        assertEquals("1001", event.code());
        assertEquals("KOSDAQ", event.name());
        assertEquals(new BigDecimal("962.15"), event.value());
    }

    private static KisProperties properties() {
        return new KisProperties(
                "app-key",
                "app-secret",
                "https://example.com/oauth2/Approval",
                "ws://example.com",
                "H0STCNT0",
                "H0STASP0",
                "H0UPCNT0",
                "P"
        );
    }

    private static List<String> priceFields() {
        List<String> fields = IntStream.range(0, KisRealtimeParser.PRICE_FIELD_COUNT)
                .mapToObj(index -> "0")
                .collect(Collectors.toList());
        fields.set(0, "005930");
        fields.set(1, "123929");
        fields.set(2, "73100");
        fields.set(3, "5");
        fields.set(4, "500");
        fields.set(5, "0.68");
        fields.set(7, "71500");
        fields.set(8, "73500");
        fields.set(9, "71200");
        fields.set(13, "12345678");
        fields.set(14, "889012345678");
        fields.set(33, "20260527");
        return fields;
    }

    private static List<String> orderbookFields() {
        List<String> fields = IntStream.range(0, KisRealtimeParser.ORDERBOOK_FIELD_COUNT)
                .mapToObj(index -> "0")
                .collect(Collectors.toList());
        fields.set(0, "005930");
        fields.set(1, "123929");
        for (int i = 0; i < 10; i++) {
            fields.set(3 + i, String.valueOf(73200 + (i * 100)));
            fields.set(13 + i, String.valueOf(73100 - (i * 100)));
            fields.set(23 + i, String.valueOf(1000 + i));
            fields.set(33 + i, String.valueOf(2000 + i));
        }
        return fields;
    }

    private static List<String> indexFields() {
        List<String> fields = IntStream.range(0, KisRealtimeParser.INDEX_FIELD_COUNT)
                .mapToObj(index -> "0")
                .collect(Collectors.toList());
        fields.set(0, "0001");
        fields.set(1, "101530");
        fields.set(2, "2847.15");
        fields.set(3, "2");
        fields.set(4, "15.42");
        fields.set(5, "123456");
        fields.set(9, "0.54");
        return fields;
    }
}
