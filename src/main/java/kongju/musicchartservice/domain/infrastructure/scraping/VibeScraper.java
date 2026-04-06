package kongju.musicchartservice.domain.infrastructure.scraping;

import java.util.List;

import kongju.musicchartservice.domain.Music.dto.vibeScrapingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;


@Component
@Slf4j
public class VibeScraper implements MusicScraper {

    private final WebClient client;

    public VibeScraper(WebClient.Builder webClientBuilder) {
        this.client = webClientBuilder.clone()
                .baseUrl("https://apis.naver.com/vibeWeb/musicapiweb/")
                .build();
    }


    /**
     * 바이브 스크래핑 로직
     *
     * @return 곡, 앨범 정보를 담은 객체 리스트 반환
     */
    public Mono<List<MusicScrapingContext>> vibeScrapping() {
        log.info("바이브 차트 수집 시작");
        String url = "vibe/v1/chart/track/total?start=1&display=100";
        // url을 가지고 json객체 가져오기
        return client.get()
                .uri(url)
                .header("Referer", "https://vibe.naver.com/chart/top100")
                .header("Accept", "application/json, text/plain, */*")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(root -> {
                    JsonNode tracks = root.at("/response/result/chart/items/tracks");

                    ObjectMapper mapper = new ObjectMapper();
                    List<vibeScrapingContext> vibeList = mapper.convertValue(
                            tracks,
                            new TypeReference<List<vibeScrapingContext>>() {
                            }
                    );

                    return vibeList.stream()
                            .map(this::convertToMusic)
                            .toList();
                });
    }

    public MusicScrapingContext convertToMusic(vibeScrapingContext vibe) {

        String artist = (vibe.getArtists() == null) ? "Unknown" : getValueOrDefault(vibe.getArtists().get(0).getArtistName());
        String album = (vibe.getAlbum() == null) ? "Unknown" : getValueOrDefault(vibe.getAlbum().getAlbumTitle());
        String agency = (vibe.getAlbum() == null) ? "Unknown" : getValueOrDefault(vibe.getAlbum().getAgencyName());
        String publisher = (vibe.getAlbum() == null) ? "Unknown" : getValueOrDefault(vibe.getAlbum().getProductionName());

        return MusicScrapingContext.builder()
                .ranking(vibe.getRank().getCurrentRank())
                .title(vibe.getTrackTitle())
                .artist(artist)
                .songId(vibe.getTrackId())
                .album(album)
                .publisher(publisher)
                .agency(agency)
                .build();
    }

    private String getValueOrDefault(String value) {
        return (value == null || value.trim().isEmpty()) ? "Unknown" : value;
    }

    @Override
    public Mono<List<MusicScrapingContext>> scrape() {
        return vibeScrapping();
    }

    @Override
    public Vendor getScraperName() {
        return Vendor.VIBE;
    }
}
