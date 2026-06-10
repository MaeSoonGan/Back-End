package com.mock.maesoongan.marketservice.marketdata.kis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KisMarketProperties {

    private final String appKey;
    private final String appSecret;
    private final String baseUrl;
    private final String tokenUrl;
    private final String customerType;
    private final String dailyChartPath;
    private final String dailyChartTrId;
    private final String dailyChartMarketDivCode;
    private final String dailyChartAdjustedPrice;

    public KisMarketProperties(
            @Value("${kis.app-key:}") String appKey,
            @Value("${kis.app-secret:}") String appSecret,
            @Value("${kis.base-url:https://openapivts.koreainvestment.com:29443}") String baseUrl,
            @Value("${kis.token-url:}") String tokenUrl,
            @Value("${kis.customer-type:P}") String customerType,
            @Value("${kis.daily-chart-path:/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice}") String dailyChartPath,
            @Value("${kis.daily-chart-tr-id:FHKST03010100}") String dailyChartTrId,
            @Value("${kis.daily-chart-market-div-code:J}") String dailyChartMarketDivCode,
            @Value("${kis.daily-chart-adjusted-price:0}") String dailyChartAdjustedPrice
    ) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.baseUrl = baseUrl;
        this.tokenUrl = tokenUrl;
        this.customerType = customerType;
        this.dailyChartPath = dailyChartPath;
        this.dailyChartTrId = dailyChartTrId;
        this.dailyChartMarketDivCode = dailyChartMarketDivCode;
        this.dailyChartAdjustedPrice = dailyChartAdjustedPrice;
    }

    public String appKey() {
        return appKey;
    }

    public String appSecret() {
        return appSecret;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String tokenUrl() {
        return tokenUrl;
    }

    public String customerType() {
        return customerType;
    }

    public String dailyChartPath() {
        return dailyChartPath;
    }

    public String dailyChartTrId() {
        return dailyChartTrId;
    }

    public String dailyChartMarketDivCode() {
        return dailyChartMarketDivCode;
    }

    public String dailyChartAdjustedPrice() {
        return dailyChartAdjustedPrice;
    }
}
