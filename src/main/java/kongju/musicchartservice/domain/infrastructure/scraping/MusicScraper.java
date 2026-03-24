package kongju.musicchartservice.domain.infrastructure.scraping;

import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;

import java.util.List;

public interface MusicScraper {
    List<MusicScrapingContext> scrape();

    String getScraperName();
}
