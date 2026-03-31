package kongju.musicchartservice.domain.Music.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import kongju.musicchartservice.global.error.exception.ScrapingFailedException;
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
import tools.jackson.databind.ObjectMapper;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.dto.VendorRequest;
import kongju.musicchartservice.domain.Music.dto.MusicInfoResponse;
import kongju.musicchartservice.domain.Music.repository.MusicDetailRepository;
import kongju.musicchartservice.domain.infrastructure.scraping.MusicScraper;
import kongju.musicchartservice.domain.Music.repository.MusicSummaryRepository;
import kongju.musicchartservice.domain.Music.dto.MusicAlbumInfoResponse;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import kongju.musicchartservice.domain.Music.entity.MusicDetail;
import kongju.musicchartservice.domain.Music.entity.MusicSummary;
import kongju.musicchartservice.global.error.exception.VendorNotFoundException;


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
    private final ObjectMapper objectMapper;

    // 의존성 주입이 완료된 후 실행됨
    @PostConstruct
    public void init() {
        this.scraperMap = scrapers.stream()
                .collect(Collectors.toMap(
                        s -> Vendor.fromString(s.getScraperName()),
                        s -> s
                ));
    }

    /**
     * 레디스 확인 후 없으면 checkDbAndLock호출
     *
     * @param request 검색하고자하는 vendor
     * @return 곡 정보 리스트
     */
    public Mono<List<MusicInfoResponse>> getSummary(VendorRequest request) {
        // vendor 가져오기

        Vendor vendor = Vendor.fromString(request.vendor());
        // 스크래퍼 가져오기
        MusicScraper scraper = scraperMap.get(vendor);
        if (scraper == null) {
            throw new VendorNotFoundException();
        }

        // Vendor 확인 및 Key 생성
        String cacheKey = "summary:" + vendor;

        // 레디스 조회
        return redisTemplate.opsForValue()
                .get(cacheKey)
                // 모노 안에 객체가 뭔지 명시적으로 표현
                .cast(List.class)
                // 리스트 타입을 명시적으로 표현
                .map(list -> (List<MusicInfoResponse>) list)
                // 데이터 없으면 checkDbAndLock 실행
                .switchIfEmpty(Mono.defer(() -> checkDbAndLock(scraper, vendor)));

    }

    /**
     * DB조회 후 30분 지났는지 확인
     */
    public Mono<List<MusicInfoResponse>> checkDbAndLock(MusicScraper scraper, Vendor vendor) {
        // JPA로 DB테이블 검색
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);

        return Mono.fromCallable(() -> {
                            // 데이터가 있는가 && 있다면 30분이 지나지 않았는가
                            return musicSummaryRepository.existsByVendorAndCreatedAtAfter(vendor, thirtyMinutesAgo);
                        }
                ).subscribeOn(Schedulers.boundedElastic())
                .flatMap(isFresh -> {
                    if (isFresh) {
                        // 30분 안지났다면 redis에 넣고 반환
                        return getLatestDataFromDb(vendor);
                    } else {
                        // 지났다면 proceedToLock()호출
                        return proceedToLock(scraper, vendor);
                    }
                });
    }

    private Mono<List<MusicInfoResponse>> getLatestDataFromDb(Vendor vendor) {
        // 레디스에 DB에 summary와 detail데이터 옮겨 넣기

        // MusicInfo, AlbumInfo다 레디스에 넣기
        return Mono.fromCallable(() -> {
                    List<MusicSummary> summaries = musicSummaryRepository.findByVendor(vendor);
                    List<MusicDetail> details = musicDetailRepository.findByVendor(vendor);
                    return Map.entry(summaries, details);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(entry -> {
                    // redis 3종 키에 저장
                    List<MusicSummary> summaries = entry.getKey();
                    List<MusicDetail> details = entry.getValue();

                    List<MusicInfoResponse> infoList = summaryToInfoList(summaries);
                    List<MusicAlbumInfoResponse> albumList = detailToInfoList(details, summaries);

                    String summaryKey = "summary:" + vendor;
                    // 타임은 30분으로 지정
                    Mono<Void> saveSummary = redisTemplate.opsForValue()
                            .set(summaryKey, infoList, Duration.ofMinutes(30))
                            .then();

                    String detailKey = "detail:" + vendor;
                    // 타임은 30분으로 지정
                    Mono<Void> saveDetail = redisTemplate.opsForValue()
                            .set(detailKey, albumList, Duration.ofMinutes(30))
                            .then();

                    Mono<Void> saveSongs = Flux.fromIterable(albumList)
                            .flatMap(dto -> {
                                String songKey = "song:" + dto.info().songId();
                                return redisTemplate.opsForValue().set(songKey, dto, Duration.ofMinutes(30));
                            }).then();

                    // 저장
                    return Mono.when(saveSummary, saveSongs, saveDetail)
                            .thenReturn(infoList);
                });

    }

    private List<MusicInfoResponse> summaryToInfoList(List<MusicSummary> contexts) {
        return contexts.stream()
                .map(context -> MusicInfoResponse.builder()
                        .ranking(context.getRanking())
                        .title(context.getTitle())
                        .artist(context.getArtist())
                        .album(context.getAlbum())
                        .songId(context.getSongId())
                        .build())
                .toList();
    }

    private List<MusicAlbumInfoResponse> detailToInfoList(List<MusicDetail> contexts, List<MusicSummary> summaries) {
        List<MusicAlbumInfoResponse> result = new ArrayList<>();

        for (int i = 0; i < contexts.size(); i++) {
            MusicDetail detail = contexts.get(i);
            MusicSummary summary = summaries.get(i);

            MusicAlbumInfoResponse alInfo = MusicAlbumInfoResponse.builder()
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

            result.add(alInfo);
        }
        return result;
    }


    /**
     * 락을 걸고 성공하면 스크래핑 호출
     */
    public Mono<List<MusicInfoResponse>> proceedToLock(MusicScraper scraper, Vendor vendor) {
        // redis에 setIfAbsent를 사용하여 락 키 생성 lock:
        String lockKey = "lock:" + vendor;

        return redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(10))
                .flatMap(isLocked -> {
                    if (isLocked) {
                        // 성공하면 scrapeAndSave로 이동
                        return scrapeAndSave(scraper, vendor)
                                // 내용물을 Mono로 갈아끼우기
                                .flatMap(result -> redisTemplate.delete(lockKey)
                                        .thenReturn(result)
                                )
                                .onErrorResume(e ->
                                        redisTemplate.delete(lockKey).then(Mono.error(e))
                                );
                    } else {
                        return Mono.error(new ScrapingFailedException());
                    }
                })
                // 실패하면 대기했다가 다시 시도
                .retryWhen(Retry.fixedDelay(10, Duration.ofMillis(500))
                        .filter(throwable -> throwable instanceof ScrapingFailedException));

    }

    /**
     * 스크래핑 데이터를 받고 DB 각 테이블에 저장, redis에 저장
     */
    public Mono<List<MusicInfoResponse>> scrapeAndSave(MusicScraper scraper, Vendor vendor) {
        // 스크래핑 시작
        return scraper.scrape()
                // 스크랩 데이터를 조작
                // 리스트를 별도의 스레드에서 작업
                .flatMap(scrapList -> Mono.fromCallable(() -> {
                            // List데이터 정보를 가져와 2개의 테이블에 분할하여 생성
                            List<MusicSummary> summaries = toSummaries(scrapList, vendor);
                            List<MusicSummary> saveSummaries = musicSummaryRepository.saveAll(summaries);
                            List<MusicDetail> details = toDetails(scrapList, saveSummaries);
                            musicDetailRepository.saveAll(details);
                            // DTO변환
                            List<MusicInfoResponse> infoList = toInfoList(scrapList);
                            List<MusicAlbumInfoResponse> albumInfoList = toAlbumInfoList(scrapList);

                            // 두 리스트 한꺼번에 넘기기
                            return Map.entry(infoList, albumInfoList);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(entry -> {
                            // redis 3종 키에 저장
                            List<MusicInfoResponse> infoList = entry.getKey();
                            List<MusicAlbumInfoResponse> albumList = entry.getValue();

                            String summaryKey = "summary:" + vendor;
                            // 타임은 30분으로 지정
                            Mono<Void> saveSummary = redisTemplate.opsForValue()
                                    .set(summaryKey, infoList, Duration.ofMinutes(30))
                                    .then();

                            String detailKey = "detail:" + vendor;
                            // 타임은 30분으로 지정
                            Mono<Void> saveDetail = redisTemplate.opsForValue()
                                    .set(detailKey, albumList, Duration.ofMinutes(30))
                                    .then();

//                                    Flux.fromIterable이 리스트를 하나씩 비동기로 전달
                            Mono<Void> saveSongs = Flux.fromIterable(albumList)
                                    .flatMap(dto -> {
                                        String songKey = "song:" + dto.info().songId();
                                        return redisTemplate.opsForValue().set(songKey, dto, Duration.ofMinutes(30));
                                    }).then();
                            // 저장
                            return Mono.when(saveSummary, saveSongs, saveDetail)
                                    .thenReturn(infoList);
                        }));
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

    /**
     * 음원 정보 dto
     *
     * @param contexts 스크래핑 데이터
     * @return 음원 정보 dto반환
     */
    private List<MusicInfoResponse> toInfoList(List<MusicScrapingContext> contexts) {
        return contexts.stream()
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
     * 음원 상세 정보 dto
     *
     * @param contexts 스크래핑 데이터
     * @return
     */
    private List<MusicAlbumInfoResponse> toAlbumInfoList(List<MusicScrapingContext> contexts) {
        return contexts.stream()
                .map(context -> MusicAlbumInfoResponse.builder()
                        .info(MusicInfoResponse.builder()
                                .ranking(context.getRanking())
                                .title(context.getTitle())
                                .artist(context.getArtist())
                                .album(context.getAlbum())
                                .songId(context.getSongId())
                                .build())
                        .agency(context.getAgency())
                        .publisher(context.getPublisher())
                        .build())
                .toList();
    }

}
