package com.mock.maesoongan.realtimequoteingestor.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RealtimeCacheControllerTest {

    private RedisCacheProbe redisCacheProbe;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        redisCacheProbe = mock(RedisCacheProbe.class);
        mockMvc = standaloneSetup(new RealtimeCacheController(redisCacheProbe)).build();
    }

    @Test
    void statusReturnsRedisConnectionStatus() throws Exception {
        when(redisCacheProbe.status()).thenReturn(new RedisCacheProbe.RedisConnectionStatus(
                true,
                true,
                "PONG",
                LocalDateTime.of(2026, 6, 11, 10, 0)
        ));

        mockMvc.perform(get("/api/realtime/cache/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.enabled", is(true)))
                .andExpect(jsonPath("$.data.connected", is(true)));
    }

    @Test
    void priceReturnsPriceCacheKeyValue() throws Exception {
        when(redisCacheProbe.get("stock:005930:price")).thenReturn(cacheValue("stock:005930:price"));

        mockMvc.perform(get("/api/realtime/cache/price/{stockCode}", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key", is("stock:005930:price")))
                .andExpect(jsonPath("$.data.value", is("{\"stockCode\":\"005930\"}")));
    }

    @Test
    void orderbookReturnsOrderbookCacheKeyValue() throws Exception {
        when(redisCacheProbe.get("stock:005930:orderbook")).thenReturn(cacheValue("stock:005930:orderbook"));

        mockMvc.perform(get("/api/realtime/cache/orderbook/{stockCode}", "005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key", is("stock:005930:orderbook")));
    }

    @Test
    void indexUppercasesMarketKey() throws Exception {
        when(redisCacheProbe.get("market:index:KOSDAQ")).thenReturn(cacheValue("market:index:KOSDAQ"));

        mockMvc.perform(get("/api/realtime/cache/index/{market}", "kosdaq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key", is("market:index:KOSDAQ")));
    }

    @Test
    void stockMasterReturnsStockMasterCacheStatus() throws Exception {
        when(redisCacheProbe.get("stock:master:status")).thenReturn(cacheValue("stock:master:status"));

        mockMvc.perform(get("/api/realtime/cache/stock-master"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key", is("stock:master:status")));
    }

    private RedisCacheProbe.RedisCacheValue cacheValue(String key) {
        return new RedisCacheProbe.RedisCacheValue(
                true,
                true,
                key,
                "{\"stockCode\":\"005930\"}",
                null,
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );
    }
}
