package com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketRealtimeStatusEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteEventHandler;
import com.mock.maesoongan.realtimequoteingestor.quote.port.QuoteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "quote.source", name = "mode", havingValue = "kis")
public class KisQuoteSource implements QuoteSource {

    private static final Logger log = LoggerFactory.getLogger(KisQuoteSource.class);

    private final KisProperties properties;
    private final KisApprovalKeyClient approvalKeyClient;
    private final KisRealtimeParser parser;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final HttpClient httpClient;
    private final ScheduledExecutorService reconnectExecutor;
    // MAX SUBSCRIBE OVER 시 KIS 재연결로 등록 초기화 (잦은 재연결 루프 방지 throttle)
    private static final long SUBSCRIBE_OVER_RECONNECT_INTERVAL_MS = 30_000;
    // 클라이언트 요청(페이지 이동) 리셋: 빠른 연속 이동을 합치기 위한 짧은 throttle
    private static final long RESET_MIN_INTERVAL_MS = 1_500;
    private volatile long lastSubscribeOverReconnectAt;
    private volatile long lastResetAt;
    private final AtomicBoolean recoveringFromSubscribeOver = new AtomicBoolean(false);
    private final Set<String> subscribedPriceCodes = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedOrderbookCodes = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedIndexes = ConcurrentHashMap.newKeySet();
    // 해제 디바운스: 즉시 KIS 해제하지 않고 잠시 예약해, 새로고침 등으로 곧 재구독되면 취소(churn/MAX OVER 방지)
    private static final long UNSUBSCRIBE_DELAY_SECONDS = 5;
    private final Map<String, ScheduledFuture<?>> pendingUnsubscribe = new ConcurrentHashMap<>();
    private final Map<String, KisRealtimeParser.EncryptionContext> encryptionContexts = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger();
    private final AtomicInteger orderbookParseWarningCount = new AtomicInteger();
    private final AtomicInteger indexParseWarningCount = new AtomicInteger();

    private volatile WebSocket webSocket;
    private volatile QuoteEventHandler handler;
    private volatile String approvalKey;

