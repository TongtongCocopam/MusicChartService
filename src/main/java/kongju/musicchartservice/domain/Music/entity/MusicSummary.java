package kongju.musicchartservice.domain.Music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;


@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MusicSummary {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;
    @Column(nullable = false)
    private int ranking;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String singer;
    @Column(nullable = false)
    private String album;
    @Column(nullable = false)
    private String song_id;
    @Column(nullable = false)
    private String vendor;

    @Builder
    public MusicSummary(int ranking, String name, String singer,
                        String album, String song_id, String vendor) {
        this.ranking = ranking;
        this.name = name;
        this.singer = singer;
        this.album = album;
        this.song_id = song_id;
        this.vendor = vendor;
    }
}
