package com.mock.maesoongan.marketservice.stock;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.OrderbookLevelRow;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.StockDailyPriceRow;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.StockPriceRow;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.StockSearchRow;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockDailyInfoResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockOrderbookResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockPriceResponse;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private MarketDataRepository marketDataRepository;
    private StockService stockService;

    @BeforeEach
    void setUp() {
        marketDataRepository = mock(MarketDataRepository.class);
        stockService = new StockService(marketDataRepository);
    }

    @Test
    void getPriceReturnsStockPrice() {
        when(marketDataRepository.findStockPrice("005930")).thenReturn(Optional.of(new StockPriceRow(
                1L,
                "005930",
                "\uC0BC\uC131\uC804\uC790",
                "KOSPI",
                bd("75400"),
                bd("1200"),
                bd("1.62"),
                12_300_000L,
                LocalDateTime.of(2026, 6, 10, 9, 0)
        )));

        StockPriceResponse response = stockService.getPrice(" 005930 ");

        assertThat(response.code()).isEqualTo("005930");
        assertThat(response.name()).isEqualTo("\uC0BC\uC131\uC804\uC790");
        assertThat(response.price()).isEqualByComparingTo("75400");
        assertThat(response.changeRate()).isEqualByComparingTo("1.62");
    }

    @Test
    void getPriceThrowsNotFoundWhenStockDoesNotExist() {
        when(marketDataRepository.findStockPrice("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.getPrice("000000"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void getDailyInfoReturnsLatestDailyPrice() {
        when(marketDataRepository.findLatestDailyPrice("005930")).thenReturn(Optional.of(dailyRow(
                LocalDate.of(2026, 6, 10),
                "74200",
                "75900",
                "74100",
                "75400",
                "74200",
                12_300_000L
        )));

        StockDailyInfoResponse response = stockService.getDailyInfo("005930");

        assertThat(response.open()).isEqualByComparingTo("74200");
        assertThat(response.high()).isEqualByComparingTo("75900");
        assertThat(response.low()).isEqualByComparingTo("74100");
        assertThat(response.prevClose()).isEqualByComparingTo("74200");
    }

    @Test
    void getOrderbookReturnsAskAndBidLevels() {
        when(marketDataRepository.existsActiveStock("005930")).thenReturn(true);
        when(marketDataRepository.findOrderbookLevels("005930", "ASK")).thenReturn(List.of(
                new OrderbookLevelRow(bd("75800"), 3214L, 1)
        ));
        when(marketDataRepository.findOrderbookLevels("005930", "BID")).thenReturn(List.of(
                new OrderbookLevelRow(bd("75300"), 6234L, 1)
        ));

        StockOrderbookResponse response = stockService.getOrderbook("005930");

        assertThat(response.asks()).hasSize(1);
        assertThat(response.asks().get(0).price()).isEqualByComparingTo("75800");
        assertThat(response.bids().get(0).quantity()).isEqualTo(6234L);
    }

    @Test
    void searchReturnsStocksWithWatchlistFlag() {
        when(marketDataRepository.searchStocks("\uC0BC\uC131", "KOSPI", 7L)).thenReturn(List.of(
                new StockSearchRow(1L, "005930", "\uC0BC\uC131\uC804\uC790", "KOSPI", bd("75400"), bd("1.62"), true)
        ));

        StockSearchResponse response = stockService.search(" \uC0BC\uC131 ", "kospi", 7L);

        assertThat(response.stocks()).hasSize(1);
        assertThat(response.stocks().get(0).stockCode()).isEqualTo("005930");
        assertThat(response.stocks().get(0).isWatchlisted()).isTrue();
        verify(marketDataRepository).searchStocks("\uC0BC\uC131", "KOSPI", 7L);
    }

    @Test
    void searchThrowsBadRequestWhenMarketIsInvalid() {
        assertThatThrownBy(() -> stockService.search("\uC0BC\uC131", "KRX", 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private StockDailyPriceRow dailyRow(
            LocalDate tradeDate,
            String open,
            String high,
            String low,
            String close,
            String prevClose,
            long volume
    ) {
        return new StockDailyPriceRow(
                "005930",
                tradeDate,
                bd(open),
                bd(high),
                bd(low),
                bd(close),
                bd(prevClose),
                volume
        );
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
