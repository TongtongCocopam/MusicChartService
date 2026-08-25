package kongju.musicchartservice.domain.infrastructure.scraping;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import static org.assertj.core.api.Assertions.assertThat;

import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;


public class MelonScraperTest {
    private MelonScraper melonScraper;

    @BeforeEach
    void setup() {
        melonScraper = new MelonScraper(WebClient.builder());
    }

    @Test
    @DisplayName("success : 멜론 차트와 앨범 정보를 수집한다")
    void melonScrapping_success() {

        String chartHtml = """
                <table>
                    <tr class="lst50" data-song-no="12345">
                        <td>
                            <span class="rank">1</span>
                
                            <div class="rank01">
                                <span>
                                    <a>Test Song</a>
                                </span>
                            </div>
                
                            <div class="rank02">
                                <a>Test Artist</a>
                            </div>
                
                            <div class="rank03">
                                <a href="javascript:melon.link.goAlbumDetail('999')">
                                    Test Album
                                </a>
                            </div>
                        </td>
                    </tr>
                </table>
                """;

        String albumHtml = """
                <div class="meta">
                    <dl class="list">
                        <dt>발매사</dt>
                        <dd>Test Publisher</dd>
                
                        <dt>기획사</dt>
                        <dd>Test Agency</dd>
                    </dl>
                </div>
                """;

        // 기존 client요청 대체
        ExchangeFunction exchangeFunction = request -> {

            String path = request.url().getPath();

            // 곡 정보 요청
            if (path.contains("chart/index.htm")) {
                return Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(
                                        HttpHeaders.CONTENT_TYPE,
                                        MediaType.TEXT_HTML_VALUE
                                )
                                .body(chartHtml)
                                .build()
                );
            }
            // 앨범 정보 요청
            if (path.contains("album/detail.htm")) {
                return Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(
                                        HttpHeaders.CONTENT_TYPE,
                                        MediaType.TEXT_HTML_VALUE
                                )
                                .body(albumHtml)
                                .build()
                );
            }

