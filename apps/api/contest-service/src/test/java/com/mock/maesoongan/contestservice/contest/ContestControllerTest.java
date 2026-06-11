package com.mock.maesoongan.contestservice.contest;

import com.mock.maesoongan.contestservice.auth.CurrentMemberProvider;
import com.mock.maesoongan.contestservice.common.BusinessException;
import com.mock.maesoongan.contestservice.common.GlobalExceptionHandler;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestDetailResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestJoinResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestLeaveResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestListItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestResultResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestStockItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.MyContestListItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.MyRankingResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.OrderValidationResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.PageInfo;
import com.mock.maesoongan.contestservice.contest.ContestDtos.PageResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.RankingItem;
import com.mock.maesoongan.contestservice.contest.ContestDtos.TopRankerItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ContestControllerTest {

    private ContestService contestService;
    private CurrentMemberProvider currentMemberProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        contestService = mock(ContestService.class);
        currentMemberProvider = mock(CurrentMemberProvider.class);
        mockMvc = standaloneSetup(new ContestController(contestService, currentMemberProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getContestsReturnsContestPage() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(contestService.getContests(7L, "\uD22C\uC790", "ACTIVE", "ALL", 0, 10)).thenReturn(new PageResponse<>(
                List.of(contestListItem(false, true, null)),
                1L,
                1,
                0
        ));

        mockMvc.perform(get("/api/contests")
                        .param("keyword", "\uD22C\uC790")
                        .param("status", "ACTIVE")
                        .param("participation", "ALL")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalElements", is(1)))
                .andExpect(jsonPath("$.data.content[0].contestId", is(3)))
                .andExpect(jsonPath("$.data.content[0].joinable", is(true)));
    }

    @Test
    void getMyContestsReturnsMyContestPage() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(contestService.getMyContests(7L, "ACTIVE", 0, 10)).thenReturn(new PageResponse<>(
                List.of(new MyContestListItem(
                        3L,
                        "\uD22C\uC790\uB300\uD68C",
                        "ACTIVE",
                        new BigDecimal("10000000"),
                        12L,
                        5,
                        new BigDecimal("10500000"),
                        new BigDecimal("500000"),
                        new BigDecimal("5.00"),
                        List.of(new TopRankerItem(1, "top", new BigDecimal("10.00"))),
                        LocalDateTime.of(2026, 6, 10, 9, 0),
                        LocalDateTime.of(2026, 6, 20, 15, 30)
                )),
                1L,
                1,
                0
        ));

        mockMvc.perform(get("/api/contests/my").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].contestId", is(3)))
                .andExpect(jsonPath("$.data.content[0].myRank", is(5)));
    }

    @Test
    void getContestReturnsContestDetail() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(contestService.getContest(3L, 7L)).thenReturn(contestDetailResponse(true, false, "ALREADY_JOINED"));

        mockMvc.perform(get("/api/contests/{contestId}", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contestId", is(3)))
                .andExpect(jsonPath("$.data.joined", is(true)))
                .andExpect(jsonPath("$.data.joinDisabledReason", is("ALREADY_JOINED")));
    }

    @Test
    void getContestReturnsNotFoundWhenContestDoesNotExist() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(contestService.getContest(999L, 7L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Contest not found"));

        mockMvc.perform(get("/api/contests/{contestId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    @Test
    void joinContestReturnsParticipationStatus() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(contestService.joinContest(3L, 7L)).thenReturn(new ContestJoinResponse(
                3L,
                7L,
                "ACTIVE",
                "PENDING",
                "Contest joined."
        ));

        mockMvc.perform(post("/api/contests/{contestId}/join", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contestId", is(3)))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    void leaveContestReturnsWithdrawnStatus() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(contestService.leaveContest(3L, 7L)).thenReturn(new ContestLeaveResponse(
                3L,
                7L,
                "WITHDRAWN",
                "Contest left."
        ));

        mockMvc.perform(patch("/api/contests/{contestId}/leave", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("WITHDRAWN")));
    }

    @Test
    void getContestStocksReturnsTradableStockPage() throws Exception {
        when(contestService.getContestStocks(3L, "\uC0BC\uC131", "KOSPI", 0, 20)).thenReturn(new PageResponse<>(
                List.of(new ContestStockItem(1L, "005930", "\uC0BC\uC131\uC804\uC790", "KOSPI", "ELECTRONICS", "ACTIVE")),
                1L,
                1,
                0
        ));

        mockMvc.perform(get("/api/contests/{contestId}/stocks", 3L)
                        .param("keyword", "\uC0BC\uC131")
                        .param("market", "KOSPI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code", is("005930")));
    }

    @Test
    void getRankingsReturnsRankingPage() throws Exception {
        when(contestService.getRankings(3L, 0, 20)).thenReturn(new PageResponse<>(
                List.of(rankingItem()),
                1L,
                1,
                0
        ));

        mockMvc.perform(get("/api/contests/{contestId}/rankings", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].rank", is(1)))
                .andExpect(jsonPath("$.data.content[0].nickname", is("winner")));
    }

    @Test
    void getMyRankingReturnsMyRanking() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(contestService.getMyRanking(3L, 7L)).thenReturn(new MyRankingResponse(
                3L,
                7L,
                true,
                5,
                new BigDecimal("10500000"),
                new BigDecimal("500000"),
                new BigDecimal("5.00"),
                false,
                null
        ));

        mockMvc.perform(get("/api/contests/{contestId}/rankings/me", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rank", is(5)))
                .andExpect(jsonPath("$.data.joined", is(true)));
    }

    @Test
    void getContestResultReturnsEndedContestResult() throws Exception {
        when(currentMemberProvider.memberId()).thenReturn(7L);
        when(contestService.getContestResult(3L, 7L, 0, 20)).thenReturn(new ContestResultResponse(
                3L,
                "\uD22C\uC790\uB300\uD68C",
                LocalDateTime.of(2026, 6, 20, 15, 30),
                12L,
                new MyRankingResponse(3L, 7L, true, 5, new BigDecimal("10500000"), new BigDecimal("500000"), new BigDecimal("5.00"), false, null),
                List.of(rankingItem()),
                new PageInfo(0, 1)
        ));

        mockMvc.perform(get("/api/contests/{contestId}/result", 3L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contestId", is(3)))
                .andExpect(jsonPath("$.data.pagination.totalPages", is(1)));
    }

    @Test
    void validateOrderReturnsValidationResult() throws Exception {
        when(contestService.validateOrder(any(Long.class), any())).thenReturn(new OrderValidationResponse(
                true,
                null,
                3L,
                7L,
                1L,
                new BigDecimal("1000000"),
                new BigDecimal("30.00")
        ));

        mockMvc.perform(post("/internal/contests/{contestId}/order-validation", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 7,
                                  "stockId": 1,
                                  "orderAmount": 754000,
                                  "stockRatioAfterOrder": 12.50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid", is(true)))
                .andExpect(jsonPath("$.data.maxOrderAmount", is(1000000)));

        verify(contestService).validateOrder(any(Long.class), any());
    }

    @Test
    void validateOrderReturnsBadRequestWhenRequiredFieldIsMissing() throws Exception {
        mockMvc.perform(post("/internal/contests/{contestId}/order-validation", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberId": 7,
                                  "orderAmount": 754000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    private ContestListItem contestListItem(boolean joined, boolean joinable, String reason) {
        return new ContestListItem(
                3L,
                "\uD22C\uC790\uB300\uD68C",
                "\uBAA8\uC758\uD22C\uC790 \uB300\uD68C",
                new BigDecimal("10000000"),
                100,
                12L,
                "ACTIVE",
                joined,
                joinable,
                reason,
                LocalDateTime.of(2026, 6, 10, 9, 0),
                LocalDateTime.of(2026, 6, 20, 15, 30)
        );
    }

    private ContestDetailResponse contestDetailResponse(boolean joined, boolean joinable, String reason) {
        return new ContestDetailResponse(
                3L,
                "\uD22C\uC790\uB300\uD68C",
                "\uBAA8\uC758\uD22C\uC790 \uB300\uD68C",
                new BigDecimal("10000000"),
                100,
                new BigDecimal("1000000"),
                new BigDecimal("30.00"),
                "ALL",
                "PROFIT_RATE",
                true,
                "ALL",
                "ACTIVE",
                12L,
                joined,
                joinable,
                reason,
                new BigDecimal("10500000"),
                new BigDecimal("500000"),
                new BigDecimal("5.00"),
                5,
                LocalDateTime.of(2026, 6, 10, 9, 0),
                LocalDateTime.of(2026, 6, 20, 15, 30)
        );
    }

    private RankingItem rankingItem() {
        return new RankingItem(
                10L,
                "winner",
                1,
                new BigDecimal("11000000"),
                new BigDecimal("1000000"),
                new BigDecimal("10.00")
        );
    }
}
