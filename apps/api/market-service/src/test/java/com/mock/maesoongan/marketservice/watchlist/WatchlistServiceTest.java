package com.mock.maesoongan.marketservice.watchlist;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.WatchlistStockRow;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.AddWatchlistResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.DeleteWatchlistResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.WatchlistResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistServiceTest {

    private MarketDataRepository marketDataRepository;
    private WatchlistService watchlistService;

    @BeforeEach
    void setUp() {
        marketDataRepository = mock(MarketDataRepository.class);
        watchlistService = new WatchlistService(marketDataRepository);
    }

    @Test
    void getWatchlistReturnsDomesticStocks() {
        when(marketDataRepository.findWatchlistStocks(7L, List.of("KOSPI", "KOSDAQ"), 0L)).thenReturn(List.of(
                new WatchlistStockRow("005930", "삼성전자", "KOSPI", bd("75400"), bd("1200"), bd("1.62"))
        ));

        WatchlistResponse response = watchlistService.getWatchlist(7L, "domestic", 0L);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items().get(0).code()).isEqualTo("005930");
        verify(marketDataRepository).findWatchlistStocks(7L, List.of("KOSPI", "KOSDAQ"), 0L);
    }

    @Test
    void addWatchlistInsertsStockAndReturnsTotalCount() {
        when(marketDataRepository.existsActiveStock("005930")).thenReturn(true);
        when(marketDataRepository.existsWatchlist(7L, "005930", 0L)).thenReturn(false);
        when(marketDataRepository.countWatchlist(7L, 0L)).thenReturn(12, 13);

        AddWatchlistResponse response = watchlistService.addWatchlist(7L, " 005930 ", 0L);

        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.totalCount()).isEqualTo(13);
        verify(marketDataRepository).insertWatchlist(7L, "005930", 0L);
    }

    @Test
    void addWatchlistThrowsConflictWhenStockAlreadyExists() {
        when(marketDataRepository.existsActiveStock("005930")).thenReturn(true);
        when(marketDataRepository.existsWatchlist(7L, "005930", 0L)).thenReturn(true);

        assertThatThrownBy(() -> watchlistService.addWatchlist(7L, "005930", 0L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));

        verify(marketDataRepository, never()).insertWatchlist(7L, "005930", 0L);
    }

    @Test
    void addWatchlistThrowsConflictWhenLimitExceeded() {
        when(marketDataRepository.existsActiveStock("005930")).thenReturn(true);
        when(marketDataRepository.existsWatchlist(7L, "005930", 0L)).thenReturn(false);
        when(marketDataRepository.countWatchlist(7L, 0L)).thenReturn(30);

        assertThatThrownBy(() -> watchlistService.addWatchlist(7L, "005930", 0L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT));

        verify(marketDataRepository, never()).insertWatchlist(7L, "005930", 0L);
    }

    @Test
    void deleteWatchlistRemovesStockAndReturnsTotalCount() {
        when(marketDataRepository.existsWatchlist(7L, "005930", 0L)).thenReturn(true);
        when(marketDataRepository.countWatchlist(7L, 0L)).thenReturn(12);

        DeleteWatchlistResponse response = watchlistService.deleteWatchlist(7L, "005930", 0L);

        assertThat(response.totalCount()).isEqualTo(12);
        verify(marketDataRepository).deleteWatchlist(7L, "005930", 0L);
    }

    @Test
    void deleteWatchlistThrowsNotFoundWhenStockIsNotRegistered() {
        when(marketDataRepository.existsWatchlist(7L, "005930", 0L)).thenReturn(false);

        assertThatThrownBy(() -> watchlistService.deleteWatchlist(7L, "005930", 0L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getWatchlistThrowsBadRequestWhenMarketIsInvalid() {
        assertThatThrownBy(() -> watchlistService.getWatchlist(7L, "invalid", 0L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
