package com.mock.maesoongan.realtimequoteingestor.stock;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class StockMasterFileParser {

    static final Charset KIS_MST_CHARSET = Charset.forName("MS949");
    private static final int CODE_START = 0;
    private static final int CODE_END = 9;
    private static final int STANDARD_CODE_END = 21;
    private static final int NAME_END = 61;

    public List<StockMetadata> parse(Path path, String market) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            List<StockMetadata> metadata = new ArrayList<>();
            int lineStart = 0;
            for (int index = 0; index <= bytes.length; index++) {
                if (index == bytes.length || bytes[index] == '\n') {
                    int lineEnd = index;
                    if (lineEnd > lineStart && bytes[lineEnd - 1] == '\r') {
                        lineEnd--;
                    }
                    if (lineEnd - lineStart >= NAME_END) {
                        StockMetadata item = parseLine(bytes, lineStart, market);
                        if (!item.code().isBlank() && !item.name().isBlank()) {
                            metadata.add(item);
                        }
                    }
                    lineStart = index + 1;
                }
            }
            return metadata;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read stock master file: " + path, exception);
        }
    }

    private StockMetadata parseLine(byte[] bytes, int lineStart, String market) {
        return new StockMetadata(
                decode(bytes, lineStart + CODE_START, lineStart + CODE_END),
                decode(bytes, lineStart + CODE_END, lineStart + STANDARD_CODE_END),
                decode(bytes, lineStart + STANDARD_CODE_END, lineStart + NAME_END),
                market
        );
    }

    private String decode(byte[] bytes, int start, int end) {
        return new String(Arrays.copyOfRange(bytes, start, end), KIS_MST_CHARSET).trim();
    }
}
