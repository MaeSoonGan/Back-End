package com.mock.maesoongan.marketservice.watchlist;

import com.mock.maesoongan.marketservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.marketservice.common.GlobalExceptionHandler;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.AddWatchlistResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.DeleteWatchlistResponse;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.WatchlistItem;
import com.mock.maesoongan.marketservice.watchlist.WatchlistDtos.WatchlistResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WatchlistControllerTest {

    private WatchlistService watchlistService;
    private CurrentMemberProvider currentMemberProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        watchlistService = mock(WatchlistService.class);
        currentMemberProvider = mock(CurrentMemberProvider.class);
        mockMvc = standaloneSetup(new WatchlistController(watchlistService, currentMemberProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getWatchlistReturnsMemberWatchlist() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(watchlistService.getWatchlist(7L, "domestic")).thenReturn(new WatchlistResponse(
                1,
                List.of(new WatchlistItem(
                        "005930",
                        "\uC0BC\uC131\uC804\uC790",
                        "KOSPI",
                        new BigDecimal("75400"),
                        new BigDecimal("1200"),
                        new BigDecimal("1.62")
                ))
        ));

        mockMvc.perform(get("/api/watchlist").param("market", "domestic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("005930"));

        verify(watchlistService).getWatchlist(7L, "domestic");
    }

    @Test
    void addWatchlistReturnsCreated() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(watchlistService.addWatchlist(7L, "005930")).thenReturn(new AddWatchlistResponse("005930", 13));

        mockMvc.perform(post("/api/watchlist/005930"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.stockCode").value("005930"))
                .andExpect(jsonPath("$.data.totalCount").value(13));

        verify(watchlistService).addWatchlist(7L, "005930");
    }

    @Test
    void deleteWatchlistReturnsRemainingCount() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(watchlistService.deleteWatchlist(7L, "005930")).thenReturn(new DeleteWatchlistResponse(12));

        mockMvc.perform(delete("/api/watchlist/005930"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(12));

        verify(watchlistService).deleteWatchlist(7L, "005930");
    }
}
