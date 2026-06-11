package com.mock.maesoongan.realtimequoteingestor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock.maesoongan.realtimequoteingestor.market.application.MarketStatusService;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketIndexResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketOrderbookResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusResponse;
import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketStatusType;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketIndexUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketOrderbookUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.market.event.MarketPriceUpdatedEvent;
import com.mock.maesoongan.realtimequoteingestor.quote.application.QuoteIngestionService;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.OrderbookLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketWebSocketHandlerTest {

    private ObjectMapper objectMapper;
    private QuoteIngestionService quoteIngestionService;
    private MarketStatusService marketStatusService;
    private MarketWebSocketHandler handler;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        quoteIngestionService = mock(QuoteIngestionService.class);
        marketStatusService = mock(MarketStatusService.class);
        handler = new MarketWebSocketHandler(objectMapper, quoteIngestionService, marketStatusService);
        session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        when(marketStatusService.currentStatus()).thenReturn(new MarketStatusResponse(
                MarketStatusType.OPEN,
                "open",
                LocalTime.of(9, 0),
                LocalTime.of(15, 30),
                LocalTime.of(10, 0),
                true
        ));
    }

    @Test
    void afterConnectionEstablishedSendsMarketStatus() throws Exception {
        handler.afterConnectionEstablished(session);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"type\":\"MARKET_STATUS\"");
    }

    @Test
    void subscribePriceSubscribesOnlyNewCodesAndSendsAck() throws Exception {
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_PRICE",
                  "stockCodes": ["005930", "005930", "000660"]
                }
                """));

        verify(quoteIngestionService).subscribePrices(List.of("005930", "000660"));
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getPayload())
                .contains("\"type\":\"SUBSCRIPTION_ACK\"")
                .contains("SUBSCRIBE_PRICE")
                .contains("005930")
                .contains("000660");
    }

    @Test
    void unsubscribePriceUnsubscribesFromSourceWhenLastSubscriberLeaves() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_PRICE",
                  "stockCodes": ["005930"]
                }
                """));

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "UNSUBSCRIBE_PRICE",
                  "stockCodes": ["005930"]
                }
                """));

        verify(quoteIngestionService).unsubscribePrices(List.of("005930"));
    }

    @Test
    void subscribeOrderbookAndIndexCallSourceSubscriptions() throws Exception {
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_ORDERBOOK",
                  "stockCodes": ["005930"]
                }
                """));
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_INDEX",
                  "markets": ["kospi", "KOSDAQ"]
                }
                """));

        verify(quoteIngestionService).subscribeOrderbooks(List.of("005930"));
        verify(quoteIngestionService).subscribeIndexes(List.of("KOSPI", "KOSDAQ"));
    }

    @Test
    void invalidJsonSendsErrorMessage() throws Exception {
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{invalid"));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getPayload())
                .contains("\"type\":\"ERROR\"");
    }

    @Test
    void marketPriceUpdateIsSentOnlyToSubscribedSession() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_PRICE",
                  "stockCodes": ["005930"]
                }
                """));

        handler.handleMarketPriceUpdated(new MarketPriceUpdatedEvent(price("005930")));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        assertThat(captor.getAllValues().stream().map(TextMessage::getPayload).toList())
                .anyMatch(payload -> payload.contains("\"type\":\"PRICE_UPDATE\"") && payload.contains("005930"));
    }

    @Test
    void orderbookAndIndexUpdatesAreSentToSubscribedSession() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_ORDERBOOK",
                  "stockCodes": ["005930"]
                }
                """));
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_INDEX",
                  "markets": ["KOSPI"]
                }
                """));

        handler.handleMarketOrderbookUpdated(new MarketOrderbookUpdatedEvent(new MarketOrderbookResponse(
                "005930",
                "Samsung",
                List.of(new OrderbookLevel(new BigDecimal("75500"), 100L)),
                List.of(new OrderbookLevel(new BigDecimal("75400"), 200L)),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        )));
        handler.handleMarketIndexUpdated(new MarketIndexUpdatedEvent(new MarketIndexResponse(
                "KOSPI",
                new BigDecimal("2850.10"),
                new BigDecimal("10.10"),
                new BigDecimal("0.36"),
                123456L,
                LocalDateTime.of(2026, 6, 11, 10, 0)
        )));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        List<String> payloads = captor.getAllValues().stream().map(TextMessage::getPayload).toList();
        assertThat(payloads).anyMatch(payload -> payload.contains("\"type\":\"ORDERBOOK_UPDATE\""));
        assertThat(payloads).anyMatch(payload -> payload.contains("\"type\":\"INDEX_UPDATE\""));
    }

    @Test
    void closingSessionUnsubscribesAllChannels() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_PRICE",
                  "stockCodes": ["005930"]
                }
                """));
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_ORDERBOOK",
                  "stockCodes": ["005930"]
                }
                """));
        handler.handleTextMessage(session, new TextMessage("""
                {
                  "action": "SUBSCRIBE_INDEX",
                  "markets": ["KOSPI"]
                }
                """));

        handler.afterConnectionClosed(session, org.springframework.web.socket.CloseStatus.NORMAL);

        verify(quoteIngestionService).unsubscribePrices(List.of("005930"));
        verify(quoteIngestionService).unsubscribeOrderbooks(List.of("005930"));
        verify(quoteIngestionService).unsubscribeIndexes(List.of("KOSPI"));
    }

    private MarketPriceResponse price(String stockCode) {
        return new MarketPriceResponse(
                stockCode,
                "Samsung",
                new BigDecimal("75400"),
                new BigDecimal("1200"),
                new BigDecimal("1.62"),
                "2",
                123456L,
                new BigDecimal("9300000000"),
                new BigDecimal("76000"),
                new BigDecimal("74000"),
                new BigDecimal("74200"),
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }
}
