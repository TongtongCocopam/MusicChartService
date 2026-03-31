package kongju.musicchartservice.domain.infrastructure.scraping;

import java.util.List;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import reactor.core.publisher.Mono;


public interface MusicScraper {
    Mono<List<MusicScrapingContext>> scrape();

    Vendor getScraperName();
}
