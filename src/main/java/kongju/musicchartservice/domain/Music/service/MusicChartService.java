package kongju.musicchartservice.domain.Music.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import kongju.musicchartservice.domain.Music.dto.*;
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

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.repository.MusicDetailRepository;
import kongju.musicchartservice.domain.infrastructure.scraping.MusicScraper;
import kongju.musicchartservice.domain.Music.repository.MusicSummaryRepository;
import kongju.musicchartservice.domain.Music.entity.MusicDetail;
import kongju.musicchartservice.domain.Music.entity.MusicSummary;
import kongju.musicchartservice.global.error.exception.VendorNotFoundException;
import kongju.musicchartservice.global.error.exception.ScrapingFailedException;


@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MusicChartService {
    private final MusicDetailRepository musicDetailRepository;
    private final MusicSummaryRepository musicSummaryRepository;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final List<MusicScraper> scrapers;
    private Map<Vendor, MusicScraper> scraperMap;

    // 의존성 주입이 완료된 후 실행
    @PostConstruct
    public void init() {
        this.scraperMap = scrapers.stream()
                .collect(Collectors.toMap(
                        s -> Vendor.fromString(s.getScraperName()),
                        s -> s
                ));
    }

    /**
     * contoller가 실행할 서비스 로직 - summary api용
     * 레디스 확인 후 없으면 checkDbAndLock호출
     *
     * @param request 검색하고자하는 vendor
     * @return 곡 정보 리스트
     */
    public Mono<List<MusicInfoResponse>> getSummary(VendorRequest request) {
        return getCachedData(request, "summary");
    }

    /**
     * contoller가 실행할 서비스 로직 - songs api용
     * 레디스 확인 후 없으면 checkDbAndLock호출
     *
     * @param request 검색하고자하는 vendor
     * @return 곡 상세 정보 리스트
     */
    public Mono<List<MusicAlbumInfoResponse>> getDetails(VendorRequest request) {
        return getCachedData(request, "detail");
    }


//    -------------------------------------------------------------------------------------------------------------

    /**
     * 레디스 확인 후 없으면 DB/스크래핑 로직을 실행하는 공통 처리기
     *
     * @param request 요청 vendor
     * @param prefix  redis검색 key
     * @return dto에 맞는 데이터
     */
    private <T> Mono<List<T>> getCachedData(VendorRequest request, String prefix) {
        // vendor 가져오기
        Vendor vendor = Vendor.fromString(request.vendor());
        // 스크래퍼 가져오기
        MusicScraper scraper = chosenScraper(vendor);

        String cacheKey = prefix + ":" + vendor;

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(List.class)
                .map(list -> (List<T>) list)
                .switchIfEmpty(Mono.defer(() ->
                        checkDbAndLock(scraper, vendor)
                                .then(getFromRedis(cacheKey, List.class))
                                .map(list -> (List<T>) list)
                ));
    }

    /**
     * 레디스 확인 후 없으면 DB/스크래핑 로직을 실행
     * @param request songId와 vendor정보
     * @return 단일 곡 상세 정보
     */
    private Mono<MusicAlbumInfoResponse> getCachedData(SongRequest request) {
        // vendor 가져오기
        Vendor vendor = Vendor.fromString(request.vendor());
        // 스크래퍼 가져오기
        MusicScraper scraper = chosenScraper(vendor);

        String cacheKey = "song:" + request.musicId();

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(MusicAlbumInfoResponse.class)
                .switchIfEmpty(Mono.defer(() ->
                        checkDbAndLock(scraper, vendor)
                                .then(getFromRedis(cacheKey, MusicAlbumInfoResponse.class))
                ));
    }

    private MusicScraper chosenScraper(Vendor vendor) {
        // 스크래퍼 가져오기
        MusicScraper scraper = scraperMap.get(vendor);

        if (scraper == null)
            throw new VendorNotFoundException();

        return scraper;
    }

    /**
     * DB조회 후 30분 지났는지 확인
     * 업다면 스크래핑 후 레디스
     *
     * @param scraper 스크래퍼
     * @param vendor  스크래핑할 사이트
     * @return 곡 정보를 담은 Mono반환
     */
    public Mono<Void> checkDbAndLock(MusicScraper scraper, Vendor vendor) {
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
                                    List<MusicDetail> details = musicDetailRepository.findByVendorWithSummary(vendor);
                                    List<MusicSummary> summaries = details.stream().map(MusicDetail::getMusicSummary).toList();

                                    return Map.entry(summaries, details);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                // db에 데이터가 있다면 레디스에 저장
                                .flatMap(entry -> saveRedis(vendor, entry.getKey(), entry.getValue())
                                );
                    } else {
                        // 없다면 스크래핑 시도
                        return proceedToLock(scraper, vendor);
                    }
                });
    }

    /**
     * 락을 걸고 성공하면 스크래핑 호출
     *
     * @param scraper 스크래핑 모듈
     * @param vendor  스크래핑 사이트
     */
    public Mono<Void> proceedToLock(MusicScraper scraper, Vendor vendor) {
        // redis에 setIfAbsent를 사용하여 락 키 생성 lock:
        String lockKey = "lock:" + vendor;

        return redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(10))
                .flatMap(isLocked -> {
                    if (isLocked) {
                        // 성공하면 scrapeAndSave로 이동
                        return refreshData(scraper, vendor)
                                // 내용물을 Mono로 갈아끼우기
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
                                    List<MusicSummary> summaries = toSummaries(scrapList, vendor);
                                    List<MusicSummary> saveSummaries = musicSummaryRepository.saveAll(summaries);

                                    List<MusicDetail> details = toDetails(scrapList, saveSummaries);
                                    List<MusicDetail> saveDetails = musicDetailRepository.saveAll(details);

                                    // 두 리스트 한꺼번에 넘기기
                                    return Map.entry(saveSummaries, saveDetails);
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(entry -> saveRedis(vendor, entry.getKey(), entry.getValue())
                                )
                );
    }

    /**
     * 레디스에 정보 저장
     *
     * @param vendor  스크래핑한 사이트 이름
     * @param details detail list
     */
    private Mono<Void> saveRedis(Vendor vendor, List<MusicSummary> summaries, List<MusicDetail> details) {
        // DTO 변환
        List<MusicInfoResponse> infoList = summaryToInfoList(summaries);
        // 이거 쿼리로 받는게 아니라. 고민
        List<MusicAlbumInfoResponse> albumList = detailToInfoList(details);

        // 레디스 저장 태스크들
        Mono<Void> saveSummary = redisTemplate.opsForValue()
                .set("summary:" + vendor, infoList, Duration.ofMinutes(30)).then();

        Mono<Void> saveDetail = redisTemplate.opsForValue()
                .set("detail:" + vendor, albumList, Duration.ofMinutes(30)).then();

        Mono<Void> saveSongs = Flux.fromIterable(albumList)
                .flatMap(dto -> redisTemplate.opsForValue()
                        .set("song:" + dto.info().songId(), dto, Duration.ofMinutes(30)))
                .then();

        // 모두 저장 후 결과 반환
        return Mono.when(saveSummary, saveDetail, saveSongs);
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
                .cast(classType);
    }

    /**
     * summary 정보를 info dto로 변환
     *
     * @param summaries summary list
     * @return 음원 정보 리스트
     */
    private List<MusicInfoResponse> summaryToInfoList(List<MusicSummary> summaries) {
        return summaries.stream()
                .map(context -> MusicInfoResponse.builder()
                        .ranking(context.getRanking())
                        .title(context.getTitle())
                        .artist(context.getArtist())
                        .album(context.getAlbum())
                        .songId(context.getSongId())
                        .build())
                .toList();
    }

    /**
     * detail, summary 정보를 albuminfo dto로 변환
     *
     * @param details detail list
     * @return 음원 상세 정보 리스트
     */
    private List<MusicAlbumInfoResponse> detailToInfoList(List<MusicDetail> details) {
        return details.stream()
                .map(detail -> {
                    MusicSummary summary = detail.getMusicSummary();
                    return MusicAlbumInfoResponse.builder()
                            .info(MusicInfoResponse.builder()
                                    .ranking(summary.getRanking())
                                    .title(summary.getTitle())
                                    .artist(summary.getArtist())
                                    .album(summary.getAlbum())
                                    .songId(summary.getSongId())
                                    .build())
                            .agency(detail.getAgency())
                            .publisher(detail.getPublisher())
                            .build();
                })
                .toList();
    }

    /**
     * 스크래핑 데이터를 summary로 변환
     *
     * @param contexts 스크래핑 데이터
     * @param vendor   스크래핑할 사이트 선택
     * @return summary list반환
     */
    private List<MusicSummary> toSummaries(List<MusicScrapingContext> contexts, Vendor vendor) {
        return contexts.stream()
                .map(content -> MusicSummary.builder()
                        .ranking(content.getRanking())
                        .title(content.getTitle())
                        .album(content.getAlbum())
                        .artist(content.getArtist())
                        .songId(content.getSongId())
                        .vendor(vendor)
                        .build())
                .toList();
    }

    /**
     * 스크래핑 데이터를 detail로 변환
     *
     * @param contexts  스크래핑 데이터
     * @param summaries summary list
     * @return detail list반환
     */
    private List<MusicDetail> toDetails(List<MusicScrapingContext> contexts, List<MusicSummary> summaries) {
        List<MusicDetail> details = new ArrayList<>();
        for (int i = 0; i < summaries.size(); i++) {
            MusicScrapingContext context = contexts.get(i);
            MusicSummary parent = summaries.get(i);

            details.add(MusicDetail.builder()
                    .publisher(context.getPublisher())
                    .agency(context.getAgency())
                    .musicSummary(parent)
                    .build());
        }
        return details;
    }

}
