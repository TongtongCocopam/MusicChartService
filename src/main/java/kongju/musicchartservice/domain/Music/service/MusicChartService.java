package kongju.musicchartservice.domain.Music.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import kongju.musicchartservice.domain.Music.repository.*;
import kongju.musicchartservice.domain.Music.entity.*;
import kongju.musicchartservice.global.error.exception.*;
import kongju.musicchartservice.domain.Music.dto.*;
import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.infrastructure.scraping.MusicScraper;
import tools.jackson.databind.ObjectMapper;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MusicChartService {
    private final MusicDetailRepository musicDetailRepository;
    private final MusicSummaryRepository musicSummaryRepository;

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private final List<MusicScraper> scrapers;
    private Map<Vendor, MusicScraper> scraperMap;
    private final ObjectMapper mapper = new ObjectMapper();

    // 의존성 주입이 완료된 후 실행
    @PostConstruct
    public void init() {
        this.scraperMap = scrapers.stream()
                .collect(Collectors.toMap(
                        MusicScraper::getScraperName,
                        s -> s
                ));
    }

    /**
     * contoller가 실행할 서비스 로직 - summary api용
     * 레디스 확인 후 없으면 checkDbAndLock호출
     *
     * @param vendor 검색하고자하는 vendor
     * @return 곡 정보 리스트
     */
    public Mono<List<MusicInfoResponse>> getSummary(Vendor vendor) {
        return getCachedData(vendor, "summary:" + vendor, MusicSummaryCache.class)
                .map(MusicSummaryCache::getData);
    }

    /**
     * contoller가 실행할 서비스 로직 - songs api용
     * 레디스 확인 후 없으면 checkDbAndLock호출
     *
     * @param vendor 검색하고자하는 vendor
     * @return 곡 상세 정보 리스트
     */
    public Mono<List<MusicAlbumInfoResponse>> getDetails(Vendor vendor) {
        return getCachedData(vendor, "detail:" + vendor, MusicAlbumInfoCache.class)
                .map(MusicAlbumInfoCache::getData);
    }

    /**
     * contoller가 실행할 서비스 로직 - song api용
     * 레디스 확인 후 없으면 checkDbAndLock호출
     *
     * @param vendor 검색하고자하는 vendor, songId
     * @return 단일 곡 상세 정보
     */
    public Mono<MusicAlbumInfoResponse> getSong(Vendor vendor, String musicId) {
        return getCachedData(vendor, "song:" + musicId, MusicAlbumInfoResponse.class);
    }

    /**
     * 스크래퍼 고르기
     *
     * @param vendor 스크래핑 사이트 선택
     * @return 스크래퍼
     */
    private MusicScraper chosenScraper(Vendor vendor) {
        // 스크래퍼 가져오기
        MusicScraper scraper = scraperMap.get(vendor);

        if (scraper == null)
            throw new VendorNotFoundException();

        return scraper;
    }

    /**
     * 레디스 확인 후 없으면 DB/스크래핑 로직을 실행
     *
     * @param vendor    스크래핑 사이트
     * @param cacheKey  검색할 키
     * @param classType 반환 타입
     * @return list나 단일 곡 객체 반환
     */
    private <T> Mono<T> getCachedData(Vendor vendor, String cacheKey, Class<T> classType) {

        return getFromRedis(cacheKey, classType)
                .switchIfEmpty(Mono.defer(() ->
                        checkDbAndLock(vendor, cacheKey, classType)
                                .then(getFromRedis(cacheKey, classType))
                ));
    }

    /**
     * redis에서 필요한 데이터 객체 반환
     *
     * @param key       꺼내올 키
     * @param classType 반환 타입
     * @return info, Albuminfo, 리스트나 단일 객체
     */
    private <T> Mono<T> getFromRedis(String key, Class<T> classType) {

        return redisTemplate.opsForValue().get(key)
                .map(json -> deserialize(json, classType));
    }

    private <T> T deserialize(String json, Class<T> classType) {
        try {
            return mapper.readValue(json, classType);
        } catch (Exception e) {
            log.error("Redis 역직렬화 실패 - Key: {}, Error: {}", json, e.getMessage());
            throw new RuntimeException("JSON 변환 중 에러 발생", e);
        }
    }

    /**
     * DB조회 후 30분 지났는지 확인
     * 업다면 스크래핑 후 레디스
     *
     * @param vendor 스크래핑할 사이트
     * @return 곡 정보를 담은 Mono반환
     */
    public <T> Mono<Void> checkDbAndLock(Vendor vendor, String cacheKey, Class<T> classType) {
        // JPA로 DB테이블 검색
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);

        return Mono.fromCallable(() ->
                        // 데이터가 있는가 && 있다면 30분이 지나지 않았는가
                        musicSummaryRepository.existsByVendorAndCreatedAtAfter(vendor, thirtyMinutesAgo)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(isFresh -> {
                    if (isFresh) {
                        return Mono.fromCallable(() -> {
                                    // 30분 안지났다면 redis에 넣고 반환
                                    List<MusicSummary> summaries = musicSummaryRepository.findByVendorWithDetail(vendor);
                                    List<MusicDetail> details = summaries.stream()
                                            .map(MusicSummary::getMusicDetail)
                                            .distinct()
                                            .toList();

                                    return Map.entry(summaries, details);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                // db에 데이터가 있다면 레디스에 저장
                                .flatMap(entry -> saveRedis(vendor, entry.getKey())
                                );
                    } else {
                        // 없다면 스크래핑 시도
                        return proceedToLock(chosenScraper(vendor), vendor, cacheKey, classType);
                    }
                });
    }

    /**
     * 락을 걸고 성공하면 스크래핑 호출
     *
     * @param scraper 스크래핑 모듈
     * @param vendor  스크래핑 사이트
     */
    public <T> Mono<Void> proceedToLock(MusicScraper scraper, Vendor vendor, String cacheKey, Class<T> classType) {
        // redis에 setIfAbsent를 사용하여 락 키 생성 lock:
        String lockKey = "lock:" + vendor;

        return redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(10))
                .flatMap(isLocked -> {
                    if (isLocked) {
                        // 다시 한번 레디스 확인
                        return getFromRedis(cacheKey, classType)
                                .flatMap(getCachedData -> {
                                    log.info("락 획득 후, 이미 캐시가 존재함 : {} ", vendor);
                                    return Mono.empty();
                                })
                                .switchIfEmpty(
                                        // 성공하면 scrapeAndSave로 이동
                                        refreshData(scraper, vendor)
                                )// 내용물을 Mono로 갈아끼우기
                                .then(redisTemplate.delete(lockKey))
                                .then();
                    } else {
                        return Mono.error(new ScrapingFailedException());
                    }
                })
                .onErrorResume(e -> // 에러가 나도 락은 지워야 하니까
                        redisTemplate.delete(lockKey).then(Mono.error(e))
                )
                // 실패하면 대기했다가 다시 시도
                .retryWhen(Retry.fixedDelay(10, Duration.ofMillis(500))
                        .filter(throwable -> throwable instanceof ScrapingFailedException));

    }

    /**
     * DB저장 로직
     *
     * @param vendor  스크래핑한 사이트 이름
     * @param scraper 스크래핑 모듈
     */
    private Mono<Void> refreshData(MusicScraper scraper, Vendor vendor) {
        return scraper.scrape()
                .flatMap(scrapList -> Mono.fromCallable(() -> {
                                    // List데이터 정보를 가져와 2개의 테이블에 분할하여 생성
                                    Map<String, MusicDetail> mapDetails = toDetails(scrapList);
                                    musicDetailRepository.saveAll(mapDetails.values());
                                    List<MusicSummary> summaries = toSummaries(scrapList, vendor, mapDetails);


                                    // 두 리스트 한꺼번에 넘기기
                                    return musicSummaryRepository.saveAll(summaries);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(saveSummaries -> saveRedis(vendor, saveSummaries)
                                )
                );
    }

    /**
     * 레디스에 정보 저장
     *
     * @param vendor    스크래핑한 사이트 이름
     * @param summaries summary list
     */
    private Mono<Void> saveRedis(Vendor vendor, List<MusicSummary> summaries) {
        // DTO 변환
        List<MusicInfoResponse> infoList = summaryToInfoList(summaries);
        // 이거 쿼리로 받는게 아니라. 고민
        List<MusicAlbumInfoResponse> albumList = detailToInfoList(summaries);

        // 레디스 저장 태스크들
        ObjectMapper objectMapper = new ObjectMapper();
        MusicSummaryCache summaryCache = MusicSummaryCache.builder()
                .data(infoList)
                .build();

        MusicAlbumInfoCache albumInfoCache = MusicAlbumInfoCache.builder()
                .data(albumList)
                .build();

        String infoJson = objectMapper.writeValueAsString(summaryCache);
        String albumJson = objectMapper.writeValueAsString(albumInfoCache);

        Mono<Void> saveSummary = redisTemplate.opsForValue()
                .set("summary:" + vendor, infoJson, Duration.ofMinutes(30)).then();

        Mono<Void> saveDetail = redisTemplate.opsForValue()
                .set("detail:" + vendor, albumJson, Duration.ofMinutes(30)).then();

        Mono<Void> saveSongs = Flux.fromIterable(albumList)
                .flatMap(dto -> {
                    String songJson = objectMapper.writeValueAsString(dto);
                    return redisTemplate.opsForValue()
                            .set("song:" + dto.info().songId(), songJson, Duration.ofMinutes(30));
                })
                .then();

        // 모두 저장 후 결과 반환
        return Mono.when(saveSummary, saveDetail, saveSongs);
    }

    /**
     * summary 정보를 info dto로 변환
     *
     * @param summaries summary list
     * @return 음원 정보 리스트
     */
    private List<MusicInfoResponse> summaryToInfoList(List<MusicSummary> summaries) {
        return summaries.stream()
                .map(MusicInfoResponse::from)
                .toList();
    }

    /**
     * detail, summary 정보를 albuminfo dto로 변환
     *
     * @param summaries summary list
     * @return 음원 상세 정보 리스트
     */
    private List<MusicAlbumInfoResponse> detailToInfoList(List<MusicSummary> summaries) {
        return summaries.stream()
                .map(MusicAlbumInfoResponse::from)
                .toList();
    }

    /**
     * 스크래핑 데이터를 summary로 변환
     *
     * @param contexts 스크래핑 데이터
     * @param vendor   스크래핑할 사이트 선택
     * @return summary list반환
     */
    private List<MusicSummary> toSummaries(List<MusicScrapingContext> contexts, Vendor vendor, Map<String, MusicDetail> mapDetails) {
        return contexts.stream()
                .map(context -> {
                    String detailKey = context.getAgency() + "|" + context.getPublisher();
                    return MusicSummary.of(context, vendor, mapDetails.get(detailKey));
                })
                .toList();
    }

    /**
     * 스크래핑 데이터를 detail로 변환
     *
     * @param contexts 스크래핑 데이터
     * @return detail list반환
     */
    private Map<String, MusicDetail> toDetails(List<MusicScrapingContext> contexts) {
        Map<String, MusicDetail> detailMap = new HashMap<>();

        for (MusicScrapingContext context : contexts) {
            String detailKey = context.getAgency() + "|" + context.getPublisher();

            // 맵에 없으면 새로 만듦
            detailMap.computeIfAbsent(detailKey, k -> MusicDetail.of(context));

        }
        return detailMap;
    }
}
