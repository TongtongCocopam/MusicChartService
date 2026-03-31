package kongju.musicchartservice.domain.Music.dto;

import jakarta.validation.constraints.NotNull;
import kongju.musicchartservice.domain.Music.constant.Vendor;

public record SongRequest(
        @NotNull
        Vendor vendor,
        @NotNull
        String musicId
) implements MusicRequest{
}
