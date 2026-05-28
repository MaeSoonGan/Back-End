package com.mock.maesoongan.realtimequoteingestor.stock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockMasterFileParserTest {

    @Test
    void parsesKisMasterFileLine() throws IOException {
        Path file = Files.createTempFile("kis-stock-master", ".mst");
        String line = "005930   KR7005930003SAMSUNG                                 ST1002700130000";
        Files.writeString(file, line, StockMasterFileParser.KIS_MST_CHARSET);

        List<StockMetadata> metadata = new StockMasterFileParser().parse(file, "KOSPI");

        assertEquals(1, metadata.size());
        assertEquals("005930", metadata.get(0).code());
        assertEquals("KR7005930003", metadata.get(0).standardCode());
        assertEquals("SAMSUNG", metadata.get(0).name());
        assertEquals("KOSPI", metadata.get(0).market());
    }
}
