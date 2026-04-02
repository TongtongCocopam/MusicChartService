package kongju.musicchartservice.domain.controller;

import java.util.List;


import kongju.musicchartservice.domain.Music.dto.MusicAlbumInfoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.controller.MusicChartController;
import kongju.musicchartservice.domain.Music.dto.MusicInfoResponse;
import kongju.musicchartservice.domain.Music.service.MusicChartService;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;


@WebMvcTest(MusicChartController.class)
public class MusicChartControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MusicChartService service;

    @Test
    @DisplayName("GET /api/v1/music-chart/{vendor}/summary - 조회 성공")
    void summary_성공적으로_조회() throws Exception {
        MusicInfoResponse info = MusicInfoResponse.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("test Album")
                .songId("test SongId")
                .build();

        Mono<List<MusicInfoResponse>> response = Mono.just(List.of(info));

        when(service.getSummary(any())).thenReturn(response);

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/music-chart/MELON/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                // 비동기 시작되었는지 확인
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].['곡 이름']").value("Test Song"))
                .andExpect(jsonPath("$.data[0].가수").value("Test Artist"));
    }

    // 일치하는 vendor가 없는경우

    @Test
    @DisplayName("GET /api/v1/music-chart/{vendor}/songs - 조회 성공")
    void detail_성공적으로_조회() throws Exception {
        MusicInfoResponse info = MusicInfoResponse.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("test Album")
                .songId("test SongId")
                .build();
        MusicAlbumInfoResponse albumInfo = MusicAlbumInfoResponse.builder()
                .info(info)
                .agency("Test Agency")
                .publisher("Test Publisher")
                .build();

        Mono<List<MusicAlbumInfoResponse>> response = Mono.just(List.of(albumInfo));

        when(service.getDetails(any())).thenReturn(response);

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/music-chart/MELON/songs")
                        .contentType(MediaType.APPLICATION_JSON))
                // 비동기 시작되었는지 확인
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].info.['곡 이름']").value("Test Song"))
                .andExpect(jsonPath("$.data[0].info.가수").value("Test Artist"))
                .andExpect(jsonPath("$.data[0].기획사").value("Test Agency"));
    }


    @Test
    @DisplayName("GET /api/v1/music-chart/{vendor}/song/{music_id}- 조회 성공")
    void song_성공적으로_조회() throws Exception {
        MusicInfoResponse info = MusicInfoResponse.builder()
                .ranking(1)
                .title("Test Song")
                .artist("Test Artist")
                .album("test Album")
                .songId("SongId")
                .build();
        MusicAlbumInfoResponse albumInfo = MusicAlbumInfoResponse.builder()
                .info(info)
                .agency("Test Agency")
                .publisher("Test Publisher")
                .build();

        Mono<MusicAlbumInfoResponse> response = Mono.just(albumInfo);

        when(service.getSong(any(), any())).thenReturn(response);

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/music-chart/MELON/song/SongId")
                        .contentType(MediaType.APPLICATION_JSON))
                // 비동기 시작되었는지 확인
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.info.['곡 이름']").value("Test Song"))
                .andExpect(jsonPath("$.data.info.가수").value("Test Artist"))
                .andExpect(jsonPath("$.data.기획사").value("Test Agency"));
    }
}
