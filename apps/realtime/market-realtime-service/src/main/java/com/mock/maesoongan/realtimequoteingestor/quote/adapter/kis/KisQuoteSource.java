package com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventHandler;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "quote.source", name = "mode", havingValue = "kis")
public class KisQuoteSource implements QuoteSource {

    private final KisProperties properties;
    private final KisApprovalKeyClient approvalKeyClient;
    private final KisRealtimeParser parser;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ScheduledExecutorService reconnectExecutor;
    private final Set<String> subscribedCodes = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedIndexes = ConcurrentHashMap.newKeySet();
    private final Map<String, KisRealtimeParser.EncryptionContext> encryptionContexts = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger();

    private volatile WebSocket webSocket;
    private volatile QuoteEventHandler handler;
    private volatile String approvalKey;

    public KisQuoteSource(
            KisProperties properties,
            KisApprovalKeyClient approvalKeyClient,
            KisRealtimeParser parser,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.approvalKeyClient = approvalKeyClient;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "kis-reconnect");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start(QuoteEventHandler handler) {
        this.handler = handler;
        running.set(true);
        connect();
    }

    @Override
    public void stop() {
        running.set(false);
        connected.set(false);
        WebSocket socket = webSocket;
        if (socket != null) {
            socket.abort();
        }
    }

    @Override
    public void subscribe(List<String> stockCodes) {
        List<String> newCodes = stockCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .filter(subscribedCodes::add)
                .toList();
        if (connected.get()) {
            newCodes.forEach(code -> sendSubscriptions(code, "1"));
        }
    }

    @Override
    public void unsubscribe(List<String> stockCodes) {
        List<String> removedCodes = stockCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .filter(subscribedCodes::remove)
                .toList();
        if (connected.get()) {
            removedCodes.forEach(code -> sendSubscriptions(code, "2"));
        }
    }

    @Override
    public void subscribeIndexes(List<String> markets) {
        List<String> newIndexes = markets.stream()
                .map(this::normalizeMarket)
                .filter(market -> !market.isBlank())
                .distinct()
                .filter(subscribedIndexes::add)
                .toList();
        if (connected.get()) {
            newIndexes.forEach(market -> sendIndexSubscription(market, "1"));
        }
    }

    @Override
    public void unsubscribeIndexes(List<String> markets) {
        List<String> removedIndexes = markets.stream()
                .map(this::normalizeMarket)
                .filter(market -> !market.isBlank())
                .distinct()
                .filter(subscribedIndexes::remove)
                .toList();
        if (connected.get()) {
            removedIndexes.forEach(market -> sendIndexSubscription(market, "2"));
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    private synchronized void connect() {
        if (!running.get()) {
            return;
        }

        approvalKey = approvalKeyClient.issueApprovalKey();
        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(properties.websocketUrl()), new Listener())
                    .join();
        } catch (RuntimeException exception) {
            connected.set(false);
            scheduleReconnect();
            throw exception;
        }
    }

    private void onConnected() {
        connected.set(true);
        reconnectAttempt.set(0);
        subscribedCodes.forEach(code -> sendSubscriptions(code, "1"));
        subscribedIndexes.forEach(market -> sendIndexSubscription(market, "1"));
    }

    private void sendSubscriptions(String stockCode, String trType) {
        sendSubscription(properties.priceTrId(), stockCode, trType);
        sendSubscription(properties.orderbookTrId(), stockCode, trType);
    }

    private void sendSubscription(String trId, String stockCode, String trType) {
        WebSocket socket = webSocket;
        if (socket == null || !connected.get()) {
            return;
        }

        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "header", Map.of(
                            "approval_key", approvalKey,
                            "custtype", properties.customerType(),
                            "tr_type", trType,
                            "content-type", "utf-8"
                    ),
                    "body", Map.of(
                            "input", Map.of(
                                    "tr_id", trId,
                                    "tr_key", stockCode
                            )
                    )
            ));
            socket.sendText(message, true);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build KIS subscription message", exception);
        }
    }

    private void sendIndexSubscription(String market, String trType) {
        sendSubscription(properties.indexTrId(), indexCode(market), trType);
    }

    private void handleText(String message) {
        if (message.contains("PINGPONG")) {
            WebSocket socket = webSocket;
            if (socket != null) {
                socket.sendText(message, true);
            }
            return;
        }
        if (message.startsWith("{")) {
            handleJsonMessage(message);
            return;
        }

        String[] parts = message.split("\\|", 4);
        if (parts.length < 4) {
            return;
        }
        KisRealtimeParser.EncryptionContext encryptionContext = encryptionContexts.get(parts[1]);
        KisRealtimeParser.ParsedRealtimeMessage parsed = parser.parse(message, encryptionContext);
        parsed.priceEvents().forEach(handler::handlePrice);
        parsed.orderbookEvents().forEach(handler::handleOrderbook);
        parsed.indexEvents().forEach(handler::handleIndex);
    }

    private String normalizeMarket(String market) {
        if (market == null || market.isBlank()) {
            return "";
        }
        return market.trim().toUpperCase(Locale.ROOT);
    }

    private String indexCode(String market) {
        return switch (market) {
            case "KOSPI" -> "0001";
            case "KOSDAQ" -> "0002";
            default -> market;
        };
    }

    private void handleJsonMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String trId = root.path("header").path("tr_id").asText();
            JsonNode output = root.path("body").path("output");
            String iv = output.path("iv").asText();
            String key = output.path("key").asText();
            if (!trId.isBlank() && !iv.isBlank() && !key.isBlank()) {
                encryptionContexts.put(trId, new KisRealtimeParser.EncryptionContext(iv, key));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse KIS JSON message", exception);
        }
    }

    private void scheduleReconnect() {
        if (!running.get()) {
            return;
        }
        int attempt = reconnectAttempt.incrementAndGet();
        long delaySeconds = Math.min(60, (long) Math.pow(2, Math.min(attempt, 6)));
        reconnectExecutor.schedule(() -> {
            if (!running.get() || connected.get()) {
                return;
            }
            try {
                connect();
            } catch (RuntimeException ignored) {
                scheduleReconnect();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private class Listener implements WebSocket.Listener {

        private final StringBuilder partialText = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            KisQuoteSource.this.webSocket = webSocket;
            onConnected();
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialText.append(data);
            if (last) {
                handleText(partialText.toString());
                partialText.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected.set(false);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected.set(false);
            scheduleReconnect();
        }
    }
}
