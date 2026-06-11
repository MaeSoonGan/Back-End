package com.mock.maesoongan.realtimequoteingestor.market.event;

/**
 * 실시간 수신 복구 상태 변경 이벤트.
 * recovering=true: KIS 등록 한도 초과로 재연결(복구) 시작
 * recovering=false: 재연결 완료(복구 종료)
 */
public record MarketRealtimeStatusEvent(boolean recovering) {
}
