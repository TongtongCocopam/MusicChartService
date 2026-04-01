package kongju.musicchartservice.domain.Music.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import kongju.musicchartservice.domain.Music.entity.MusicSummary;


@Builder
public record MusicInfoResponse(
        @JsonProperty("순위")
        int ranking,
        @JsonProperty("곡 이름")
        String title,
        @JsonProperty("가수")
        String artist,
        @JsonProperty("앨범")
        String album,
        @JsonProperty("곡 아이디")
        String songId
) implements MusicResponse{
        public static MusicInfoResponse from(MusicSummary summary) {
                return MusicInfoResponse.builder()
                        .ranking(summary.getRanking())
                        .title(summary.getTitle())
                        .artist(summary.getArtist())
                        .album(summary.getAlbum())
                        .songId(summary.getSongId())
                        .build();
        }
}
