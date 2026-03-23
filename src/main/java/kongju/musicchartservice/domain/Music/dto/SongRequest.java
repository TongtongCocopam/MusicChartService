package kongju.musicchartservice.domain.Music.dto;

import jakarta.validation.constraints.NotNull;

public record SongRequest(
        @NotNull
        String vendor,
        @NotNull
        String musicId
) {
}
