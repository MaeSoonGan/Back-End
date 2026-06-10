package com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis;

import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookLevel;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.IndexQuoteEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Component
public class KisRealtimeParser {

    static final int PRICE_FIELD_COUNT = 46;
    static final int ORDERBOOK_FIELD_COUNT = 59;
    static final int INDEX_FIELD_COUNT = 30;
    static final int INDEX_MIN_FIELD_COUNT = 10;

    private final KisProperties properties;

    public KisRealtimeParser(KisProperties properties) {
        this.properties = properties;
    }

    public List<PriceQuoteEvent> parsePriceEvents(String payload) {
        List<String[]> records = splitRecords(payload, PRICE_FIELD_COUNT);
        List<PriceQuoteEvent> events = new ArrayList<>();
        for (String[] fields : records) {
            events.add(PriceQuoteEvent.of(
                    fields[0],
                    fields[0],
                    decimal(fields[2]),
                    signedChange(fields[3], fields[4]),
                    signedChange(fields[3], fields[5]),
                    longValue(fields[13]),
                    timestamp(fields[33], fields[1]),
                    LocalDateTime.now(),
                    System.nanoTime()
            ));
        }
        return events;
    }

    public List<OrderbookQuoteEvent> parseOrderbookEvents(String payload) {
        List<String[]> records = splitRecords(payload, ORDERBOOK_FIELD_COUNT);
        List<OrderbookQuoteEvent> events = new ArrayList<>();
        for (String[] fields : records) {
            List<OrderbookLevel> asks = new ArrayList<>();
            List<OrderbookLevel> bids = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                asks.add(new OrderbookLevel(decimal(fields[3 + i]), longValue(fields[23 + i])));
                bids.add(new OrderbookLevel(decimal(fields[13 + i]), longValue(fields[33 + i])));
            }
            events.add(OrderbookQuoteEvent.of(
                    fields[0],
                    fields[0],
                    asks,
                    bids,
                    timestamp(null, fields[1]),
                    LocalDateTime.now(),
                    System.nanoTime()
            ));
        }
        return events;
    }

    public List<IndexQuoteEvent> parseIndexEvents(String payload) {
        return parseIndexEvents(payload, 1);
    }

    public List<IndexQuoteEvent> parseIndexEvents(String payload, int recordCount) {
        List<String[]> records = splitIndexRecords(payload, recordCount);
        List<IndexQuoteEvent> events = new ArrayList<>();
        for (String[] fields : records) {
            events.add(IndexQuoteEvent.of(
                    fields[0],
                    indexName(fields[0]),
                    decimal(fields[2]),
                    signedChange(fields[3], fields[4]),
                    signedChange(fields[3], fields[9]),
                    longValue(fields[5]),
                    timestamp(null, fields[1]),
                    LocalDateTime.now(),
                    System.nanoTime()
            ));
        }
        return events;
    }

    public ParsedRealtimeMessage parse(String rawMessage, EncryptionContext encryptionContext) {
        String[] parts = rawMessage.split("\\|", 4);
        if (parts.length < 4) {
            return ParsedRealtimeMessage.empty();
        }

        String encryptionFlag = parts[0];
        String trId = parts[1];
        int recordCount = recordCount(parts[2]);
        String payload = parts[3];
        if ("1".equals(encryptionFlag)) {
            payload = decrypt(payload, encryptionContext);
        }

        if (properties.priceTrId().equals(trId)) {
            return new ParsedRealtimeMessage(parsePriceEvents(payload), List.of(), List.of());
        }
        if (properties.orderbookTrId().equals(trId)) {
            return new ParsedRealtimeMessage(List.of(), parseOrderbookEvents(payload), List.of());
        }
        if (properties.indexTrId().equals(trId)) {
            return new ParsedRealtimeMessage(List.of(), List.of(), parseIndexEvents(payload, recordCount));
        }
        return ParsedRealtimeMessage.empty();
    }

    private List<String[]> splitRecords(String payload, int fieldCount) {
        String[] fields = payload.split("\\^", -1);
        List<String[]> records = new ArrayList<>();
        for (int offset = 0; offset + fieldCount <= fields.length; offset += fieldCount) {
            String[] record = new String[fieldCount];
            System.arraycopy(fields, offset, record, 0, fieldCount);
            records.add(record);
        }
        return records;
    }

    int fieldCount(String payload) {
        return payload.split("\\^", -1).length;
    }

    private List<String[]> splitIndexRecords(String payload, int recordCount) {
        String[] fields = payload.split("\\^", -1);
        if (recordCount <= 0 || fields.length < INDEX_MIN_FIELD_COUNT) {
            return List.of();
        }

        int fieldCount = fields.length / recordCount;
        if (fieldCount < INDEX_MIN_FIELD_COUNT) {
            return List.of();
        }

        List<String[]> records = new ArrayList<>();
        for (int offset = 0; offset + fieldCount <= fields.length; offset += fieldCount) {
            String[] record = new String[fieldCount];
            System.arraycopy(fields, offset, record, 0, fieldCount);
            records.add(record);
        }
        return records;
    }

    private int recordCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private long longValue(String value) {
        return decimal(value).longValue();
    }

    private BigDecimal signedChange(String signCode, String value) {
        BigDecimal number = decimal(value);
        if ("4".equals(signCode) || "5".equals(signCode)) {
            return number.abs().negate();
        }
        return number;
    }

    private String indexName(String code) {
        return switch (code) {
            case "0001" -> "KOSPI";
            case "1001" -> "KOSDAQ";
            default -> code;
        };
    }

    private LocalDateTime timestamp(String date, String time) {
        LocalDate localDate = Optional.ofNullable(date)
                .filter(value -> value.length() == 8)
                .map(value -> LocalDate.of(
                        Integer.parseInt(value.substring(0, 4)),
                        Integer.parseInt(value.substring(4, 6)),
                        Integer.parseInt(value.substring(6, 8))
                ))
                .orElse(LocalDate.now());
        LocalTime localTime = Optional.ofNullable(time)
                .filter(value -> value.length() >= 6)
                .map(value -> LocalTime.of(
                        Integer.parseInt(value.substring(0, 2)),
                        Integer.parseInt(value.substring(2, 4)),
                        Integer.parseInt(value.substring(4, 6))
                ))
                .orElse(LocalTime.now());
        return LocalDateTime.of(localDate, localTime);
    }

    private String decrypt(String payload, EncryptionContext encryptionContext) {
        if (encryptionContext == null) {
            throw new IllegalStateException("Encrypted KIS realtime payload received before encryption context");
        }
        try {
            byte[] key = encryptionContext.key().getBytes();
            byte[] iv = encryptionContext.iv().getBytes();
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return new String(cipher.doFinal(Base64.getDecoder().decode(payload)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt KIS realtime payload", exception);
        }
    }

    public record ParsedRealtimeMessage(
            List<PriceQuoteEvent> priceEvents,
            List<OrderbookQuoteEvent> orderbookEvents,
            List<IndexQuoteEvent> indexEvents
    ) {

        static ParsedRealtimeMessage empty() {
            return new ParsedRealtimeMessage(List.of(), List.of(), List.of());
        }
    }

    public record EncryptionContext(String iv, String key) {
    }
}
