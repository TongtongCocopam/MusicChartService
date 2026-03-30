package kongju.musicchartservice.domain.Music.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import kongju.musicchartservice.domain.Music.constant.Vendor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;


import static jakarta.persistence.GenerationType.IDENTITY;


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
    private String name;
    @Column(nullable = false)
    private String singer;
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
    public MusicSummary(int ranking, String name, String singer,
                        String album, String songId, Vendor vendor) {
        this.ranking = ranking;
        this.name = name;
        this.singer = singer;
        this.album = album;
        this.songId = songId;
        this.vendor = vendor;
        this.createAt = LocalDateTime.now();
    }
}
