package kongju.musicchartservice.domain.Music.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MusicAlbumInfoCache {
    private List<MusicAlbumInfoResponse> data;
}