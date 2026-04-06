package kongju.musicchartservice.domain.infrastructure.scraping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import kongju.musicchartservice.global.error.exception.ScrapingFailedException;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import reactor.core.scheduler.Schedulers;


@Component
@Slf4j
public class GenieScraper implements MusicScraper {

    private final WebClient client;

    public GenieScraper(WebClient.Builder webClientBuilder) {
        this.client = webClientBuilder.clone()
                //https://www.genie.co.kr
                .baseUrl("https://www.genie.co.kr")
                .build();
    }

    /**
     * 지니 스크래핑 로직
     *
     * @return 곡, 앨범 정보를 담은 객체 리스트 반환
     */
    public Mono<List<MusicScrapingContext>> genieScrapping() {
        log.info("지니 차트 수집 시작");

        LocalDateTime now = LocalDateTime.now();
        String ymd = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String hh = now.format(DateTimeFormatter.ofPattern("HH"));

        // page 1~4까지
        return Flux.range(1, 4)
                .flatMap(pg -> {
                    String url = String.format("/chart/top200?ditc=D&ymd=%s&hh=%s&rtm=Y&pg=%d", ymd, hh, pg);
                    log.info("지니 차트 수집 중: {} 페이지", pg);

                    // html가져오기 String Mono객체라 flatMap사용 가능
                    return getHtml(url)
                            // 가져온 Mono<String> parseGenieHtml로 파싱, List<MusicScrapingContext> 4개 반환
                            .flatMap(this::parseGenieHtml)
                            // 에러 던지기
                            .onErrorResume(e -> {
                                log.error("{} 페이지 수집 중 에러 : {} ", pg, e.getMessage());
                                return Mono.just(Collections.emptyList());
                            });
                })
                // 하나로 합침 4개 큰 리스트 내부를 하나씩 꺼내기
                .flatMapIterable(list -> list)
                // 하나의 리스트로 합침
                .collectList()
                // 해당 리스트 앨범 정보 가져오기
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
                    log.error("지니 에러 상태 코드: {}, 헤더: {}",
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
    private Mono<List<MusicScrapingContext>> parseGenieHtml(String html) {
        return Mono.fromCallable(() -> {
                    List<MusicScrapingContext> result = new ArrayList<>();
                    Document doc = Jsoup.parse(html);

                    Elements rows = doc.select("tr.list");

                    for (Element row : rows) {
                        try {
                            String rankText = row.select("td.number").get(0).ownText().trim();
                            int ranking = Integer.parseInt(rankText);
                            String title = row.select("a.title").text().trim();

                            String artist = row.select("a.artist").text().trim();

                            String album = row.select("a.albumtitle").text().trim();

                            String songId = row.attr("songid");

                            String onclickValue = row.select("a.albumtitle").attr("onclick");
                            String albumId = onclickValue.replaceAll("[^0-9]", "");

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
                    // 앨범 없으면 통과
                    if (context.getAlbumId() == null || context.getAlbumId().isEmpty()) {
                        return Mono.just(context);
                    }

                    // https://www.genie.co.kr/detail/albumInfo?axnm=
                    String url = "/detail/albumInfo?axnm=" + context.getAlbumId();

                    // 주소 가지고 파싱
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
                    Elements rows = doc.select("ul.info-data li");
                    for (Element row : rows) {
                        try {
                            String attr = row.select("span.attr img").attr("alt");
                            String value = row.select("span.value").text().trim();
                            if ("기획사".equals(attr)) {
                                context.setAgency(value);
                            } else if ("발매사".equals(attr)) {
                                context.setPublisher(value);
                            }
                        } catch (Exception e) {
                            log.error("앨범 정보 파싱 에러: {}", e.getMessage());
                        }
                    }
                    //씁 리턴 값 수정 필요
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public Mono<List<MusicScrapingContext>> scrape() {
        return genieScrapping();
    }

    @Override
    public Vendor getScraperName() {
        return Vendor.GENIE;
    }
}
