package kongju.musicchartservice.domain.Music.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MusicScrapingContext {
    private int ranking;
    private String title;
    private String artist;
    private String songId;
    private String album;
    private String albumId;
    private String publisher;
    private String agency;
}
