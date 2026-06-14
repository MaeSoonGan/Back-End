package com.mock.maesoongan.realtimequoteingestor.market.adapter.kis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisAccessTokenClient;
import com.mock.maesoongan.realtimequoteingestor.quote.adapter.kis.KisProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KisOrderbookClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KisOrderbookClient client = new KisOrderbookClient(
            mock(KisProperties.class),
            mock(KisAccessTokenClient.class),
            objectMapper
    );

    @Test
    void parseSnapshotReadsObjectOutput1() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output1": {
                    "hts_kor_isnm": "Samsung",
                    "askp1": "322500",
                    "bidp1": "322000",
                    "askp_rsqn1": "113425",
                    "bidp_rsqn1": "50496"
                  }
                }
                """);

        Optional<KisOrderbookClient.KisOrderbookSnapshot> snapshot = client.parseSnapshot("005930", root);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().asks().get(0).price()).isEqualByComparingTo("322500");
        assertThat(snapshot.get().asks().get(0).quantity()).isEqualTo(113425L);
        assertThat(snapshot.get().bids().get(0).price()).isEqualByComparingTo("322000");
        assertThat(snapshot.get().bids().get(0).quantity()).isEqualTo(50496L);
    }

    @Test
    void parseSnapshotReadsArrayOutput1() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output1": [{
                    "hts_kor_isnm": "Samsung",
                    "askp1": "322500",
                    "bidp1": "322000",
                    "askp_rsqn1": "113425",
                    "bidp_rsqn1": "50496"
                  }]
                }
                """);

        Optional<KisOrderbookClient.KisOrderbookSnapshot> snapshot = client.parseSnapshot("005930", root);

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().asks().get(0).price()).isEqualByComparingTo("322500");
        assertThat(snapshot.get().bids().get(0).price()).isEqualByComparingTo("322000");
    }
}
