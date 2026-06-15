package com.mock.maesoongan.orderservice.order;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final OrderRepository orderRepository = new OrderRepository(jdbcTemplate);

    @Test
    void markCancelRequestedAllowsPartiallyFilledOrders() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 6, 15, 10, 0);

        orderRepository.markCancelRequested(7L, 1001L, requestedAt);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq(requestedAt),
                eq(requestedAt),
                eq(7L),
                eq(1001L)
        );
        assertThat(sqlCaptor.getValue()).contains("'PARTIALLY_FILLED'");
    }

    @Test
    void countOrdersIncludesCancelRequestedWhenFilteringCanceled() {
        LocalDate date = LocalDate.of(2026, 6, 15);

        orderRepository.countOrders(7L, 0L, "CANCELED", date);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(),
                eq(Integer.class),
                eq(7L),
                eq(0L),
                eq(0L),
                eq("CANCELED"),
                eq("CANCELED"),
                eq("CANCELED"),
                eq(date.atStartOfDay()),
                eq(date.plusDays(1).atStartOfDay())
        );
        assertThat(sqlCaptor.getValue()).contains("status in ('CANCELED', 'CANCEL_REQUESTED')");
    }
}
