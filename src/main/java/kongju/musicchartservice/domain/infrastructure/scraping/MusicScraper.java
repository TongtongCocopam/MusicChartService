package kongju.musicchartservice.domain.infrastructure.scraping;

import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MusicScraper {
    Mono<List<MusicScrapingContext>> scrape();

    String getScraperName();
}