            return Mono.just(
                    ClientResponse.create(HttpStatus.NOT_FOUND)
                            .build()
            );
        };

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(exchangeFunction);

        MelonScraper scraper = new MelonScraper(builder);

        // 모노 안에 객체를 받아 확인
        StepVerifier.create(scraper.melonScrapping())
                .assertNext(result -> {
                    assertThat(result).hasSize(1);

                    MusicScrapingContext music = result.get(0);

                    assertThat(music.getRanking()).isEqualTo(1);
                    assertThat(music.getTitle()).isEqualTo("Test Song");
                    assertThat(music.getArtist()).isEqualTo("Test Artist");
                    assertThat(music.getAlbum()).isEqualTo("Test Album");
                    assertThat(music.getSongId()).isEqualTo("12345");

                    assertThat(music.getPublisher()).isEqualTo("Test Publisher");
                    assertThat(music.getAgency()).isEqualTo("Test Agency");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fail : 멜론 차트 요청이 실패하면 빈 리스트를 반환")
    void melonScrapping_차트요청실패시_빈리스트반환() {

        ExchangeFunction exchangeFunction = request ->
                Mono.just(
                        ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                                .build()
                );

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(exchangeFunction);

        MelonScraper scraper = new MelonScraper(builder);

        StepVerifier.create(scraper.melonScrapping())
                .assertNext(result -> {
                    assertThat(result).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("success : albumId가 없으면 앨범 상세 요청 없이 곡 정보를 반환")
    void melonScrapping_albumId가없으면_앨범상세요청을하지않는다() {

        String chartHtml = """
            <table>
                <tr class="lst50" data-song-no="12345">
                    <td>
                        <span class="rank">1</span>

                        <div class="rank01">
                            <span>
                                <a>Test Song</a>
                            </span>
                        </div>

                        <div class="rank02">
                            <a>Test Artist</a>
                        </div>

                        <div class="rank03">
                            <a>Test Album</a>
                        </div>
                    </td>
                </tr>
            </table>
            """;

        AtomicInteger requestCount = new AtomicInteger();

        ExchangeFunction exchangeFunction = request -> {
            // http요청이 몇 번 발생했는지 카운트
            requestCount.incrementAndGet();

            return Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                            .header(
                                    HttpHeaders.CONTENT_TYPE,
                                    MediaType.TEXT_HTML_VALUE
                            )
                            .body(chartHtml)
                            .build()
            );
        };

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(exchangeFunction);

        MelonScraper scraper = new MelonScraper(builder);

        StepVerifier.create(scraper.melonScrapping())
                .assertNext(result -> {
                    assertThat(result).hasSize(1);

                    MusicScrapingContext music = result.get(0);

                    assertThat(music.getTitle()).isEqualTo("Test Song");
                    assertThat(music.getArtist()).isEqualTo("Test Artist");
                    assertThat(music.getAlbum()).isEqualTo("Test Album");

                    // href에 숫자가 없으므로 albumId는 빈 문자열
                    assertThat(music.getAlbumId()).isEmpty();
                })
                .verifyComplete();

        // 차트 요청 딱 1번만 발생
        // 앨범 상세 요청은 발생하지 않아야 함
        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("success : 일부 곡 파싱에 실패해도 정상 곡은 반환")
    void melonScrapping_일부곡파싱실패시_정상곡은반환한다() {

        String chartHtml = """
            <table>
                <!-- 정상 row -->
                <tr class="lst50" data-song-no="12345">
                    <td>
                        <span class="rank">1</span>

                        <div class="rank01">
                            <span>
                                <a>Test Song</a>
                            </span>
                        </div>

                        <div class="rank02">
                            <a>Test Artist</a>
                        </div>

                        <div class="rank03">
                            <a>Test Album</a>
                        </div>
                    </td>
                </tr>

                <!-- 비정상 row : rank가 숫자가 아님 -->
                <tr class="lst50" data-song-no="99999">
                    <td>
                        <span class="rank">INVALID</span>

                        <div class="rank01">
                            <span>
                                <a>Broken Song</a>
                            </span>
                        </div>

                        <div class="rank02">
                            <a>Broken Artist</a>
                        </div>

                        <div class="rank03">
                            <a>Broken Album</a>
                        </div>
                    </td>
                </tr>
            </table>
            """;

        ExchangeFunction exchangeFunction = request ->
                Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(
                                        HttpHeaders.CONTENT_TYPE,
                                        MediaType.TEXT_HTML_VALUE
                                )
                                .body(chartHtml)
                                .build()
                );

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(exchangeFunction);

        MelonScraper scraper = new MelonScraper(builder);

        StepVerifier.create(scraper.melonScrapping())
                .assertNext(result -> {
                    assertThat(result).hasSize(1);

                    MusicScrapingContext music = result.get(0);

                    assertThat(music.getRanking()).isEqualTo(1);
                    assertThat(music.getTitle()).isEqualTo("Test Song");
                    assertThat(music.getArtist()).isEqualTo("Test Artist");
                    assertThat(music.getSongId()).isEqualTo("12345");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("success : 앨범 상세 정보 파싱에 실패해도 곡 정보는 반환")
    void melonScrapping_앨범상세파싱실패시_곡정보는반환한다() {

        String chartHtml = """
            <table>
                <tr class="lst50" data-song-no="12345">
                    <td>
                        <span class="rank">1</span>

                        <div class="rank01">
                            <span>
                                <a>Test Song</a>
                            </span>
                        </div>

                        <div class="rank02">
                            <a>Test Artist</a>
                        </div>

                        <div class="rank03">
                            <a href="javascript:melon.link.goAlbumDetail('999')">
                                Test Album
                            </a>
                        </div>
                    </td>
                </tr>
            </table>
            """;

        // dt는 존재하지만 대응되는 dd가 없음
        // dds.get(i)에서 예외 발생
        String brokenAlbumHtml = """
            <div class="meta">
                <dl class="list">
                    <dt>발매사</dt>
                </dl>
            </div>
            """;

        ExchangeFunction exchangeFunction = request -> {

            String path = request.url().getPath();

            if (path.contains("chart/index.htm")) {
                return Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(
                                        HttpHeaders.CONTENT_TYPE,
                                        MediaType.TEXT_HTML_VALUE
                                )
                                .body(chartHtml)
                                .build()
                );
            }

            if (path.contains("album/detail.htm")) {
                return Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .header(
                                        HttpHeaders.CONTENT_TYPE,
                                        MediaType.TEXT_HTML_VALUE
                                )
                                .body(brokenAlbumHtml)
                                .build()
                );
            }

            return Mono.just(
                    ClientResponse.create(HttpStatus.NOT_FOUND)
                            .build()
            );
        };

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(exchangeFunction);

        MelonScraper scraper = new MelonScraper(builder);

        StepVerifier.create(scraper.melonScrapping())
                .assertNext(result -> {
                    assertThat(result).hasSize(1);

                    MusicScrapingContext music = result.get(0);

                    // 앨범 상세 파싱은 실패했지만
                    // 기존 차트 정보는 살아 있어야 함
                    assertThat(music.getRanking()).isEqualTo(1);
                    assertThat(music.getTitle()).isEqualTo("Test Song");
                    assertThat(music.getArtist()).isEqualTo("Test Artist");
                    assertThat(music.getAlbum()).isEqualTo("Test Album");
                    assertThat(music.getSongId()).isEqualTo("12345");

                    // 상세 정보는 채워지지 않음
                    assertThat(music.getPublisher()).isNull();
                    assertThat(music.getAgency()).isNull();
                })
                .verifyComplete();
    }
}
