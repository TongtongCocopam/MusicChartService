package kongju.musicchartservice.domain.infrastructure.scraping;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.test.StepVerifier;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import static org.assertj.core.api.Assertions.assertThat;

import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;


public class genieScraperTest {
    @Test
    @DisplayName("success : 지니 차트 4개 페이지의 데이터를 수집해 하나의 리스트로 반환한다")
    void genieScrapping_4개_페이지_정상수집() {

        ExchangeFunction exchangeFunction = request -> {
            URI url = request.url();
            String path = url.getPath();

            // 차트 페이지 요청
            if (path.contains("/chart/top200")) {
                // 요청 URL에서 pg 값 확인
                String query = url.getQuery();

                int page;
                if (query.contains("pg=1")) {
                    page = 1;
                } else if (query.contains("pg=2")) {
                    page = 2;
                } else if (query.contains("pg=3")) {
                    page = 3;
                } else {
                    page = 4;
                }

                String chartHtml = """
                        <table>
                            <tr class="list" songid="%d">
                                <td class="number">%d</td>
                        
                                <td>
                                    <a class="title">Test Song %d</a>
                        
                                    <a class="artist">
                                        Test Artist %d
                                    </a>
                        
                                    <a class="albumtitle"
                                       onclick="fnViewAlbumLayer('%d')">
                                        Test Album %d
                                    </a>
                                </td>
                            </tr>
                        </table>
                        """.formatted(page, page, page, page, page, page);

                ClientResponse response = ClientResponse.create(HttpStatus.OK)
                        .body(chartHtml)
                        .build();

                return Mono.just(response);
            }

            // 각 곡의 앨범 상세 페이지 요청
            if (path.contains("/detail/albumInfo")) {

                String albumHtml = """
                        <ul class="info-data">
                            <li>
                                <span class="attr">
                                    <img alt="기획사">
                                </span>
                        
                                <span class="value">
                                    Test Agency
                                </span>
                            </li>
                        
                            <li>
                                <span class="attr">
                                    <img alt="발매사">
                                </span>
                        
                                <span class="value">
                                    Test Publisher
                                </span>
                            </li>
                        </ul>
                        """;
                ClientResponse response = ClientResponse.create(HttpStatus.OK)
                        .body(albumHtml)
                        .build();

                return Mono.just(response);
            }

            return Mono.just(
                    ClientResponse.create(HttpStatus.NOT_FOUND)
                            .build()
            );
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        GenieScraper scraper = new GenieScraper(builder);

        StepVerifier.create(scraper.genieScrapping())
                .assertNext(result -> {
                    // 1페이지당 테스트 곡을 1개씩 넣었으므로 총 4곡
                    assertThat(result).hasSize(4);

                    assertThat(result)
                            .extracting(MusicScrapingContext::getTitle)
                            .containsExactlyInAnyOrder(
                                    "Test Song 1",
                                    "Test Song 2",
                                    "Test Song 3",
                                    "Test Song 4"
                            );

                    assertThat(result)
                            .allSatisfy(music -> {
                                assertThat(music.getAgency())
                                        .isEqualTo("Test Agency");

                                assertThat(music.getPublisher())
                                        .isEqualTo("Test Publisher");
                            });
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("success : 일부 페이지 수집에 실패해도 나머지 페이지 결과는 반환")
    void genieScrapping_일부_페이지_실패시_나머지_데이터_반환() {

        ExchangeFunction exchangeFunction = request -> {

            URI url = request.url();
            String path = url.getPath();
            String query = url.getQuery();

            if (path.contains("/chart/top200")) {
                // 2페이지 HTTP 500 응답
                if (query.contains("pg=2")) {

                    return Mono.just(
                            ClientResponse
                                    .create(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .build()
                    );
                }

                int page;

                if (query.contains("pg=1")) {
                    page = 1;
                } else if (query.contains("pg=3")) {
                    page = 3;
                } else {
                    page = 4;
                }

                // 그러면 albumId가 "" getAlbumInfo()에서 상세 페이지 요청을 생략
                String chartHtml = """
                        <table>
                            <tr class="list" songid="%d">
                                <td class="number">%d</td>
                        
                                <td>
                                    <a class="title">
                                        Test Song %d
                                    </a>
                        
                                    <a class="artist">
                                        Test Artist %d
                                    </a>
                        
                                    <a class="albumtitle">
                                        Test Album %d
                                    </a>
                                </td>
                            </tr>
                        </table>
                        """.formatted(page, page, page, page, page);

                return Mono.just(
                        ClientResponse
                                .create(HttpStatus.OK)
                                .body(chartHtml)
                                .build()
                );
            }

            return Mono.just(
                    ClientResponse
                            .create(HttpStatus.NOT_FOUND)
                            .build()
            );
        };

        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);

        GenieScraper scraper = new GenieScraper(builder);

        StepVerifier.create(scraper.genieScrapping())
                .assertNext(result -> {
                    // 결과는 3개
                    assertThat(result).hasSize(3);

                    assertThat(result)
                            .extracting(MusicScrapingContext::getTitle)
                            .containsExactlyInAnyOrder(
                                    "Test Song 1",
                                    "Test Song 3",
                                    "Test Song 4"
                            );

                    // 2페이지 데이터는 없음
                    assertThat(result)
                            .extracting(MusicScrapingContext::getTitle)
                            .doesNotContain("Test Song 2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("success : albumId가 없으면 앨범 상세 요청을 하지 않음")
    void genieScrapping_albumId가없으면_앨범_상세요청을_하지_않는다() {
        // 차트 요청 횟수
        AtomicInteger chartRequestCount = new AtomicInteger();
        // 앨범 상세 요청 횟수
        AtomicInteger albumRequestCount = new AtomicInteger();

        ExchangeFunction exchangeFunction = request -> {
            URI url = request.url();
            String path = url.getPath();

            if (path.contains("/chart/top200")) {
                chartRequestCount.incrementAndGet();

                String chartHtml = """
                        <table>
                            <tr class="list" songid="12345">
                        
                                <td class="number">
                                    1
                                </td>
                        
                                <td>
                                    <a class="title">
                                        Test Song
                                    </a>
                        
                                    <a class="artist">
                                        Test Artist
                                    </a>
                        
                                    <a class="albumtitle">
                                        Test Album
                                    </a>
                                </td>
                        
                            </tr>
                        </table>
                        """;

                ClientResponse response = ClientResponse.create(HttpStatus.OK)
                        .body(chartHtml)
                        .build();

                return Mono.just(response);
            }

            if (path.contains("/detail/albumInfo")) {
                albumRequestCount.incrementAndGet();

                return Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                                .body("<html></html>")
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

        GenieScraper scraper = new GenieScraper(builder);

        StepVerifier.create(scraper.genieScrapping())
                .assertNext(result -> {
                    // 최종 결과는 총 4개
                    assertThat(result).hasSize(4);
                    // 모든 곡의 albumId는 빈 문자열
                    assertThat(result)
                            .allSatisfy(music -> {
                                assertThat(music.getAlbumId())
                                        .isEmpty();
                            });
                })
                .verifyComplete();

        assertThat(chartRequestCount.get())
                .isEqualTo(4);

        assertThat(albumRequestCount.get())
                .isZero();
    }
}
