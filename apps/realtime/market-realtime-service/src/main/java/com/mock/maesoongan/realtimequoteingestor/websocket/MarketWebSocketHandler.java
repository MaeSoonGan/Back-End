package com.mock.maesoongan.realtimequoteingestor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketStatusService;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusResponse;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketIndexUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketOrderbookUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketPriceUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketRealtimeStatusEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.application.QuoteIngestionService;
import com.mock.maesoongan.realtimequoteingestor.websocket.dto.MarketWebSocketMessage;
import com.mock.maesoongan.realtimequoteingestor.websocket.dto.MarketWebSocketRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MarketWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final QuoteIngestionService quoteIngestionService;
    private final MarketStatusService marketStatusService;
    private final ConcurrentMap<WebSocketSession, Set<String>> priceSessionSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<WebSocketSession, Set<String>> orderbookSessionSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<WebSocketSession, Set<String>> indexSessionSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> priceSubscriberCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> orderbookSubscriberCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> indexSubscriberCounts = new ConcurrentHashMap<>();

    public MarketWebSocketHandler(
            ObjectMapper objectMapper,
            QuoteIngestionService quoteIngestionService,
            MarketStatusService marketStatusService
    ) {
        this.objectMapper = objectMapper;
        this.quoteIngestionService = quoteIngestionService;
        this.marketStatusService = marketStatusService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        priceSessionSubscriptions.put(session, ConcurrentHashMap.newKeySet());
        orderbookSessionSubscriptions.put(session, ConcurrentHashMap.newKeySet());
        indexSessionSubscriptions.put(session, ConcurrentHashMap.newKeySet());
        send(session, MarketWebSocketMessage.marketStatus(marketStatusService.currentStatus()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        MarketWebSocketRequest request;
        try {
            request = objectMapper.readValue(message.getPayload(), MarketWebSocketRequest.class);
        } catch (IOException exception) {
            log.warn("Invalid market websocket request. sessionId={}, payload={}", session.getId(), message.getPayload());
            send(session, MarketWebSocketMessage.error(Map.of(
                    "message", "Invalid websocket request",
                    "reason", "Invalid JSON request or unsupported action."
            )));
            return;
        }

        if (request.action() == null) {
            send(session, MarketWebSocketMessage.error(Map.of(
                    "message", "Missing action",
                    "reason", "action is required."
            )));
            return;
        }

        List<String> stockCodes = normalizeStockCodes(request.stockCodes());
        List<String> markets = normalizeMarkets(request.markets());
        log.info(
                "Market websocket request. sessionId={}, action={}, stockCodes={}, markets={}",
                session.getId(),
                request.action(),
                stockCodes,
                markets
        );

        switch (request.action()) {
            case SUBSCRIBE, SUBSCRIBE_PRICE -> subscribePrice(session, stockCodes);
            case UNSUBSCRIBE, UNSUBSCRIBE_PRICE -> unsubscribePrice(session, stockCodes);
            case SUBSCRIBE_ORDERBOOK -> subscribeOrderbook(session, stockCodes);
            case UNSUBSCRIBE_ORDERBOOK -> unsubscribeOrderbook(session, stockCodes);
            case SUBSCRIBE_INDEX -> subscribeIndex(session, markets);
            case UNSUBSCRIBE_INDEX -> unsubscribeIndex(session, markets);
        }
        send(session, MarketWebSocketMessage.subscriptionAck(subscriptionAck(request, stockCodes, markets)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<String> priceCodes = priceSessionSubscriptions.remove(session);
        Set<String> orderbookCodes = orderbookSessionSubscriptions.remove(session);
        Set<String> indexes = indexSessionSubscriptions.remove(session);
        decrementGlobalPriceSubscriptions(toList(priceCodes));
        decrementGlobalOrderbookSubscriptions(toList(orderbookCodes));
        decrementGlobalIndexSubscriptions(toList(indexes));
    }

    @EventListener
    public void handleMarketPriceUpdated(MarketPriceUpdatedEvent event) throws IOException {
        String stockCode = event.price().stockCode();
        TextMessage message = new TextMessage(objectMapper.writeValueAsString(
                MarketWebSocketMessage.priceUpdate(event.price())
        ));

        for (var entry : priceSessionSubscriptions.entrySet()) {
            WebSocketSession session = entry.getKey();
            if (session.isOpen() && entry.getValue().contains(stockCode)) {
                send(session, message);
            }
        }
    }

    @EventListener
    public void handleMarketOrderbookUpdated(MarketOrderbookUpdatedEvent event) throws IOException {
        String stockCode = event.orderbook().stockCode();
        TextMessage message = new TextMessage(objectMapper.writeValueAsString(
                MarketWebSocketMessage.orderbookUpdate(event.orderbook())
        ));

        for (var entry : orderbookSessionSubscriptions.entrySet()) {
            WebSocketSession session = entry.getKey();
            if (session.isOpen() && entry.getValue().contains(stockCode)) {
                send(session, message);
            }
        }
    }

    @EventListener
    public void handleMarketIndexUpdated(MarketIndexUpdatedEvent event) throws IOException {
        String market = event.index().market();
        TextMessage message = new TextMessage(objectMapper.writeValueAsString(
                MarketWebSocketMessage.indexUpdate(event.index())
        ));

        for (var entry : indexSessionSubscriptions.entrySet()) {
            WebSocketSession session = entry.getKey();
            if (session.isOpen() && entry.getValue().contains(market)) {
                send(session, message);
            }
        }
    }

    // 실시간 수신 복구 시작/완료를 모든 세션에 알림 (FE 복구 모달용)
    @EventListener
    public void handleRealtimeStatus(MarketRealtimeStatusEvent event) {
        TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(
                    MarketWebSocketMessage.realtimeStatus(Map.of("recovering", event.recovering()))
            ));
        } catch (IOException exception) {
            log.warn("Failed to serialize realtime status message", exception);
            return;
        }
        for (WebSocketSession session : priceSessionSubscriptions.keySet()) {
            try {
                send(session, message);
            } catch (IOException exception) {
                log.warn("Failed to send realtime status. sessionId={}", session.getId());
            }
        }
    }

    private void subscribePrice(WebSocketSession session, List<String> stockCodes) {
        List<String> newlySubscribedCodes = subscribeStockChannel(session, stockCodes, priceSessionSubscriptions, priceSubscriberCounts);
        if (!newlySubscribedCodes.isEmpty()) {
            quoteIngestionService.subscribePrices(newlySubscribedCodes);
        }
    }

    private void unsubscribePrice(WebSocketSession session, List<String> stockCodes) {
        List<String> removedCodes = unsubscribeStockChannel(session, stockCodes, priceSessionSubscriptions);
        decrementGlobalPriceSubscriptions(removedCodes);
    }

    private void subscribeOrderbook(WebSocketSession session, List<String> stockCodes) {
        List<String> newlySubscribedCodes = subscribeStockChannel(session, stockCodes, orderbookSessionSubscriptions, orderbookSubscriberCounts);
        if (!newlySubscribedCodes.isEmpty()) {
            quoteIngestionService.subscribeOrderbooks(newlySubscribedCodes);
        }
    }

    private void unsubscribeOrderbook(WebSocketSession session, List<String> stockCodes) {
        List<String> removedCodes = unsubscribeStockChannel(session, stockCodes, orderbookSessionSubscriptions);
        decrementGlobalOrderbookSubscriptions(removedCodes);
    }

    private List<String> subscribeStockChannel(
            WebSocketSession session,
            List<String> stockCodes,
            ConcurrentMap<WebSocketSession, Set<String>> sessionSubscriptions,
            ConcurrentMap<String, AtomicInteger> subscriberCounts
    ) {
        if (stockCodes.isEmpty()) {
            return List.of();
        }
        Set<String> subscriptions = sessionSubscriptions.computeIfAbsent(session, key -> ConcurrentHashMap.newKeySet());
        return stockCodes.stream()
                .filter(subscriptions::add)
                .filter(stockCode -> incrementGlobalSubscription(stockCode, subscriberCounts))
                .toList();
    }

    private List<String> unsubscribeStockChannel(
            WebSocketSession session,
            List<String> stockCodes,
            ConcurrentMap<WebSocketSession, Set<String>> sessionSubscriptions
    ) {
        Set<String> subscriptions = sessionSubscriptions.get(session);
        if (subscriptions == null) {
            return List.of();
        }

        return stockCodes.stream()
                .filter(subscriptions::remove)
                .toList();
    }

    private void subscribeIndex(WebSocketSession session, List<String> markets) {
        if (markets.isEmpty()) {
            return;
        }
        Set<String> subscriptions = indexSessionSubscriptions.computeIfAbsent(session, key -> ConcurrentHashMap.newKeySet());
        List<String> newlySubscribedMarkets = markets.stream()
                .filter(subscriptions::add)
                .filter(this::incrementGlobalIndexSubscription)
                .toList();

        if (!newlySubscribedMarkets.isEmpty()) {
            quoteIngestionService.subscribeIndexes(newlySubscribedMarkets);
        }
    }

    private void unsubscribeIndex(WebSocketSession session, List<String> markets) {
        Set<String> subscriptions = indexSessionSubscriptions.get(session);
        if (subscriptions == null) {
            return;
        }

        List<String> removedMarkets = markets.stream()
                .filter(subscriptions::remove)
                .toList();
        decrementGlobalIndexSubscriptions(removedMarkets);
    }

    private void decrementGlobalPriceSubscriptions(List<String> stockCodes) {
        List<String> globallyUnsubscribedCodes = stockCodes.stream()
                .filter(stockCode -> decrementGlobalSubscription(stockCode, priceSubscriberCounts))
                .toList();
        if (!globallyUnsubscribedCodes.isEmpty()) {
            quoteIngestionService.unsubscribePrices(globallyUnsubscribedCodes);
        }
    }

    private void decrementGlobalOrderbookSubscriptions(List<String> stockCodes) {
        List<String> globallyUnsubscribedCodes = stockCodes.stream()
                .filter(stockCode -> decrementGlobalSubscription(stockCode, orderbookSubscriberCounts))
                .toList();
        if (!globallyUnsubscribedCodes.isEmpty()) {
            quoteIngestionService.unsubscribeOrderbooks(globallyUnsubscribedCodes);
        }
    }

    private boolean incrementGlobalSubscription(String key, ConcurrentMap<String, AtomicInteger> subscriberCounts) {
        AtomicInteger count = subscriberCounts.computeIfAbsent(key, ignored -> new AtomicInteger());
        return count.incrementAndGet() == 1;
    }

    private boolean decrementGlobalSubscription(String key, ConcurrentMap<String, AtomicInteger> subscriberCounts) {
        AtomicInteger count = subscriberCounts.get(key);
        if (count == null) {
            return false;
        }
        int remaining = count.decrementAndGet();
        if (remaining <= 0) {
            subscriberCounts.remove(key);
            return true;
        }
        return false;
    }

    private boolean incrementGlobalIndexSubscription(String market) {
        return incrementGlobalSubscription(market, indexSubscriberCounts);
    }

    private void decrementGlobalIndexSubscriptions(List<String> markets) {
        List<String> globallyUnsubscribedMarkets = markets.stream()
                .filter(market -> decrementGlobalSubscription(market, indexSubscriberCounts))
                .toList();
        if (!globallyUnsubscribedMarkets.isEmpty()) {
            quoteIngestionService.unsubscribeIndexes(globallyUnsubscribedMarkets);
        }
    }

    private List<String> normalizeStockCodes(List<String> stockCodes) {
        if (stockCodes == null) {
            return List.of();
        }
        return stockCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> normalizeMarkets(List<String> markets) {
        if (markets == null) {
            return List.of();
        }
        return markets.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> toList(Set<String> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private void send(WebSocketSession session, MarketWebSocketMessage<?> message) throws IOException {
        send(session, new TextMessage(objectMapper.writeValueAsString(message)));
    }

    private void send(WebSocketSession session, TextMessage message) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        }
    }

    private Map<String, Object> subscriptionAck(
            MarketWebSocketRequest request,
            List<String> stockCodes,
            List<String> markets
    ) {
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("action", request.action());
        ack.put("stockCodes", stockCodes);
        ack.put("markets", markets);
        return ack;
    }
}
