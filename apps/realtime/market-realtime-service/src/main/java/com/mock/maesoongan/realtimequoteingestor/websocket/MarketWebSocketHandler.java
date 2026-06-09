package com.mock.maesoongan.realtimequoteingestor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketStatusService;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusResponse;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketIndexUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketOrderbookUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketPriceUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.application.QuoteIngestionService;
import com.mock.maesoongan.realtimequoteingestor.websocket.dto.MarketWebSocketMessage;
import com.mock.maesoongan.realtimequoteingestor.websocket.dto.MarketWebSocketRequest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MarketWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final QuoteIngestionService quoteIngestionService;
    private final MarketStatusService marketStatusService;
    private final ConcurrentMap<WebSocketSession, Set<String>> priceSessionSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<WebSocketSession, Set<String>> orderbookSessionSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<WebSocketSession, Set<String>> indexSessionSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> stockSubscriberCounts = new ConcurrentHashMap<>();
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
        MarketWebSocketRequest request = objectMapper.readValue(message.getPayload(), MarketWebSocketRequest.class);
        if (request.action() == null) {
            return;
        }

        switch (request.action()) {
            case SUBSCRIBE, SUBSCRIBE_PRICE -> subscribePrice(session, normalizeStockCodes(request.stockCodes()));
            case UNSUBSCRIBE, UNSUBSCRIBE_PRICE -> unsubscribePrice(session, normalizeStockCodes(request.stockCodes()));
            case SUBSCRIBE_ORDERBOOK -> subscribeOrderbook(session, normalizeStockCodes(request.stockCodes()));
            case UNSUBSCRIBE_ORDERBOOK -> unsubscribeOrderbook(session, normalizeStockCodes(request.stockCodes()));
            case SUBSCRIBE_INDEX -> subscribeIndex(session, normalizeMarkets(request.markets()));
            case UNSUBSCRIBE_INDEX -> unsubscribeIndex(session, normalizeMarkets(request.markets()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<String> priceCodes = priceSessionSubscriptions.remove(session);
        Set<String> orderbookCodes = orderbookSessionSubscriptions.remove(session);
        Set<String> indexes = indexSessionSubscriptions.remove(session);
        decrementGlobalStockSubscriptions(toList(priceCodes));
        decrementGlobalStockSubscriptions(toList(orderbookCodes));
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

    private void subscribePrice(WebSocketSession session, List<String> stockCodes) {
        subscribeStockChannel(session, stockCodes, priceSessionSubscriptions);
    }

    private void unsubscribePrice(WebSocketSession session, List<String> stockCodes) {
        unsubscribeStockChannel(session, stockCodes, priceSessionSubscriptions);
    }

    private void subscribeOrderbook(WebSocketSession session, List<String> stockCodes) {
        subscribeStockChannel(session, stockCodes, orderbookSessionSubscriptions);
    }

    private void unsubscribeOrderbook(WebSocketSession session, List<String> stockCodes) {
        unsubscribeStockChannel(session, stockCodes, orderbookSessionSubscriptions);
    }

    private void subscribeStockChannel(
            WebSocketSession session,
            List<String> stockCodes,
            ConcurrentMap<WebSocketSession, Set<String>> sessionSubscriptions
    ) {
        if (stockCodes.isEmpty()) {
            return;
        }
        Set<String> subscriptions = sessionSubscriptions.computeIfAbsent(session, key -> ConcurrentHashMap.newKeySet());
        List<String> newlySubscribedCodes = stockCodes.stream()
                .filter(subscriptions::add)
                .filter(this::incrementGlobalStockSubscription)
                .toList();

        if (!newlySubscribedCodes.isEmpty()) {
            quoteIngestionService.subscribe(newlySubscribedCodes);
        }
    }

    private void unsubscribeStockChannel(
            WebSocketSession session,
            List<String> stockCodes,
            ConcurrentMap<WebSocketSession, Set<String>> sessionSubscriptions
    ) {
        Set<String> subscriptions = sessionSubscriptions.get(session);
        if (subscriptions == null) {
            return;
        }

        List<String> removedCodes = stockCodes.stream()
                .filter(subscriptions::remove)
                .toList();
        decrementGlobalStockSubscriptions(removedCodes);
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

    private boolean incrementGlobalStockSubscription(String stockCode) {
        AtomicInteger count = stockSubscriberCounts.computeIfAbsent(stockCode, key -> new AtomicInteger());
        return count.incrementAndGet() == 1;
    }

    private void decrementGlobalStockSubscriptions(List<String> stockCodes) {
        List<String> globallyUnsubscribedCodes = stockCodes.stream()
                .filter(this::decrementGlobalStockSubscription)
                .toList();
        if (!globallyUnsubscribedCodes.isEmpty()) {
            quoteIngestionService.unsubscribe(globallyUnsubscribedCodes);
        }
    }

    private boolean decrementGlobalStockSubscription(String stockCode) {
        AtomicInteger count = stockSubscriberCounts.get(stockCode);
        if (count == null) {
            return false;
        }
        int remaining = count.decrementAndGet();
        if (remaining <= 0) {
            stockSubscriberCounts.remove(stockCode);
            return true;
        }
        return false;
    }

    private boolean incrementGlobalIndexSubscription(String market) {
        AtomicInteger count = indexSubscriberCounts.computeIfAbsent(market, key -> new AtomicInteger());
        return count.incrementAndGet() == 1;
    }

    private void decrementGlobalIndexSubscriptions(List<String> markets) {
        List<String> globallyUnsubscribedMarkets = markets.stream()
                .filter(this::decrementGlobalIndexSubscription)
                .toList();
        if (!globallyUnsubscribedMarkets.isEmpty()) {
            quoteIngestionService.unsubscribeIndexes(globallyUnsubscribedMarkets);
        }
    }

    private boolean decrementGlobalIndexSubscription(String market) {
        AtomicInteger count = indexSubscriberCounts.get(market);
        if (count == null) {
            return false;
        }
        int remaining = count.decrementAndGet();
        if (remaining <= 0) {
            indexSubscriberCounts.remove(market);
            return true;
        }
        return false;
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
}
