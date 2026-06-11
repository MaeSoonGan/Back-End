package com.mock.maesoongan.marketservice.stock;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.marketdata.kis.KisDailyItemChartClient;
import com.mock.maesoongan.marketservice.marketdata.kis.KisDailyItemChartClient.DailyChartPrice;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.DailyPriceCoverage;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.StockDailyPriceRow;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockChartResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StockChartServiceTest {

    private MarketDataRepository marketDataRepository;
    private KisDailyItemChartClient kisDailyItemChartClient;

    @BeforeEach
    void setUp() {
        marketDataRepository = mock(MarketDataRepository.class);
        kisDailyItemChartClient = mock(KisDailyItemChartClient.class);
    }

    @Test
    void getChartReturnsDailyCandles() {
        StockChartService service = stockChartService(false);
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 2);
        when(marketDataRepository.findActiveStockId("005930")).thenReturn(Optional.of(1L));
        when(marketDataRepository.findDailyPrices("005930", from, to)).thenReturn(List.of(
                dailyRow(LocalDate.of(2026, 6, 1), "70000", "71000", "69000", "70500", "69800", 100L),
                dailyRow(LocalDate.of(2026, 6, 2), "70600", "72000", "70400", "71800", "70500", 200L)
        ));

        StockChartResponse response = service.getChart("005930", "D", null, "2026-06-01", "2026-06-02");

        assertThat(response.period()).isEqualTo("D");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(1).close()).isEqualByComparingTo("71800");
    }

    @Test
    void getChartAggregatesWeeklyCandles() {
        StockChartService service = stockChartService(false);
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 5);
        when(marketDataRepository.findActiveStockId("005930")).thenReturn(Optional.of(1L));
        when(marketDataRepository.findDailyPrices("005930", from, to)).thenReturn(List.of(
                dailyRow(LocalDate.of(2026, 6, 1), "70000", "71000", "69000", "70500", "69800", 100L),
                dailyRow(LocalDate.of(2026, 6, 2), "70600", "72000", "70400", "71800", "70500", 200L),
                dailyRow(LocalDate.of(2026, 6, 5), "71900", "72500", "71500", "72200", "71800", 300L)
        ));

        StockChartResponse response = service.getChart("005930", "W", null, "2026-06-01", "2026-06-05");

        assertThat(response.period()).isEqualTo("W");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(response.items().get(0).open()).isEqualByComparingTo("70000");
        assertThat(response.items().get(0).high()).isEqualByComparingTo("72500");
        assertThat(response.items().get(0).low()).isEqualByComparingTo("69000");
        assertThat(response.items().get(0).close()).isEqualByComparingTo("72200");
        assertThat(response.items().get(0).volume()).isEqualTo(600L);
    }

    @Test
    void getChartAggregatesMonthlyCandles() {
        StockChartService service = stockChartService(false);
        LocalDate from = LocalDate.of(2026, 5, 29);
        LocalDate to = LocalDate.of(2026, 6, 2);
        when(marketDataRepository.findActiveStockId("005930")).thenReturn(Optional.of(1L));
        when(marketDataRepository.findDailyPrices("005930", from, to)).thenReturn(List.of(
                dailyRow(LocalDate.of(2026, 5, 29), "69000", "71000", "68500", "70500", "68800", 100L),
                dailyRow(LocalDate.of(2026, 6, 1), "70600", "72000", "70400", "71800", "70500", 200L),
                dailyRow(LocalDate.of(2026, 6, 2), "71900", "72500", "71500", "72200", "71800", 300L)
        ));

        StockChartResponse response = service.getChart("005930", "M", null, "2026-05-29", "2026-06-02");

        assertThat(response.period()).isEqualTo("M");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).close()).isEqualByComparingTo("70500");
        assertThat(response.items().get(1).open()).isEqualByComparingTo("70600");
        assertThat(response.items().get(1).volume()).isEqualTo(500L);
    }

    @Test
    void getChartSyncsMissingDailyPricesFromKis() {
        StockChartService service = stockChartService(true);
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 2);
        when(marketDataRepository.findActiveStockId("005930")).thenReturn(Optional.of(1L));
        when(marketDataRepository.findDailyPriceCoverage("005930", from, to))
                .thenReturn(new DailyPriceCoverage(null, null, 0));
        when(kisDailyItemChartClient.fetchDailyPrices("005930", from, to)).thenReturn(List.of(
                new DailyChartPrice(
                        LocalDate.of(2026, 6, 2),
                        bd("70600"),
                        bd("72000"),
                        bd("70400"),
                        bd("71800"),
                        bd("70500"),
                        200L
                )
        ));
        when(marketDataRepository.findDailyPrices("005930", from, to)).thenReturn(List.of(
                dailyRow(LocalDate.of(2026, 6, 2), "70600", "72000", "70400", "71800", "70500", 200L)
        ));

        StockChartResponse response = service.getChart("005930", "D", null, "2026-06-01", "2026-06-02");

        assertThat(response.items()).hasSize(1);
        verify(marketDataRepository).upsertDailyPrices(argThat(rows ->
                rows.size() == 1
                        && rows.get(0).stockId() == 1L
                        && rows.get(0).code().equals("005930")
                        && rows.get(0).closePrice().compareTo(bd("71800")) == 0
        ));
    }

    @Test
    void getChartDoesNotFetchKisWhenRangeOnlyContainsToday() {
        StockChartService service = stockChartService(true);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        when(marketDataRepository.findActiveStockId("005930")).thenReturn(Optional.of(1L));
        when(marketDataRepository.findDailyPrices("005930", today, today)).thenReturn(List.of(
                dailyRow(today, "70600", "72000", "70400", "71800", "70500", 200L)
        ));

        StockChartResponse response = service.getChart("005930", "D", null, today.toString(), today.toString());

        assertThat(response.items()).hasSize(1);
        verifyNoInteractions(kisDailyItemChartClient);
    }

    @Test
    void getChartThrowsBadRequestWhenPeriodIsInvalid() {
        StockChartService service = stockChartService(false);

        assertThatThrownBy(() -> service.getChart("005930", "Q", null, "2026-06-01", "2026-06-02"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private StockChartService stockChartService(boolean syncEnabled) {
        return new StockChartService(marketDataRepository, kisDailyItemChartClient, syncEnabled, 120);
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
