package com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KisProperties {

    private final String appKey;
    private final String appSecret;
    private final String approvalUrl;
    private final String websocketUrl;
    private final String priceTrId;
    private final String orderbookTrId;
    private final String indexTrId;
    private final String customerType;

    public KisProperties(
            @Value("${kis.app-key:}") String appKey,
            @Value("${kis.app-secret:}") String appSecret,
            @Value("${kis.approval-url:https://openapivts.koreainvestment.com:29443/oauth2/Approval}") String approvalUrl,
            @Value("${kis.websocket-url:ws://ops.koreainvestment.com:31000}") String websocketUrl,
            @Value("${kis.price-tr-id:H0STCNT0}") String priceTrId,
            @Value("${kis.orderbook-tr-id:H0STASP0}") String orderbookTrId,
            @Value("${kis.index-tr-id:H0UPCNT0}") String indexTrId,
            @Value("${kis.customer-type:P}") String customerType
    ) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.approvalUrl = approvalUrl;
        this.websocketUrl = websocketUrl;
        this.priceTrId = priceTrId;
        this.orderbookTrId = orderbookTrId;
        this.indexTrId = indexTrId;
        this.customerType = customerType;
    }

    public String appKey() {
        return appKey;
    }

    public String appSecret() {
        return appSecret;
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

    public String customerType() {
        return customerType;
    }
}
