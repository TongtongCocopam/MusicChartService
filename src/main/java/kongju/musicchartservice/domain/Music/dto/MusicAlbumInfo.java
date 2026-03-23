package kongju.musicchartservice.domain.Music.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MusicAlbumInfo(
        MusicInfo info,
        @JsonProperty("발매사")
        String publisher,
        @JsonProperty("기획사")
        String agency
) {
}
