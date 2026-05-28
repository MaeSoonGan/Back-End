package com.mock.maesoongan.realtimequoteingestor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketStatusService;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusResponse;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MarketWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final QuoteIngestionService quoteIngestionService;
    private final MarketStatusService marketStatusService;
    private final ConcurrentMap<WebSocketSession, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> stockSubscriberCounts = new ConcurrentHashMap<>();

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
        sessionSubscriptions.put(session, ConcurrentHashMap.newKeySet());
        send(session, MarketWebSocketMessage.marketStatus(marketStatusService.currentStatus()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        MarketWebSocketRequest request = objectMapper.readValue(message.getPayload(), MarketWebSocketRequest.class);
        List<String> stockCodes = normalize(request.stockCodes());

        if (request.action() == null || stockCodes.isEmpty()) {
            return;
        }

        switch (request.action()) {
            case SUBSCRIBE -> subscribe(session, stockCodes);
            case UNSUBSCRIBE -> unsubscribe(session, stockCodes);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<String> subscribedCodes = sessionSubscriptions.remove(session);
        if (subscribedCodes == null || subscribedCodes.isEmpty()) {
            return;
        }
        decrementGlobalSubscriptions(List.copyOf(subscribedCodes));
    }

    @EventListener
    public void handleMarketPriceUpdated(MarketPriceUpdatedEvent event) throws IOException {
        String stockCode = event.price().stockCode();
        TextMessage message = new TextMessage(objectMapper.writeValueAsString(
                MarketWebSocketMessage.priceUpdate(event.price())
        ));

        for (var entry : sessionSubscriptions.entrySet()) {
            WebSocketSession session = entry.getKey();
            if (session.isOpen() && entry.getValue().contains(stockCode)) {
                send(session, message);
            }
        }
    }

    private void subscribe(WebSocketSession session, List<String> stockCodes) {
        Set<String> subscriptions = sessionSubscriptions.computeIfAbsent(session, key -> ConcurrentHashMap.newKeySet());
        List<String> newlySubscribedCodes = stockCodes.stream()
                .filter(subscriptions::add)
                .filter(this::incrementGlobalSubscription)
                .toList();

        if (!newlySubscribedCodes.isEmpty()) {
            quoteIngestionService.subscribe(newlySubscribedCodes);
        }
    }

    private void unsubscribe(WebSocketSession session, List<String> stockCodes) {
        Set<String> subscriptions = sessionSubscriptions.get(session);
        if (subscriptions == null) {
            return;
        }

        List<String> removedCodes = stockCodes.stream()
                .filter(subscriptions::remove)
                .toList();
        decrementGlobalSubscriptions(removedCodes);
    }

    private boolean incrementGlobalSubscription(String stockCode) {
        AtomicInteger count = stockSubscriberCounts.computeIfAbsent(stockCode, key -> new AtomicInteger());
        return count.incrementAndGet() == 1;
    }

    private void decrementGlobalSubscriptions(List<String> stockCodes) {
        List<String> globallyUnsubscribedCodes = stockCodes.stream()
                .filter(this::decrementGlobalSubscription)
                .toList();
        if (!globallyUnsubscribedCodes.isEmpty()) {
            quoteIngestionService.unsubscribe(globallyUnsubscribedCodes);
        }
    }

    private boolean decrementGlobalSubscription(String stockCode) {
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

    private List<String> normalize(List<String> stockCodes) {
        if (stockCodes == null) {
            return List.of();
        }
        return stockCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
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
