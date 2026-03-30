package kongju.musicchartservice.domain.infrastructure;

import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import kongju.musicchartservice.domain.infrastructure.scraping.GenieScraper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class genieScraperTest {
    @Autowired
    private GenieScraper genieScraper;

    @Test
    void genie_chart_get_test(){
        List<MusicScrapingContext> result = genieScraper.genieScrapping();
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        org.junit.jupiter.api.Assertions.assertFalse(result.isEmpty());
        System.out.println("### 결과 리스트 개수: " + result.size());
    }
}
