package kongju.musicchartservice.domain.infrastructure.scraping;

import reactor.test.StepVerifier;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import static org.assertj.core.api.Assertions.assertThat;

import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;


public class VibeScraperTest {
    @Test
    @DisplayName("success : VIBE 차트 데이터를 조회하고 음원 정보로 변환")
    void vibeScrapping_정상조회() {

        String json = """
                {
                  "response": {
                    "result": {
                      "chart": {
                        "items": {
                          "tracks": [
                            {
                              "trackId": "12345",
                              "trackTitle": "Test Song",
                              "rank": {
                                "currentRank": 1
                              },
                              "artists": [
                                {
                                  "artistName": "Test Artist"
                                }
                              ],
                              "album": {
                                "albumTitle": "Test Album",
                                "agencyName": "Test Agency",
                                "productionName": "Test Publisher"
                              }
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(
                                HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE
                        )
                        .body(json)
                        .build()
        );

        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(exchangeFunction);

        VibeScraper scraper = new VibeScraper(builder);

        StepVerifier.create(scraper.vibeScrapping())
                .assertNext(result -> {
                    assertThat(result).hasSize(1);

                    MusicScrapingContext music = result.get(0);

                    assertThat(music.getRanking()).isEqualTo(1);
                    assertThat(music.getTitle()).isEqualTo("Test Song");
                    assertThat(music.getArtist()).isEqualTo("Test Artist");
                    assertThat(music.getAlbum()).isEqualTo("Test Album");
                    assertThat(music.getAgency()).isEqualTo("Test Agency");
                    assertThat(music.getPublisher()).isEqualTo("Test Publisher");
                    assertThat(music.getSongId()).isEqualTo("12345");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("success : VIBE 데이터의 선택 정보가 없으면 Unknown으로 변환")
    void vibeScrapping_정보가_없으면_Unknown으로_변환한다() {
        String json = """
                {
                  "response": {
                    "result": {
                      "chart": {
                        "items": {
                          "tracks": [
                            {
                              "trackId": "12345",
                              "trackTitle": "Test Song",
                              "rank": {
                                "currentRank": 1
                              },
                              "artists": null,
                              "album": null
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(
                                HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE
                        )
                        .body(json)
                        .build()
        );

        VibeScraper scraper = new VibeScraper(
                WebClient.builder()
                        .exchangeFunction(exchangeFunction)
        );

        StepVerifier.create(scraper.vibeScrapping())
                .assertNext(result -> {

                    MusicScrapingContext music = result.get(0);

                    assertThat(music.getArtist()).isEqualTo("Unknown");
                    assertThat(music.getAlbum()).isEqualTo("Unknown");
                    assertThat(music.getAgency()).isEqualTo("Unknown");
                    assertThat(music.getPublisher()).isEqualTo("Unknown");
                })
                .verifyComplete();
    }

}
