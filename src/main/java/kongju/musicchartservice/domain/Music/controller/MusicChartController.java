package kongju.musicchartservice.domain.Music.controller;

import java.util.List;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import kongju.musicchartservice.domain.Music.dto.MusicAlbumInfoResponse;
import kongju.musicchartservice.domain.Music.dto.MusicInfoResponse;
import kongju.musicchartservice.domain.Music.service.MusicChartService;
import kongju.musicchartservice.global.common.ApiResponse;


@RestController
@RequestMapping("/api/v1/music-chart/")
@RequiredArgsConstructor
public class MusicChartController {

    private final MusicChartService musicChartService;

    /**
     * vendor에 해당하는 곡 전체 리스트 가져오기
     * @param vendor 스크래핑할 사이트
     * @return List<MusicInfoResponse>
     */
    @GetMapping("{vendor}/summary")
    public Mono<ResponseEntity<ApiResponse<List<MusicInfoResponse>>>> getVendorSummary(@PathVariable Vendor vendor) {
        return musicChartService.getSummary(vendor)
                .map(list -> ResponseEntity
                        .status(HttpStatus.OK)
                        .body(ApiResponse.success(list))
                );
    }

    /**
     * vendor에 해당하는 곡 상세 정보 포함 전체 리스트 가져오기
     * @param vendor 스크래핑할 사이트
     * @return
     */
    @GetMapping("{vendor}/songs")
    public Mono<ResponseEntity<ApiResponse<List<MusicAlbumInfoResponse>>>> getVendorDetail(@PathVariable Vendor vendor) {
        return musicChartService.getDetails(vendor)
                .map(list -> ResponseEntity
                        .status(HttpStatus.OK)
                        .body(ApiResponse.success(list))
                );
    }

    /**
     *
     * @param vendor 스크래핑할 사이트
     * @param musicId 가져올 곡 번호
     * @return vendor와 musicId가 일치하는 단일 곡 상세 정보
     */
    @GetMapping("{vendor}/song/{music_id}")
    public Mono<ResponseEntity<ApiResponse<MusicAlbumInfoResponse>>> getVendorSong(@PathVariable Vendor vendor, String musicId) {
        return musicChartService.getSong(vendor,  musicId)
                .map(song -> ResponseEntity
                        .status(HttpStatus.OK)
                        .body(ApiResponse.success(song))
                );
    }

}
