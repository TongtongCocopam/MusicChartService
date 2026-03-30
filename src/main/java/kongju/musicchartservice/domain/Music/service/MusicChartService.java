package kongju.musicchartservice.domain.Music.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import kongju.musicchartservice.domain.Music.dto.MusicAlbumInfoResponse;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;
import kongju.musicchartservice.domain.Music.entity.MusicDetail;
import kongju.musicchartservice.domain.Music.entity.MusicSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.dto.MusicInfoResponse;
import kongju.musicchartservice.domain.Music.dto.VendorRequest;
import kongju.musicchartservice.domain.Music.repository.MusicDetailRepository;
import kongju.musicchartservice.domain.Music.repository.MusicSummaryRepository;
import kongju.musicchartservice.domain.infrastructure.scraping.MusicScraper;
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
    private final ObjectMapper objectMapper;
    private Map<Vendor, MusicScraper> scraperMap;

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

        // 데이터 없으면 checkDbAndLock 실행
        return null;
    }

    /**
     * DB조회 후 30분 지났는지 확인
     */
    public Mono<List<MusicInfoResponse>> checkDbAndLock(MusicScraper scraper, Vendor vendor) {
        // JPA로 DB테이블 검색

        // 데이터가 있는가 && 있다면 30분이 지나지 않았는가

        // 30분 안지났다면 redis에 넣고 반환

        // 지났다면 proceedToLock()호출
        return null;
    }

    /**
     * 락을 걸고 성공하면 스크래핑 호출
     */
    public void proceedToLock(MusicScraper scraper, Vendor vendor) {
        // redis에 setIfAbsent를 사용하여 락 키 생성 lock:

        // 성공하면 scrapeAndSave로 이동

        // 실패하면 대기했다가 다시 시도
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


    private List<MusicSummary> toSummaries(List<MusicScrapingContext> contexts, Vendor vendor) {
        return contexts.stream()
                .map(content -> MusicSummary.builder()
                        .ranking(content.getRanking())
                        .name(content.getTitle())
                        .album(content.getAlbum())
                        .singer(content.getArtist())
                        .songId(content.getSongId())
                        .vendor(vendor)
                        .build())
                .toList();
    }


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

    private List<MusicInfoResponse> toInfoList(List<MusicScrapingContext> contexts) {
        return contexts.stream()
                .map(context -> MusicInfoResponse.builder()
                        .ranking(context.getRanking())
                        .title(context.getTitle())
                        .singer(context.getArtist())
                        .album(context.getAlbum())
                        .songId(context.getSongId())
                        .build())
                .toList();
    }

    private List<MusicAlbumInfoResponse> toAlbumInfoList(List<MusicScrapingContext> contexts) {
        return contexts.stream()
                .map(context -> MusicAlbumInfoResponse.builder()
                        .info(MusicInfoResponse.builder()
                                .ranking(context.getRanking())
                                .title(context.getTitle())
                                .singer(context.getArtist())
                                .album(context.getAlbum())
                                .songId(context.getSongId())
                                .build())
                        .agency(context.getAgency())
                        .publisher(context.getPublisher())
                        .build())
                .toList();
    }

}
