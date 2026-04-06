package kongju.musicchartservice.domain.Music.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import kongju.musicchartservice.domain.Music.entity.MusicDetail;
import kongju.musicchartservice.domain.Music.entity.MusicSummary;
import lombok.Builder;

@Builder
public record MusicAlbumInfoResponse(
        MusicInfoResponse info,
        @JsonProperty("발매사")
        String publisher,
        @JsonProperty("기획사")
        String agency
) implements MusicResponse {
    public static MusicAlbumInfoResponse from(MusicSummary summary) {
        MusicDetail detail = summary.getMusicDetail();
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
    }
}
