package kongju.musicchartservice.domain.Music.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import static jakarta.persistence.GenerationType.IDENTITY;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MusicSummary {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;
    @Column(nullable = false)
    private int ranking;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String artist;
    @Column(nullable = false)
    private String album;
    @Column(nullable = false)
    private String songId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Vendor vendor;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createAt;

    @Builder
    public MusicSummary(int ranking, String title, String artist,
                        String album, String songId, Vendor vendor) {
        this.ranking = ranking;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.songId = songId;
        this.vendor = vendor;
        this.createAt = LocalDateTime.now();
    }

    public static MusicSummary of(MusicScrapingContext content, Vendor vendor) {
        return MusicSummary.builder()
                .ranking(content.getRanking())
                .title(content.getTitle())
                .album(content.getAlbum())
                .artist(content.getArtist())
                .songId(content.getSongId())
                .vendor(vendor)
                .build();
    }

}
