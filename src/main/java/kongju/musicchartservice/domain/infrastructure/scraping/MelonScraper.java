package kongju.musicchartservice.domain.infrastructure.scraping;

import java.util.ArrayList;
import java.util.List;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import kongju.musicchartservice.global.error.exception.ScrapingFailedException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
public class MelonScraper implements MusicScraper{
    private final WebClient client;

    public MelonScraper(WebClient.Builder webClientBuilder) {
        this.client = webClientBuilder.clone()
                .baseUrl("https://www.melon.com/")
                .build();
    }

    /**
     * 지니 스크래핑 로직
     *
     * @return 곡, 앨범 정보를 담은 객체 리스트 반환
     */
    public List<MusicScrapingContext> melonScrapping() {
        log.info("멜론 차트 수집 시작");
        List<MusicScrapingContext> results = new ArrayList<>();
        String url = "chart/index.htm";

        try {
            String html = getHtml(url);
            results = parseMelonHtml(html);

        } catch (Exception e) {
            log.error("{} 페이지 수집 중 에러 : {} ", e.getMessage());
        }

        return getAlbumInfo(results);
    }

    /**
     * get요청을 해서 html데이터를 가져오는 메서드
     *
     * @param url get요청할 주소
     * @return html정보 반환
     */
    private String getHtml(String url) {
        return client.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("멜론 에러 상태 코드: {}, 헤더: {}",
                            response.statusCode(),
                            response.headers().asHttpHeaders());
                    return Mono.error(new ScrapingFailedException());
                })
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 곡 정보 파싱
     *
     * @param html 파싱할 데이터
     * @return 곡 정보를 담은 객체 반환
     */
    private List<MusicScrapingContext> parseMelonHtml(String html) {
        List<MusicScrapingContext> result = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements rows = doc.select("tr.lst50, tr.lst100");

        for (Element row : rows) {
            try {
                int ranking = Integer.parseInt(row.select("span.rank").text().trim());
                String title = row.select("div.rank01 span a").text().trim();

                String artist = row.select("div.rank02 a").first().text().trim();

                String album = row.select("div.rank03 a").text().trim();

                String songId = row.attr("data-song-no");

                String albumHref = row.select("div.rank03 a").attr("href");
                String albumId = albumHref.replaceAll("[^0-9]", "");

                result.add(MusicScrapingContext.builder()
                        .title(title)
                        .artist(artist)
                        .album(album)
                        .albumId(albumId)
                        .songId(songId)
                        .ranking(ranking)
                        .build());

            } catch (Exception e) {
                log.error("데이터 파싱 에러: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 앨범 주소 get요청
     *
     * @param results 앨범 정보를 담을 객체
     * @return 앨범 정보까지 담은 객체 반환
     */
    public List<MusicScrapingContext> getAlbumInfo(List<MusicScrapingContext> results) {
        for (MusicScrapingContext context : results) {
            if (context.getAlbumId() != null && !context.getAlbumId().isEmpty()) {
                String url = "album/detail.htm?albumId=" + context.getAlbumId();
                String html = getHtml(url);
                parseAlbumInfo(html, context);
            }
        }

        return results;
    }

    /**
     * 앨범 정보 파싱
     * @param html 파싱할 앨범 주소
     * @param context 데이터를 넣을 객체
     */
    private void parseAlbumInfo(String html, MusicScrapingContext context) {
        Document doc = Jsoup.parse(html);
        Elements row = doc.select("div.meta dl.list");
        try {
            Elements dts = row.select("dt");
            Elements dds = row.select("dd");

            for (int i = 0; i < dts.size(); i++) {
                String label = dts.get(i).text().trim();
                String value = dds.get(i).text().trim();

                if ("발매사".equals(label)) {
                    context.setPublisher(value);
                } else if ("기획사".equals(label)) {
                    context.setAgency(value);
                }
            }

        } catch (Exception e) {
            log.error("앨범 정보 파싱 에러: {}", e.getMessage());
        }
    }
//
//    @Override
//    public List<MusicScrapingContext> scrape() {
//        return melonScrapping();
//    }

    @Override
    public Mono<List<MusicScrapingContext>> scrape() {
        // 1. 일단 기존 로직을 실행하되, 결과를 Mono라는 봉투에 담아서 줍니다.
        // 2. 나중에 진짜 비동기로 고칠 때 이 부분을 Flux/flatMap으로 바꿀 거예요.
        return Mono.fromCallable(() -> melonScrapping())
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Vendor getScraperName() {
        return Vendor.MELON;
    }
}
