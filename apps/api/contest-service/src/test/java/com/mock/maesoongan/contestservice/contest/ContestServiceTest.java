package com.mock.maesoongan.contestservice.contest;

import com.mock.maesoongan.contestservice.common.BusinessException;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestJoinResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.ContestResultResponse;
import com.mock.maesoongan.contestservice.contest.ContestDtos.OrderValidationRequest;
import com.mock.maesoongan.contestservice.contest.ContestDtos.OrderValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ContestService contestService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        contestService = new ContestService(jdbcTemplate);
    }

    @Test
    void getContestsThrowsBadRequestWhenPageIsInvalid() {
        assertThatThrownBy(() -> contestService.getContests(7L, null, "ALL", "ALL", -1, 10))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("BAD_REQUEST");
                });
    }

    @Test
    void getContestsThrowsBadRequestWhenStatusIsInvalid() {
        assertThatThrownBy(() -> contestService.getContests(7L, null, "INVALID", "ALL", 0, 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void joinContestReturnsActiveParticipation() throws Exception {
        mockContest("ACTIVE", true, "ALL", 100, "ALL", new BigDecimal("10000000"), null, null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L, 12L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ContestJoinResponse response = contestService.joinContest(3L, 7L);

        assertThat(response.contestId()).isEqualTo(3L);
        assertThat(response.memberId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.accountProvisionStatus()).isEqualTo("PENDING");
        verify(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    @Test
    void joinContestThrowsBadRequestWhenAlreadyJoined() throws Exception {
        mockContest("ACTIVE", true, "ALL", 100, "ALL", new BigDecimal("10000000"), null, null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);

        assertThatThrownBy(() -> contestService.joinContest(3L, 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void joinContestThrowsNotFoundWhenContestDoesNotExist() {
        mockContestNotFound();

        assertThatThrownBy(() -> contestService.joinContest(999L, 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("NOT_FOUND");
                });
    }

    @Test
    void getContestResultThrowsBadRequestWhenContestIsNotEnded() throws Exception {
        mockContest("ACTIVE", true, "ALL", 100, "ALL", new BigDecimal("10000000"), null, null);

        assertThatThrownBy(() -> contestService.getContestResult(3L, 7L, 0, 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void getContestResultReturnsEndedContestResult() throws Exception {
        mockContest("ENDED", true, "ALL", 100, "ALL", new BigDecimal("10000000"), null, null);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L, 1L, 1L, 12L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(java.util.List.of(
                new ContestDtos.RankingItem(
                        10L,
                        "winner",
                        1,
                        new BigDecimal("11000000"),
                        new BigDecimal("1000000"),
                        new BigDecimal("10.00")
                )
        ));

        ContestResultResponse response = contestService.getContestResult(3L, 7L, 0, 20);

        assertThat(response.contestId()).isEqualTo(3L);
        assertThat(response.totalParticipants()).isEqualTo(12L);
        assertThat(response.rankings()).hasSize(1);
        assertThat(response.pagination().totalPages()).isEqualTo(1);
    }

    @Test
    void validateOrderReturnsInvalidWhenContestDoesNotExist() {
        mockContestNotFound();
        OrderValidationRequest request = new OrderValidationRequest(
                7L,
                1L,
                new BigDecimal("754000"),
                new BigDecimal("12.50")
        );

        OrderValidationResponse response = contestService.validateOrder(999L, request);

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).isEqualTo("CONTEST_NOT_FOUND");
        assertThat(response.memberId()).isEqualTo(7L);
    }

    @Test
    void validateOrderReturnsInvalidWhenOrderAmountExceedsLimit() throws Exception {
        mockContest("ACTIVE", true, "ALL", 100, "ALL", new BigDecimal("10000000"), new BigDecimal("500000"), new BigDecimal("30.00"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L, 1L);

        OrderValidationResponse response = contestService.validateOrder(3L, new OrderValidationRequest(
                7L,
                1L,
                new BigDecimal("754000"),
                new BigDecimal("12.50")
        ));

        assertThat(response.valid()).isFalse();
        assertThat(response.reason()).isEqualTo("MAX_ORDER_AMOUNT_EXCEEDED");
        assertThat(response.maxOrderAmount()).isEqualByComparingTo("500000");
    }

    @Test
    void validateOrderReturnsValidWhenAllConditionsPass() throws Exception {
        mockContest("ACTIVE", true, "ALL", 100, "ALL", new BigDecimal("10000000"), new BigDecimal("1000000"), new BigDecimal("30.00"));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L, 1L);

        OrderValidationResponse response = contestService.validateOrder(3L, new OrderValidationRequest(
                7L,
                1L,
                new BigDecimal("754000"),
                new BigDecimal("12.50")
        ));

        assertThat(response.valid()).isTrue();
        assertThat(response.reason()).isNull();
        assertThat(response.maxStockRatio()).isEqualByComparingTo("30.00");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockContest(
            String status,
            boolean isPublic,
            String joinType,
            Integer maxParticipants,
            String stockType,
            BigDecimal seedMoney,
            BigDecimal maxOrderAmount,
            BigDecimal maxStockRatio
    ) throws Exception {
        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(3L);
            when(resultSet.getString("title")).thenReturn("\uD22C\uC790\uB300\uD68C");
            when(resultSet.getString("description")).thenReturn("\uBAA8\uC758\uD22C\uC790 \uB300\uD68C");
            when(resultSet.getBigDecimal("seed_money")).thenReturn(seedMoney);
            when(resultSet.getObject("max_participants", Integer.class)).thenReturn(maxParticipants);
            when(resultSet.getBigDecimal("max_order_amount")).thenReturn(maxOrderAmount);
            when(resultSet.getBigDecimal("max_stock_ratio")).thenReturn(maxStockRatio);
            when(resultSet.getString("stock_type")).thenReturn(stockType);
            when(resultSet.getString("profit_criteria")).thenReturn("PROFIT_RATE");
            when(resultSet.getBoolean("is_public")).thenReturn(isPublic);
            when(resultSet.getString("join_type")).thenReturn(joinType);
            when(resultSet.getString("status")).thenReturn(status);
            when(resultSet.getTimestamp("start_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 10, 9, 0)));
            when(resultSet.getTimestamp("end_at")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 6, 20, 15, 30)));
            return mapper.mapRow(resultSet, 0);
        }).when(jdbcTemplate).queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockContestNotFound() {
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbcTemplate)
                .queryForObject(anyString(), any(RowMapper.class), any(Object[].class));
    }
}
