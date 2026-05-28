package com.mock.maesoongan.realtimequoteingestor.market.application;

import com.mock.maesoongan.realtimequoteingestor.market.dto.MarketPriceResponse;
import com.mock.maesoongan.realtimequoteingestor.quote.domain.PriceQuoteEvent;
import com.mock.maesoongan.realtimequoteingestor.stock.StockNameResolver;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class MarketPriceMapper {

    private final StockNameResolver stockNameResolver;

    public MarketPriceMapper(StockNameResolver stockNameResolver) {
        this.stockNameResolver = stockNameResolver;
    }

    public MarketPriceResponse from(PriceQuoteEvent event) {
        BigDecimal tradingValue = event.price().multiply(BigDecimal.valueOf(event.volume()));
        BigDecimal open = event.price().subtract(event.change());
        BigDecimal high = event.price().max(open);
        BigDecimal low = event.price().min(open);

        return new MarketPriceResponse(
                event.code(),
                stockNameResolver.resolve(event.code(), event.name()),
                event.price().setScale(0, RoundingMode.HALF_UP),
                event.change(),
                event.changeRate(),
                changeSign(event.change()),
                event.volume(),
                tradingValue.setScale(0, RoundingMode.HALF_UP),
                high.setScale(0, RoundingMode.HALF_UP),
                low.setScale(0, RoundingMode.HALF_UP),
                open.setScale(0, RoundingMode.HALF_UP),
                event.sourceTimestamp()
        );
    }

    private static String changeSign(BigDecimal change) {
        int sign = change.signum();
        if (sign > 0) {
            return "+";
        }
        if (sign < 0) {
            return "-";
        }
        return "0";
    }
}
