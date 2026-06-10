package com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KisProperties {

    private final String appKey;
    private final String appSecret;
    private final String baseUrl;
    private final String tokenUrl;
    private final String approvalUrl;
    private final String websocketUrl;
    private final String priceTrId;
    private final String orderbookTrId;
    private final String indexTrId;
    private final String htsTopViewTrId;
    private final String htsTopViewPath;
    private final String htsTopViewQuery;
    private final String customerType;

    public KisProperties(
            @Value("${kis.app-key:}") String appKey,
            @Value("${kis.app-secret:}") String appSecret,
            @Value("${kis.base-url:https://openapivts.koreainvestment.com:29443}") String baseUrl,
            @Value("${kis.token-url:}") String tokenUrl,
            @Value("${kis.approval-url:https://openapivts.koreainvestment.com:29443/oauth2/Approval}") String approvalUrl,
            @Value("${kis.websocket-url:ws://ops.koreainvestment.com:31000}") String websocketUrl,
            @Value("${kis.price-tr-id:H0STCNT0}") String priceTrId,
            @Value("${kis.orderbook-tr-id:H0STASP0}") String orderbookTrId,
            @Value("${kis.index-tr-id:H0UPCNT0}") String indexTrId,
            @Value("${kis.hts-top-view-tr-id:HHMCM000100C0}") String htsTopViewTrId,
            @Value("${kis.hts-top-view-path:/uapi/domestic-stock/v1/ranking/hts-top-view}") String htsTopViewPath,
            @Value("${kis.hts-top-view-query:FID_COND_MRKT_DIV_CODE=J&FID_COND_SCR_DIV_CODE=20171&FID_INPUT_ISCD=0000&FID_DIV_CLS_CODE=0&FID_BLNG_CLS_CODE=0&FID_TRGT_CLS_CODE=111111111&FID_TRGT_EXLS_CLS_CODE=0000000000}") String htsTopViewQuery,
            @Value("${kis.customer-type:P}") String customerType
    ) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.baseUrl = baseUrl;
        this.tokenUrl = tokenUrl;
        this.approvalUrl = approvalUrl;
        this.websocketUrl = websocketUrl;
        this.priceTrId = priceTrId;
        this.orderbookTrId = orderbookTrId;
        this.indexTrId = indexTrId;
        this.htsTopViewTrId = htsTopViewTrId;
        this.htsTopViewPath = htsTopViewPath;
        this.htsTopViewQuery = htsTopViewQuery;
        this.customerType = customerType;
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

    public String approvalUrl() {
        return approvalUrl;
    }

    public String websocketUrl() {
        return websocketUrl;
    }

    public String priceTrId() {
        return priceTrId;
    }

    public String orderbookTrId() {
        return orderbookTrId;
    }

    public String indexTrId() {
        return indexTrId;
    }

    public String htsTopViewTrId() {
        return htsTopViewTrId;
    }

    public String htsTopViewPath() {
        return htsTopViewPath;
    }

    public String htsTopViewQuery() {
        return htsTopViewQuery;
    }

    public String customerType() {
        return customerType;
    }
}