    public KisQuoteSource(
            KisProperties properties,
            KisApprovalKeyClient approvalKeyClient,
            KisRealtimeParser parser,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.properties = properties;
        this.approvalKeyClient = approvalKeyClient;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
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
    public void subscribePrices(List<String> stockCodes) {
        stockCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .forEach(code -> {
                    boolean hadPending = cancelPendingUnsubscribe("P:" + code);
                    boolean isNew = subscribedPriceCodes.add(code);
                    // 예약 해제를 취소한 경우 이미 KIS에 등록돼 있으므로 재전송 불필요
                    if (isNew && !hadPending && connected.get()) {
                        sendSubscription(properties.priceTrId(), code, "1");
                    }
                });
    }

    @Override
    public void unsubscribePrices(List<String> stockCodes) {
        stockCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .filter(subscribedPriceCodes::contains)
                .forEach(code -> scheduleUnsubscribe("P:" + code, () -> {
                    if (subscribedPriceCodes.remove(code) && connected.get()) {
                        sendSubscription(properties.priceTrId(), code, "2");
                    }
                }));
    }

    @Override
    public void subscribeOrderbooks(List<String> stockCodes) {
        stockCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .forEach(code -> {
                    boolean hadPending = cancelPendingUnsubscribe("O:" + code);
                    boolean isNew = subscribedOrderbookCodes.add(code);
                    if (isNew && !hadPending && connected.get()) {
                        sendSubscription(properties.orderbookTrId(), code, "1");
                    }
                });
    }

    @Override
    public void unsubscribeOrderbooks(List<String> stockCodes) {
        stockCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .distinct()
                .filter(subscribedOrderbookCodes::contains)
                .forEach(code -> scheduleUnsubscribe("O:" + code, () -> {
                    if (subscribedOrderbookCodes.remove(code) && connected.get()) {
                        sendSubscription(properties.orderbookTrId(), code, "2");
                    }
                }));
    }

    @Override
    public void subscribeIndexes(List<String> markets) {
        markets.stream()
                .map(this::normalizeMarket)
                .filter(market -> !market.isBlank())
                .distinct()
                .forEach(market -> {
                    boolean hadPending = cancelPendingUnsubscribe("I:" + market);
                    boolean isNew = subscribedIndexes.add(market);
                    if (isNew && !hadPending && connected.get()) {
                        sendIndexSubscription(market, "1");
                    }
                });
    }

    @Override
    public void unsubscribeIndexes(List<String> markets) {
        markets.stream()
                .map(this::normalizeMarket)
                .filter(market -> !market.isBlank())
                .distinct()
                .filter(subscribedIndexes::contains)
                .forEach(market -> scheduleUnsubscribe("I:" + market, () -> {
                    if (subscribedIndexes.remove(market) && connected.get()) {
                        sendIndexSubscription(market, "2");
                    }
                }));
    }

    // 해제를 UNSUBSCRIBE_DELAY_SECONDS 만큼 지연 예약. 같은 키가 그 사이 재구독되면 cancelPendingUnsubscribe로 취소됨.
    private void scheduleUnsubscribe(String key, Runnable unsubscribeAction) {
        pendingUnsubscribe.compute(key, (ignored, existing) -> {
            if (existing != null) {
                return existing; // 이미 예약돼 있으면 유지
            }
            return reconnectExecutor.schedule(() -> {
                pendingUnsubscribe.remove(key);
                unsubscribeAction.run();
            }, UNSUBSCRIBE_DELAY_SECONDS, TimeUnit.SECONDS);
        });
    }

    // 예약된 해제가 있으면 취소(= 계속 구독 유지). 취소했으면 true.
    private boolean cancelPendingUnsubscribe(String key) {
        ScheduledFuture<?> pending = pendingUnsubscribe.remove(key);
        if (pending != null) {
            pending.cancel(false);
            return true;
        }
        return false;
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
        subscribedPriceCodes.forEach(code -> sendSubscription(properties.priceTrId(), code, "1"));
        subscribedOrderbookCodes.forEach(code -> sendSubscription(properties.orderbookTrId(), code, "1"));
        subscribedIndexes.forEach(market -> sendIndexSubscription(market, "1"));
        // 한도 초과로 재연결한 경우 → 복구 완료 알림
        if (recoveringFromSubscribeOver.compareAndSet(true, false)) {
            applicationEventPublisher.publishEvent(new MarketRealtimeStatusEvent(false));
        }
    }

    // KIS 등록 한도 초과 시: 현재 연결을 끊고 재연결.
    // → connect()가 새 approval_key 발급(한도 초기화), onConnected()가 현재 ref-count 구독만 재등록.
    //   누적된 유령 등록이 사라짐. throttle(30초)로 잦은 재연결 루프를 방지한다.
    private void reconnectForSubscribeOver() {
        long now = System.currentTimeMillis();
        if (now - lastSubscribeOverReconnectAt < SUBSCRIBE_OVER_RECONNECT_INTERVAL_MS) {
            return;
        }
        lastSubscribeOverReconnectAt = now;
        log.warn("KIS MAX SUBSCRIBE OVER 감지 → KIS 재연결로 등록 초기화");
        triggerReconnect();
    }

    // 클라이언트 요청(페이지 이동)에 의한 연결 리셋. 빠른 연속 이동은 짧은 throttle로 합친다.
    @Override
    public void reset() {
        long now = System.currentTimeMillis();
        if (now - lastResetAt < RESET_MIN_INTERVAL_MS) {
            return;
        }
        lastResetAt = now;
        log.info("KIS 연결 리셋 요청 → 추적 목록 초기화 + 재연결");
        // pod 메모리의 누적 추적 목록까지 비운다(= pod 재시작 효과). 재연결 후엔
        // 핸들러가 현재 활성 구독만 다시 등록하므로 onConnected가 그 깨끗한 목록만 등록한다.
        clearSubscriptions();
        triggerReconnect();
    }

    private void clearSubscriptions() {
        pendingUnsubscribe.values().forEach(future -> future.cancel(false));
        pendingUnsubscribe.clear();
        subscribedPriceCodes.clear();
        subscribedOrderbookCodes.clear();
        subscribedIndexes.clear();
    }

    // KIS 연결을 끊고 재연결. connect()가 새 approval_key 발급(한도 초기화),
    // onConnected()가 현재 ref-count 구독만 재등록. 복구 상태 이벤트도 발행.
    private void triggerReconnect() {
        recoveringFromSubscribeOver.set(true);
        applicationEventPublisher.publishEvent(new MarketRealtimeStatusEvent(true));
        connected.set(false);
        WebSocket socket = webSocket;
        if (socket != null) {
            socket.abort();
        }
        scheduleReconnect(); // abort 시 onClose가 호출되지 않을 수 있어 명시적으로 재연결 예약
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
            log.info("Sent KIS realtime subscription. trId={}, trKey={}, trType={}", trId, stockCode, trType);
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
        if (properties.orderbookTrId().equals(parts[1]) && parsed.orderbookEvents().isEmpty()) {
            int warningCount = orderbookParseWarningCount.incrementAndGet();
            if (warningCount <= 5) {
                log.warn(
                        "No KIS orderbook events parsed. trId={}, dataCount={}, fieldCount={}, encryptionFlag={}",
                        parts[1],
                        parts[2],
                        parser.fieldCount(parts[3]),
                        parts[0]
                );
            }
        }
        if (properties.indexTrId().equals(parts[1]) && parsed.indexEvents().isEmpty()) {
            int warningCount = indexParseWarningCount.incrementAndGet();
            if (warningCount <= 5) {
                log.warn(
                        "No KIS index events parsed. trId={}, dataCount={}, fieldCount={}, encryptionFlag={}",
                        parts[1],
                        parts[2],
                        parser.fieldCount(parts[3]),
                        parts[0]
                );
            }
        }
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
            case "KOSDAQ" -> "1001";
            default -> market;
        };
    }

