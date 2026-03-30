package kongju.musicchartservice.domain.Music.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record MusicInfoResponse(
        @JsonProperty("순위")
        int ranking,
        @JsonProperty("곡 이름")
        String title,
        @JsonProperty("가수")
        String singer,
        @JsonProperty("앨범")
        String album,
        @JsonProperty("곡 아이디")
        String songId
) {
}
