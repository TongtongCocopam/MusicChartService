package kongju.musicchartservice.domain.service;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.entity.MusicDetail;
import kongju.musicchartservice.domain.Music.entity.MusicSummary;
import kongju.musicchartservice.domain.Music.dto.MusicSummaryCache;
import kongju.musicchartservice.domain.Music.dto.MusicInfoResponse;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import kongju.musicchartservice.domain.Music.service.MusicChartService;
import kongju.musicchartservice.domain.infrastructure.scraping.MusicScraper;
import kongju.musicchartservice.domain.Music.repository.MusicDetailRepository;
import kongju.musicchartservice.domain.Music.repository.MusicSummaryRepository;

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
