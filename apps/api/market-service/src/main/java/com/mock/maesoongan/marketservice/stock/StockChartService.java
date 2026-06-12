package com.mock.maesoongan.marketservice.stock;

import com.mock.maesoongan.marketservice.common.BusinessException;
import com.mock.maesoongan.marketservice.marketdata.kis.KisDailyItemChartClient;
import com.mock.maesoongan.marketservice.marketdata.kis.KisDailyItemChartClient.DailyChartPrice;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.DailyPriceCoverage;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.StockDailyPriceRow;
import com.mock.maesoongan.marketservice.marketdata.repository.MarketDataRepository.StockDailyPriceUpsert;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockChartItem;
import com.mock.maesoongan.marketservice.stock.StockDtos.StockChartResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StockChartService {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketDataRepository marketDataRepository;
    private final KisDailyItemChartClient kisDailyItemChartClient;
    private final boolean syncEnabled;
    private final int kisWindowDays;

    public StockChartService(
            MarketDataRepository marketDataRepository,
            KisDailyItemChartClient kisDailyItemChartClient,
            @Value("${chart.sync-enabled:true}") boolean syncEnabled,
            @Value("${chart.kis-window-days:120}") int kisWindowDays
    ) {
        this.marketDataRepository = marketDataRepository;
        this.kisDailyItemChartClient = kisDailyItemChartClient;
        this.syncEnabled = syncEnabled;
        this.kisWindowDays = Math.max(1, kisWindowDays);
    }

    @Transactional
    public StockChartResponse getChart(String code, String period, String range, String from, String to) {
        String normalizedCode = normalizeCode(code);
        ChartPeriod chartPeriod = ChartPeriod.from(period);
        ChartRange chartRange = resolveRange(chartPeriod, range, from, to);
        long stockId = marketDataRepository.findActiveStockId(normalizedCode)
                .orElseThrow(() -> notFound("Stock not found"));

        if (syncEnabled) {
            syncMissingDailyPrices(stockId, normalizedCode, chartRange.from(), chartRange.to());
        }

        List<StockDailyPriceRow> dailyRows = marketDataRepository.findDailyPrices(
                normalizedCode,
                chartRange.from(),
                chartRange.to()
        );
        if (dailyRows.isEmpty()) {
            throw notFound("Stock chart data not found");
        }

        List<StockChartItem> items = switch (chartPeriod) {
            case D -> dailyRows.stream().map(this::toChartItem).toList();
            case W -> aggregateWeekly(dailyRows);
            case M -> aggregateMonthly(dailyRows);
        };

        return new StockChartResponse(
                normalizedCode,
                chartPeriod.name(),
                chartRange.from(),
                chartRange.to(),
                items
        );
    }

    // 최근 일별시세를 백필(없으면 KIS에서 받아 적재). 순위에서 현재가/종가가 없는 종목 보강용.
    @Transactional
    public void ensureRecentDailyPrices(String code) {
        if (!syncEnabled) {
            return;
        }
        String normalizedCode = normalizeCode(code);
        marketDataRepository.findActiveStockId(normalizedCode).ifPresent(stockId -> {
            LocalDate today = LocalDate.now(KST);
            syncMissingDailyPrices(stockId, normalizedCode, today.minusDays(kisWindowDays - 1L), today);
        });
    }

    private void syncMissingDailyPrices(long stockId, String code, LocalDate from, LocalDate requestedTo) {
        LocalDate today = LocalDate.now(KST);
        LocalDate syncTo = requestedTo.isBefore(today) ? requestedTo : today.minusDays(1);
        if (syncTo.isBefore(from)) {
            return;
        }

        DailyPriceCoverage coverage = marketDataRepository.findDailyPriceCoverage(code, from, syncTo);
        if (coverage != null
                && coverage.rowCount() > 0
                && !coverage.firstTradeDate().isAfter(from)
                && !coverage.lastTradeDate().isBefore(syncTo)) {
            return;
        }

        LocalDate cursor = from;
        while (!cursor.isAfter(syncTo)) {
            LocalDate windowTo = cursor.plusDays(kisWindowDays - 1L);
            if (windowTo.isAfter(syncTo)) {
                windowTo = syncTo;
            }
            List<StockDailyPriceUpsert> rows = kisDailyItemChartClient.fetchDailyPrices(code, cursor, windowTo)
                    .stream()
                    .map(price -> toUpsertRow(stockId, code, price))
                    .toList();
            marketDataRepository.upsertDailyPrices(rows);
            cursor = windowTo.plusDays(1);
        }
    }

    private StockDailyPriceUpsert toUpsertRow(long stockId, String code, DailyChartPrice price) {
        return new StockDailyPriceUpsert(
                stockId,
                code,
                price.tradeDate(),
                price.openPrice(),
                price.highPrice(),
                price.lowPrice(),
                price.closePrice(),
                price.prevClosePrice(),
                price.volume()
        );
    }

    private List<StockChartItem> aggregateWeekly(List<StockDailyPriceRow> rows) {
        WeekFields weekFields = WeekFields.ISO;
        Map<YearWeek, MutableCandle> candles = new LinkedHashMap<>();
        for (StockDailyPriceRow row : rows) {
            YearWeek key = new YearWeek(
                    row.tradeDate().get(weekFields.weekBasedYear()),
                    row.tradeDate().get(weekFields.weekOfWeekBasedYear())
            );
            candles.computeIfAbsent(key, ignored -> new MutableCandle(row)).add(row);
        }
        return candles.values().stream().map(MutableCandle::toItem).toList();
    }

    private List<StockChartItem> aggregateMonthly(List<StockDailyPriceRow> rows) {
        Map<YearMonth, MutableCandle> candles = new LinkedHashMap<>();
        for (StockDailyPriceRow row : rows) {
            YearMonth key = YearMonth.from(row.tradeDate());
            candles.computeIfAbsent(key, ignored -> new MutableCandle(row)).add(row);
        }
        return candles.values().stream().map(MutableCandle::toItem).toList();
    }

    private StockChartItem toChartItem(StockDailyPriceRow row) {
        return new StockChartItem(
                row.tradeDate(),
                row.openPrice(),
                row.highPrice(),
                row.lowPrice(),
                row.closePrice(),
                row.volume()
        );
    }

    private ChartRange resolveRange(ChartPeriod period, String range, String from, String to) {
        LocalDate today = LocalDate.now(KST);
        LocalDate toDate = parseOptionalDate(to, today);
        if (toDate.isAfter(today)) {
            toDate = today;
        }

        LocalDate fromDate;
        if (hasText(from)) {
            fromDate = parseDate(from);
        } else {
            fromDate = applyRange(toDate, hasText(range) ? range : period.defaultRange());
        }

        if (fromDate.isAfter(toDate)) {
            throw badRequest("from must be before or equal to to");
        }
        if (fromDate.isBefore(period.maxFrom(toDate))) {
            throw badRequest("period " + period.name() + " supports up to " + period.maxRangeLabel());
        }
        return new ChartRange(fromDate, toDate);
    }

    private LocalDate applyRange(LocalDate toDate, String range) {
        String normalized = range.trim().toUpperCase(Locale.ROOT);
        try {
            if (normalized.endsWith("M")) {
                int months = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
                return toDate.minusMonths(months);
            }
            if (normalized.endsWith("Y")) {
                int years = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
                return toDate.minusYears(years);
            }
        } catch (NumberFormatException ignored) {
            throw badRequest("range must be like 3M, 6M, 1Y, 3Y, or 5Y");
        }
        throw badRequest("range must be like 3M, 6M, 1Y, 3Y, or 5Y");
    }

    private LocalDate parseOptionalDate(String value, LocalDate fallback) {
        return hasText(value) ? parseDate(value) : fallback;
    }

    private LocalDate parseDate(String value) {
        String trimmed = value.trim();
        try {
            if (trimmed.contains("-")) {
                return LocalDate.parse(trimmed, ISO_DATE);
            }
            return LocalDate.parse(trimmed, BASIC_DATE);
        } catch (DateTimeParseException exception) {
            throw badRequest("date must be yyyyMMdd or yyyy-MM-dd");
        }
    }

    private String normalizeCode(String code) {
        if (!hasText(code)) {
            throw badRequest("stock code is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private enum ChartPeriod {
        D("3M", "1Y"),
        W("1Y", "3Y"),
        M("3Y", "5Y");

        private final String defaultRange;
        private final String maxRangeLabel;

        ChartPeriod(String defaultRange, String maxRangeLabel) {
            this.defaultRange = defaultRange;
            this.maxRangeLabel = maxRangeLabel;
        }

        static ChartPeriod from(String value) {
            if (!hasTextStatic(value)) {
                return D;
            }
            try {
                return ChartPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "period must be D, W, or M");
            }
        }

        String defaultRange() {
            return defaultRange;
        }

        String maxRangeLabel() {
            return maxRangeLabel;
        }

        LocalDate maxFrom(LocalDate toDate) {
            return switch (this) {
                case D -> toDate.minusYears(1);
                case W -> toDate.minusYears(3);
                case M -> toDate.minusYears(5);
            };
        }

        private static boolean hasTextStatic(String value) {
            return value != null && !value.isBlank();
        }
    }

    private record ChartRange(LocalDate from, LocalDate to) {
    }

    private record YearWeek(int year, int week) {
    }

    private static class MutableCandle {

        private LocalDate date;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private long volume;

        MutableCandle(StockDailyPriceRow first) {
            this.date = first.tradeDate();
            this.open = first.openPrice();
            this.high = first.highPrice();
            this.low = first.lowPrice();
            this.close = first.closePrice();
            this.volume = 0L;
        }

        void add(StockDailyPriceRow row) {
            this.date = row.tradeDate();
            this.high = this.high.max(row.highPrice());
            this.low = this.low.min(row.lowPrice());
            this.close = row.closePrice();
            this.volume += row.volume();
        }

        StockChartItem toItem() {
            return new StockChartItem(date, open, high, low, close, volume);
        }
    }
}
