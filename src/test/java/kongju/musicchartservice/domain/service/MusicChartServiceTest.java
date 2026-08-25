package kongju.musicchartservice.domain.service;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.dto.*;
import kongju.musicchartservice.domain.Music.entity.MusicDetail;
import kongju.musicchartservice.domain.Music.entity.MusicSummary;
import kongju.musicchartservice.domain.Music.service.MusicChartService;
import kongju.musicchartservice.domain.infrastructure.scraping.MusicScraper;
import kongju.musicchartservice.domain.Music.repository.MusicDetailRepository;
import kongju.musicchartservice.domain.Music.repository.MusicSummaryRepository;

import kongju.musicchartservice.global.error.exception.VendorNotFoundException;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import reactor.test.StepVerifier;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class MusicChartServiceTest {
    @Mock
    private MusicSummaryRepository musicSummaryRepository;
    @Mock
    private MusicDetailRepository musicDetailRepository;

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    // Redis 값 조작용 가짜 객체
    @Mock
    private ReactiveValueOperations<String, Object> valueOperations;

    @Mock
    private MusicScraper musicScraper;

    @InjectMocks
    private MusicChartService musicChartService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("fail : vendor에 해당하는 scraper가 없으면 예외")
    void getSummary_scraper가없으면_예외가발생한다() {

        Map<Vendor, MusicScraper> scraperMap = new HashMap<>();
        scraperMap.put(Vendor.MELON, musicScraper);

        ReflectionTestUtils.setField(
                musicChartService,
                "scraperMap",
                scraperMap
        );

        Vendor vendor = Vendor.VIBE;
        String key = "summary:" + vendor;

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(key))
                .thenReturn(Mono.empty());

        when(musicSummaryRepository
                .existsByVendorAndCreatedAtAfter(any(), any()))
                .thenReturn(false);

        StepVerifier.create(musicChartService.getSummary(vendor))
                .expectError(VendorNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("fail : Redis 캐시 역직렬화 실패")
    void getSummary_잘못된캐시데이터면_예외가발생한다() {

        Vendor vendor = Vendor.MELON;
        String key = "summary:" + vendor;

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(key))
                .thenReturn(Mono.just("invalid-json"));

        StepVerifier.create(musicChartService.getSummary(vendor))
                .expectError(RuntimeException.class)
                .verify();

        verifyNoInteractions(musicSummaryRepository);
    }

    @Test
    @DisplayName("success : 상세 차트 캐시 조회")
    void getDetails_캐시가있으면_반환한다() {

        Vendor vendor = Vendor.MELON;
        String key = "detail:" + vendor;

        MusicAlbumInfoResponse mockResponse = mock(MusicAlbumInfoResponse.class);
        MusicAlbumInfoCache cache = MusicAlbumInfoCache.builder()
                .data(List.of(mockResponse))
                .build();

        String json = objectMapper.writeValueAsString(cache);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(Mono.just(json));

        StepVerifier.create(musicChartService.getDetails(vendor))
                .expectNextMatches(list -> !list.isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("success : Redis에 데이터가 이미 있는 경우")
    void getSummary_캐시에_데이터가_있으면_반환한다() {
        // 키 준비
        Vendor vendor = Vendor.MELON;
        String key = "summary:" + vendor;

        MusicInfoResponse mockResponse = MusicInfoResponse.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("test Album")
                .songId("test SongId")
                .build();

        MusicSummaryCache mockCache = MusicSummaryCache.builder()
                .data(List.of(mockResponse))
                .build();

        String cacheJson = objectMapper.writeValueAsString(mockCache);
        // opsForValue()가 가짜 객체를 넘김, 그 객체가 데이터를 뱉음
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 비동기라 Mono로 반환 객체를 감싸야 함
        when(valueOperations.get(key)).thenReturn(Mono.just(cacheJson));

        // 실행
        Mono<List<MusicInfoResponse>> result = musicChartService.getSummary(vendor);

        // 데이터 흐름 관찰
        //테스트 대상인 Mono나 Flux를 관찰
        StepVerifier.create(result)
                // 스트림에서 나온 데이터 확인
                .expectNextMatches(list -> {
                    return list.size() == 1 && list.get(0).title().equals("Test Song");
                })
                //성공적인 종료 신호
                .verifyComplete();

        // 테스트 중 해당 객체와 상호작용이 없었는지 조회
        verifyNoInteractions(musicSummaryRepository);
        verifyNoInteractions(musicDetailRepository);
    }

    @Test
    @DisplayName("success : Redis에 데이터가 없지만 DB에 있는경우")
    void getSummary_캐시가_없고_DB가_유효하면_DB데이터를_캐싱후_반환한다() {
        // 키 준비
        Vendor vendor = Vendor.MELON;
        String key = "summary:" + vendor;
        MusicInfoResponse mockResponse = MusicInfoResponse.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("test Album")
                .songId("test SongId")
                .build();

        MusicSummaryCache mockCache = MusicSummaryCache.builder()
                .data(List.of(mockResponse))
                .build();

        String cacheJson = objectMapper.writeValueAsString(mockCache);

        // redis empty
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(cacheJson));

        // DB 데이터 있음
        when(musicSummaryRepository.existsByVendorAndCreatedAtAfter(any(), any())).thenReturn(true);

        MusicDetail detail = MusicDetail.builder()
                .agency("Test Agency")
                .publisher("Test Publisher")
                .build();

        MusicSummary summary = MusicSummary.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("test Album")
                .songId("test1234")
                .musicDetail(detail)
                .vendor(vendor)
                .build();

        List<MusicSummary> mockSummaries = List.of(summary);
        when(musicSummaryRepository.findByVendorWithDetail(vendor))
                .thenReturn(mockSummaries);

        when(valueOperations.set(anyString(), any(), any(Duration.class)))
                .thenReturn(Mono.just(true));

        // 실행
        Mono<List<MusicInfoResponse>> result = musicChartService.getSummary(vendor)
                .doOnNext(list -> System.out.println("받은 데이터 크기: " + list.size()));

        // 데이터 흐름 관찰
        //테스트 대상인 Mono나 Flux를 관찰
        StepVerifier.create(result)
                // 스트림에서 나온 데이터 확인
                .expectNextMatches(list -> list.get(0).album().equals("test Album"))
                //성공적인 종료 신호
                .verifyComplete();
    }

    @Test
    @DisplayName("success : 락 획득 후 캐시가 생성되어 있으면 스크래핑하지 않는다")
    void getSummary_락획득후_캐시가있으면_스크래핑하지않는다() {

        Vendor vendor = Vendor.MELON;
        String key = "summary:" + vendor;
        String lockKey = "lock:" + vendor;

        Map<Vendor, MusicScraper> scraperMap = new HashMap<>();
        scraperMap.put(vendor, musicScraper);
        ReflectionTestUtils.setField(musicChartService, "scraperMap", scraperMap);

        MusicInfoResponse response = MusicInfoResponse.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("Test Album")
                .songId("1")
                .build();

        String json = objectMapper.writeValueAsString(
                MusicSummaryCache.builder()
                        .data(List.of(response))
                        .build()
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 1차 캐시 MISS
        // 락 획득 후 Double Check HIT
        // checkDbAndLock 종료 후 최종 조회 HIT
        when(valueOperations.get(key))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(json))
                .thenReturn(Mono.just(json));

        when(musicSummaryRepository
                .existsByVendorAndCreatedAtAfter(any(), any()))
                .thenReturn(false);

        when(valueOperations.setIfAbsent(
                eq(lockKey),
                eq("LOCKED"),
                any(Duration.class)
        )).thenReturn(Mono.just(true));

        when(redisTemplate.delete(lockKey))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(musicChartService.getSummary(vendor))
                .assertNext(list ->
                        assertThat(list.get(0).title())
                                .isEqualTo("Test Song")
                )
                .verifyComplete();

        verify(musicScraper, never()).scrape();
    }

    @Test
    @DisplayName("success : 캐시와 DB데이터 둘 다 없는 경우")
    void getSummary_데이터가_전혀_없으면_스크래핑을_시도한다() {
        // 스크래퍼
        Map<Vendor, MusicScraper> scraperMap = new HashMap<>();
        scraperMap.put(Vendor.MELON, musicScraper);

        ReflectionTestUtils.setField(musicChartService, "scraperMap", scraperMap);

        // 키 준비
        Vendor vendor = Vendor.MELON;

        String key = "summary:" + vendor;
        String lockKey = "lock:" + vendor;

        MusicInfoResponse mockResponse = MusicInfoResponse.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("test Album")
                .songId("test SongId")
                .build();

        MusicSummaryCache mockCache = MusicSummaryCache.builder()
                .data(List.of(mockResponse))
                .build();

        String cacheJson = objectMapper.writeValueAsString(mockCache);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 1. 최초 캐시 조회
        // 2. 락 획득 후 캐시 재조회
        // 3. 스크래핑 완료 후 캐시 조회
        when(valueOperations.get(key))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(cacheJson));

        // checkDbAndLock
        // DB 데이터 없음
        when(musicSummaryRepository.existsByVendorAndCreatedAtAfter(any(), any())).thenReturn(false);

        // proceedToLock
        when(valueOperations.setIfAbsent(eq(lockKey), eq("LOCKED"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        List<MusicScrapingContext> mockContext = List.of(MusicScrapingContext.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("test Album")
                .songId("test SongId")
                .build());

        when(musicScraper.scrape()).thenReturn(Mono.just(mockContext));
        // refreshData
        when(valueOperations.set(anyString(), any(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        // 락 쓰고 삭제
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        //테스트 대상인 Mono나 Flux를 관찰
        StepVerifier.create(musicChartService.getSummary(vendor))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).album()).isEqualTo("test Album");
                })
                .verifyComplete();

        verify(musicScraper, times(1)).scrape();
        // Lock도 획득했는지 확인
        verify(valueOperations, times(1)).setIfAbsent(eq(lockKey), eq("LOCKED"), any(Duration.class));
    }
}
