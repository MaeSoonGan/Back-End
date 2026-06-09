package com.mock.maesoongan.orderservice.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.orderservice.common.BusinessException;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.AvailableCashResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ContestAccountResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingDetailResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.HoldingSnapshot;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.PortfolioSummaryResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.ProfitHistoryItem;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.SeedMoneyResetRequest;
import com.mock.maesoongan.orderservice.portfolio.PortfolioDtos.SeedMoneyResetResponse;
import com.mock.maesoongan.orderservice.portfolio.PortfolioRepository.PortfolioRow;
import com.mock.maesoongan.orderservice.portfolio.PortfolioRepository.StockPriceRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    private static final long DEFAULT_CONTEST_ID = 0L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final BigDecimal DEFAULT_SEED_MONEY = BigDecimal.valueOf(10_000_000L);

    private final PortfolioRepository portfolioRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.portfolioRepository = portfolioRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse getSummary(long memberId) {
        PortfolioRow portfolio = findPortfolio(memberId, DEFAULT_CONTEST_ID);
        BigDecimal cashBalance = value(portfolio.cashBalance());
        BigDecimal availableBalance = availableBalance(memberId, DEFAULT_CONTEST_ID, portfolio.availableCash());
        BigDecimal stockValuation = value(portfolio.stockEvaluationAmount());
        return new PortfolioSummaryResponse(
                totalAsset(cashBalance, stockValuation, portfolio.totalAsset()),
                cashBalance,
                availableBalance,
                reservedAmount(cashBalance, availableBalance),
                stockValuation,
                value(portfolio.profitAmount()),
                value(portfolio.profitRate())
        );
    }

    @Transactional(readOnly = true)
    public AvailableCashResponse getAvailableCash(long memberId) {
        PortfolioRow portfolio = findPortfolio(memberId, DEFAULT_CONTEST_ID);
        BigDecimal cashBalance = value(portfolio.cashBalance());
        BigDecimal availableBalance = availableBalance(memberId, DEFAULT_CONTEST_ID, portfolio.availableCash());
        return new AvailableCashResponse(cashBalance, availableBalance, reservedAmount(cashBalance, availableBalance));
    }

    @Transactional(readOnly = true)
    public List<HoldingItem> getHoldings(long memberId, Long contestId) {
        long resolvedContestId = resolveContestId(contestId);
        PortfolioRow portfolio = findPortfolio(memberId, resolvedContestId);
        return parseHoldings(portfolio.holdingsJson())
                .stream()
                .map(holding -> toHoldingItem(memberId, resolvedContestId, holding))
                .toList();
    }

    @Transactional(readOnly = true)
    public HoldingDetailResponse getHolding(long memberId, String stockCode, Long contestId) {
        long resolvedContestId = resolveContestId(contestId);
        String normalizedCode = stockCode.trim().toUpperCase(Locale.ROOT);
        HoldingSnapshot holding = parseHoldings(findPortfolio(memberId, resolvedContestId).holdingsJson())
                .stream()
                .filter(item -> normalizedCode.equals(item.stockCode()))
                .findFirst()
                .orElse(new HoldingSnapshot(normalizedCode, 0));

        long pendingSellQuantity = portfolioRepository.countPendingSellQuantity(memberId, resolvedContestId, normalizedCode);
        long availableQuantity = Math.max(0, holding.quantity() - pendingSellQuantity);
        StockPriceRow stock = portfolioRepository.findStockPrice(normalizedCode)
                .orElse(new StockPriceRow(normalizedCode, normalizedCode, BigDecimal.ZERO));

        return new HoldingDetailResponse(
                normalizedCode,
                holding.quantity(),
                availableQuantity,
                value(stock.currentPrice())
        );
    }

    @Transactional(readOnly = true)
    public List<ProfitHistoryItem> getProfitHistory(long memberId, String period) {
        PortfolioRow portfolio = findPortfolio(memberId, DEFAULT_CONTEST_ID);
        int days = daysByPeriod(period);
        LocalDate today = LocalDate.now(SEOUL);
        List<ProfitHistoryItem> items = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            items.add(new ProfitHistoryItem(
                    today.minusDays(i),
                    value(portfolio.profitRate()),
                    value(portfolio.totalAsset())
            ));
        }
        return items;
    }

    @Transactional(readOnly = true)
    public ContestAccountResponse getContestAccount(long memberId, long contestId) {
        var row = portfolioRepository.findContestAccount(memberId, contestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Contest account not found"));
        BigDecimal cashBalance = value(row.cashBalance());
        BigDecimal availableBalance = availableBalance(memberId, contestId, row.availableBalance());
        BigDecimal stockValuation = value(row.stockEvaluationAmount());

        return new ContestAccountResponse(
                row.contestId(),
                value(row.seedMoney()),
                totalAsset(cashBalance, stockValuation, row.currentAsset()),
                cashBalance,
                availableBalance,
                reservedAmount(cashBalance, availableBalance),
                value(row.profitAmount()),
                value(row.profitRate()),
                row.rank(),
                row.totalParticipants()
        );
    }

    @Transactional
    public SeedMoneyResetResponse resetSeedMoney(long memberId, SeedMoneyResetRequest request) {
        validateResetRequest(request);

        ZonedDateTime now = ZonedDateTime.now(SEOUL);
        LocalDate resetDate = now.toLocalDate();
        LocalDateTime resetAt = now.toLocalDateTime();
        LocalDateTime nextResetAvailableAt = resetDate.plusDays(1).atStartOfDay();
        String resetKey = resetKey(memberId, DEFAULT_CONTEST_ID, resetDate);
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                resetKey,
                resetAt.toString(),
                Duration.between(now, resetDate.plusDays(1).atStartOfDay(SEOUL))
        );
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "SEED_RESET_LIMIT_EXCEEDED", "Seed money can be reset once per day");
        }

        try {
            BigDecimal previousTotalAsset = portfolioRepository.findPortfolio(memberId, DEFAULT_CONTEST_ID)
                    .map(portfolio -> totalAsset(
                            value(portfolio.cashBalance()),
                            value(portfolio.stockEvaluationAmount()),
                            portfolio.totalAsset()
                    ))
                    .orElse(BigDecimal.ZERO);
            int canceledOrderCount = portfolioRepository.cancelOpenOrdersForReset(memberId, DEFAULT_CONTEST_ID, resetAt);

            portfolioRepository.resetPortfolio(memberId, DEFAULT_CONTEST_ID, DEFAULT_SEED_MONEY, resetAt);
            evictBalanceCache(memberId, DEFAULT_CONTEST_ID);

            return new SeedMoneyResetResponse(
                    DEFAULT_SEED_MONEY,
                    previousTotalAsset,
                    DEFAULT_SEED_MONEY,
                    DEFAULT_SEED_MONEY,
                    0,
                    canceledOrderCount,
                    resetDate,
                    resetAt,
                    nextResetAvailableAt
            );
        } catch (RuntimeException exception) {
            redisTemplate.delete(resetKey);
            throw exception;
        }
    }

    private long resolveContestId(Long contestId) {
        return contestId == null ? DEFAULT_CONTEST_ID : contestId;
    }

    private PortfolioRow findPortfolio(long memberId, long contestId) {
        return portfolioRepository.findPortfolio(memberId, contestId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Portfolio not found"));
    }

    private HoldingItem toHoldingItem(long memberId, long contestId, HoldingSnapshot holding) {
        StockPriceRow stock = portfolioRepository.findStockPrice(holding.stockCode())
                .orElse(new StockPriceRow(holding.stockCode(), holding.stockCode(), BigDecimal.ZERO));
        BigDecimal currentPrice = value(stock.currentPrice());
        BigDecimal quantity = BigDecimal.valueOf(holding.quantity());
        BigDecimal valuation = currentPrice.multiply(quantity);
        BigDecimal avgPrice = currentPrice;
        BigDecimal purchaseAmount = avgPrice.multiply(quantity);
        BigDecimal profitAmount = valuation.subtract(purchaseAmount);
        BigDecimal profitRate = purchaseAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : profitAmount.divide(purchaseAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        long pendingSellQuantity = portfolioRepository.countPendingSellQuantity(memberId, contestId, holding.stockCode());
        long availableQuantity = Math.max(0, holding.quantity() - pendingSellQuantity);

        return new HoldingItem(
                holding.stockCode(),
                stock.stockName(),
                holding.quantity(),
                avgPrice,
                currentPrice,
                valuation,
                profitAmount,
                profitRate
        );
    }

    private List<HoldingSnapshot> parseHoldings(String holdingsJson) {
        if (holdingsJson == null || holdingsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(holdingsJson);
            if (!root.isArray()) {
                return List.of();
            }
            List<HoldingSnapshot> holdings = new ArrayList<>();
            for (JsonNode item : root) {
                String stockCode = item.path("stockCode").asText("").trim().toUpperCase(Locale.ROOT);
                long quantity = item.path("quantity").asLong(0);
                if (!stockCode.isBlank() && quantity > 0) {
                    holdings.add(new HoldingSnapshot(stockCode, quantity));
                }
            }
            return holdings;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private BigDecimal availableBalance(long memberId, long contestId, BigDecimal fallback) {
        try {
            String cached = redisTemplate.opsForValue().get("balance:" + memberId + ":" + contestId);
            if (cached != null && !cached.isBlank()) {
                return new BigDecimal(cached);
            }
        } catch (NumberFormatException ignored) {
            // 캐시값 형식 오류 → DB값 사용 (Redis 장애 아님, 로그 불필요)
        } catch (Exception e) {
            // Redis 연결 실패/타임아웃 → DB값으로 폴백 (서비스 중단 방지). 모니터링 위해 WARN 로깅.
            log.warn("Redis 사용 불가 (balance:{}:{}) → DB값으로 폴백. cause={}", memberId, contestId, e.toString());
        }
        return value(fallback);
    }

    private BigDecimal reservedAmount(BigDecimal cashBalance, BigDecimal availableBalance) {
        BigDecimal reservedAmount = cashBalance.subtract(availableBalance);
        return reservedAmount.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : reservedAmount;
    }

    private BigDecimal totalAsset(BigDecimal cashBalance, BigDecimal stockValuation, BigDecimal fallback) {
        BigDecimal calculated = cashBalance.add(stockValuation);
        return calculated.compareTo(BigDecimal.ZERO) == 0 ? value(fallback) : calculated;
    }

    private void validateResetRequest(SeedMoneyResetRequest request) {
        if (request == null || !request.holdingsAndCashResetAgreed() || !request.irreversibleAgreed()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "RESET_CONFIRMATION_REQUIRED", "Seed money reset requires both confirmations");
        }
    }

    private void evictBalanceCache(long memberId, long contestId) {
        redisTemplate.delete(List.of(
                balanceKey(memberId, contestId),
                pendingReleaseKey(memberId, contestId)
        ));
    }

    private String resetKey(long memberId, long contestId, LocalDate resetDate) {
        return "seed-reset:" + memberId + ":" + contestId + ":" + resetDate;
    }

    private String balanceKey(long memberId, long contestId) {
        return "balance:" + memberId + ":" + contestId;
    }

    private String pendingReleaseKey(long memberId, long contestId) {
        return "balance:pending-release:" + memberId + ":" + contestId;
    }

    private int daysByPeriod(String period) {
        if (period == null || period.isBlank() || "1M".equalsIgnoreCase(period)) {
            return 30;
        }
        return switch (period.trim().toUpperCase(Locale.ROOT)) {
            case "3M" -> 90;
            case "6M" -> 180;
            case "1Y" -> 365;
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "period must be 1M, 3M, 6M, or 1Y");
        };
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