    private void handleJsonMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String trId = firstNonBlank(
                    root.path("header").path("tr_id").asText(),
                    root.path("body").path("input").path("tr_id").asText(),
                    root.path("body").path("output").path("tr_id").asText()
            );
            String trKey = firstNonBlank(
                    root.path("body").path("input").path("tr_key").asText(),
                    root.path("body").path("output").path("tr_key").asText()
            );
            String rtCd = firstNonBlank(root.path("body").path("rt_cd").asText(), root.path("header").path("rt_cd").asText());
            String msg1 = firstNonBlank(root.path("body").path("msg1").asText(), root.path("header").path("msg1").asText());
            JsonNode output = root.path("body").path("output");
            String iv = output.path("iv").asText();
            String key = output.path("key").asText();
            if (!rtCd.isBlank() || !msg1.isBlank()) {
                log.info("KIS realtime control message. trId={}, trKey={}, rtCd={}, msg={}", trId, trKey, rtCd, msg1);
            }
            if (msg1 != null && msg1.toUpperCase(Locale.ROOT).contains("MAX SUBSCRIBE OVER")) {
                reconnectForSubscribeOver();
            }
            if (!trId.isBlank() && !iv.isBlank() && !key.isBlank()) {
                encryptionContexts.put(trId, new KisRealtimeParser.EncryptionContext(iv, key));
            } else if (!iv.isBlank() || !key.isBlank()) {
                log.warn("KIS realtime encryption context is incomplete. trId={}, hasIv={}, hasKey={}", trId, !iv.isBlank(), !key.isBlank());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse KIS JSON message", exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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
                try {
                    handleText(partialText.toString());
                } catch (RuntimeException exception) {
                    log.warn("Failed to handle KIS realtime message", exception);
                }
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
