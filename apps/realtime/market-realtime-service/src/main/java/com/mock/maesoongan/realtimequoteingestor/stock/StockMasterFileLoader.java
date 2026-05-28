package com.mock.maesoongan.realtimequoteingestor.stock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class StockMasterFileLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockMasterFileLoader.class);

    private final boolean enabled;
    private final boolean failFast;
    private final String kospiPath;
    private final String kosdaqPath;
    private final StockMasterFileParser parser;
    private final StockMetadataCache cache;

    public StockMasterFileLoader(
            @Value("${stock.master.load-enabled:false}") boolean enabled,
            @Value("${stock.master.fail-fast:false}") boolean failFast,
            @Value("${stock.master.kospi-path:}") String kospiPath,
            @Value("${stock.master.kosdaq-path:}") String kosdaqPath,
            StockMasterFileParser parser,
            StockMetadataCache cache
    ) {
        this.enabled = enabled;
        this.failFast = failFast;
        this.kospiPath = kospiPath;
        this.kosdaqPath = kosdaqPath;
        this.parser = parser;
        this.cache = cache;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        try {
            List<StockMetadata> kospiMetadata = parseIfExists(kospiPath, "KOSPI");
            List<StockMetadata> kosdaqMetadata = parseIfExists(kosdaqPath, "KOSDAQ");
            List<StockMetadata> metadata = new ArrayList<>();
            metadata.addAll(kospiMetadata);
            metadata.addAll(kosdaqMetadata);

            if (metadata.isEmpty()) {
                log.warn("Stock master loading is enabled but no metadata was parsed.");
                return;
            }

            StockMasterCacheStatus status = new StockMasterCacheStatus(
                    metadata.size(),
                    kospiMetadata.size(),
                    kosdaqMetadata.size(),
                    LocalDateTime.now()
            );

            cache.saveAll(metadata, status);
            log.info(
                    "Loaded stock metadata into Redis. totalCount={}, kospiCount={}, kosdaqCount={}",
                    status.totalCount(),
                    status.kospiCount(),
                    status.kosdaqCount()
            );
        } catch (RuntimeException exception) {
            if (failFast) {
                throw exception;
            }
            log.error("Failed to load stock metadata into Redis. Service startup will continue.", exception);
        }
    }

    private List<StockMetadata> parseIfExists(String filePath, String market) {
        if (!StringUtils.hasText(filePath)) {
            return List.of();
        }
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            log.warn("Stock master file does not exist. market={}, path={}", market, filePath);
            return List.of();
        }
        return parser.parse(path, market);
    }
}
