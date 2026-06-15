package com.mock.maesoongan.orderservice.order;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
