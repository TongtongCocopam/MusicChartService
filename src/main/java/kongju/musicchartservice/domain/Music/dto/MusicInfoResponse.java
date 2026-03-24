package kongju.musicchartservice.domain.Music.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record MusicInfoResponse(
        @JsonProperty("순위")
        int ranking,
        @JsonProperty("곡 이름")
        String name,
        @JsonProperty("가수")
        String singer,
        @JsonProperty("앨범")
        String album
) {
}
