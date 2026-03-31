package kongju.musicchartservice.domain.Music.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record MusicAlbumInfoResponse(
        MusicInfoResponse info,
        @JsonProperty("발매사")
        String publisher,
        @JsonProperty("기획사")
        String agency
)  implements MusicResponse{
}
