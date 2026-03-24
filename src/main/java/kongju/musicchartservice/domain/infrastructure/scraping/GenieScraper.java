package kongju.musicchartservice.domain.infrastructure.scraping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import kongju.musicchartservice.global.error.exception.ScrapingFailedException;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;


@Component
@Slf4j
public class GenieScraper {

    private final WebClient client;

    public GenieScraper(WebClient.Builder webClientBuilder) {
        this.client = webClientBuilder.clone()
                .baseUrl("https://www.genie.co.kr")
                .build();
    }

    /**
     * 지니 스크래핑 로직
     *
     * @return 곡, 앨범 정보를 담은 객체 리스트 반환
     */
    public List<MusicScrapingContext> genieScrapping() {
        List<MusicScrapingContext> totalResults = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();
        String ymd = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String hh = now.format(DateTimeFormatter.ofPattern("HH"));

        for (int pg = 1; pg <= 4; pg++) {
            String url = String.format("/chart/top200?ditc=D&ymd=%s&hh=%s&rtm=Y&pg=%d", ymd, hh, pg);
//            https://www.genie.co.kr/chart/top200?ditc=D&ymd=20260324&hh=13&rtm=Y&pg=1
            String html = getHtml(url);
            List<MusicScrapingContext> results = parseGenieHtml(html);

            totalResults.addAll(results);
        }

        return getAlbumInfo(totalResults);
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
                    log.error("지니 에러 상태 코드: {}, 헤더: {}",
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
    private List<MusicScrapingContext> parseGenieHtml(String html) {
        List<MusicScrapingContext> result = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements rows = doc.select("table.list-wrap tbody tr.list");

        for (Element row : rows) {
            try {
                int ranking = Integer.parseInt(row.select("td.number").text().trim());
                String title = row.select("td.info a.title").text().trim();
                String artist = row.select("td.info a.artist").text().trim();
                String album = row.select("td.info a.albumtitle").text().trim();
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
                String url = "\"/detail/albumInfo?axnm=\"" + context.getAlbumId();
                String html = getHtml(url);
                parseAlbumInfo(html, context);
            }
        }

        return results;
    }

    private void parseAlbumInfo(String html, MusicScrapingContext context) {
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("ul.Info-data li");
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
    }

}
