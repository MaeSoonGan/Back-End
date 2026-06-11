package com.mock.maesoongan.marketservice.market;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketIndexResponse;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketRankingItem;
import com.mock.maesoongan.marketservice.market.MarketDtos.MarketStatusResponse;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.MarketIndexRow;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.MarketRankingRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketServiceTest {

    private MarketDataRepository marketDataRepository;
    private MarketService marketService;

    @BeforeEach
    void setUp() {
        marketDataRepository = mock(MarketDataRepository.class);
        marketService = new MarketService(marketDataRepository);
    }

    @Test
    void getMarketIndexReturnsSnapshot() {
        when(marketDataRepository.findMarketIndex("KOSPI")).thenReturn(Optional.of(new MarketIndexRow(
                "KOSPI",
                bd("2847.00"),
                bd("15.42"),
                bd("0.54"),
                false,
                LocalDateTime.of(2026, 6, 10, 9, 0)
        )));

        MarketIndexResponse response = marketService.getMarketIndex("kospi");

        assertThat(response.market()).isEqualTo("KOSPI");
        assertThat(response.value()).isEqualByComparingTo("2847.00");
        assertThat(response.isCached()).isFalse();
        verify(marketDataRepository).findMarketIndex("KOSPI");
    }

    @Test
    void getMarketIndexThrowsBadRequestWhenMarketIsInvalid() {
        assertThatThrownBy(() -> marketService.getMarketIndex("KRX"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getMarketStatusReturnsTradingHours() {
        MarketStatusResponse response = marketService.getMarketStatus();

        assertThat(response.status()).isIn("open", "closed");
        assertThat(response.openTime()).isEqualTo("09:00");
        assertThat(response.closeTime()).isEqualTo("15:30");
    }

    @Test
    void getMarketRankingDefaultsToTradingValue() {
        when(marketDataRepository.findMarketRankings("TRADING_VALUE")).thenReturn(List.of(
                new MarketRankingRow(1, "005930", "\uC0BC\uC131\uC804\uC790", "KOSPI", bd("75400"), bd("1200"), bd("1.62"), 12_300_000L)
        ));

        List<MarketRankingItem> response = marketService.getMarketRanking(null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).code()).isEqualTo("005930");
        verify(marketDataRepository).findMarketRankings("TRADING_VALUE");
    }

    @Test
    void getMarketRankingAcceptsKoreanRiseType() {
        when(marketDataRepository.findMarketRankings("RISE")).thenReturn(List.of());

        List<MarketRankingItem> response = marketService.getMarketRanking("\uC0C1\uC2B9");

        assertThat(response).isEmpty();
        verify(marketDataRepository).findMarketRankings("RISE");
    }

    @Test
    void getMarketRankingThrowsBadRequestWhenTypeIsInvalid() {
        assertThatThrownBy(() -> marketService.getMarketRanking("invalid"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
