package kongju.musicchartservice.domain.infrastructure.scraping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import kongju.musicchartservice.global.error.exception.ScrapingFailedException;


@Component
@Slf4j
public class VibeScraper implements MusicScraper {

    private final WebClient client;

    public VibeScraper(WebClient.Builder webClientBuilder) {
        this.client = webClientBuilder.clone()
                .baseUrl("https://vibe.naver.com/")
                .build();
    }


    /**
     * 지니 스크래핑 로직
     *
     * @return 곡, 앨범 정보를 담은 객체 리스트 반환
     */
    public Mono<List<MusicScrapingContext>> vibeScrapping() {
        log.info("바이브 차트 수집 시작");
        String url = "chart/total";

        return getHtml(url)
                .flatMap(this::parseVibeHtml)
                .onErrorResume(e -> {
                    log.error("페이지 수집 중 에러 : {} ", e.getMessage());
                    return Mono.just(Collections.emptyList());
                })
                .flatMap(this::getAlbumInfo);
    }

    /**
     * get요청을 해서 html데이터를 가져오는 메서드
     *
     * @param url get요청할 주소
     * @return html정보 반환
     */
    private Mono<String> getHtml(String url) {
        return client.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("바이브 에러 상태 코드: {}, 헤더: {}",
                            response.statusCode(),
                            response.headers().asHttpHeaders());
                    return Mono.error(new ScrapingFailedException());
                })
                .bodyToMono(String.class);
    }

    /**
     * 곡 정보 파싱
     *
     * @param html 파싱할 데이터
     * @return 곡 정보를 담은 객체 반환
     */
    private Mono<List<MusicScrapingContext>> parseVibeHtml(String html) {
        return Mono.fromCallable(() -> {
                    List<MusicScrapingContext> result = new ArrayList<>();
                    Document doc = Jsoup.parse(html);

                    Elements rows = doc.select("div.tracklist tbody tr");

                    for (Element row : rows) {
                        try {
                            int ranking = Integer.parseInt(row.select("span.rank").text().trim());
                            String title = row.select("a.link_text").attr("title");

                            String artist = row.select("div.artist_sub a.link_artist").text().trim();

                            String album = row.select("td.album a.link").text().trim();

                            String songId = row.select("a.link_text").attr("href").replaceAll("[^0-9]", "");

                            String albumId = row.select("td.album a.link").attr("href").replaceAll("[^0-9]", "");

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
                })
                .subscribeOn(Schedulers.boundedElastic());

    }

    /**
     * 앨범 주소 get요청
     *
     * @param results 앨범 정보를 담을 객체
     * @return 앨범 정보까지 담은 객체 반환
     */
    public Mono<List<MusicScrapingContext>> getAlbumInfo(List<MusicScrapingContext> results) {
        return Flux.fromIterable(results)
                .flatMap(context -> {
                    if (context.getAlbumId() == null || context.getAlbumId().isEmpty()) {
                        return Mono.just(context);
                    }

                    String url = "album/" + context.getAlbumId();
                    return getHtml(url)
                            .flatMap(html -> parseAlbumInfo(html, context))
                            .thenReturn(context);
                })
                .collectList();
    }

    /**
     * 앨범 정보 파싱
     *
     * @param html    파싱할 앨범 주소
     * @param context 데이터를 넣을 객체
     */
    private Mono<Void> parseAlbumInfo(String html, MusicScrapingContext context) {
        return Mono.fromCallable(() -> {
                    Document doc = Jsoup.parse(html);
                    Elements rows = doc.select("div.ly_company_area tbody tr");
                    for (Element row : rows) {
                        try {
                            String th = row.select("th").text().trim();
                            String td = row.select("td").text().trim();

                            if ("발매사".equals(th)) {
                                context.setPublisher(td);
                            } else if ("기획사".equals(th)) {
                                context.setAgency(td);
                            }
                        } catch (Exception e) {
                            log.error("앨범 정보 파싱 에러: {}", e.getMessage());
                        }
                    }
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();

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
