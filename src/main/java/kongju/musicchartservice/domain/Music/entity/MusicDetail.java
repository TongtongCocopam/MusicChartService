package kongju.musicchartservice.domain.Music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MusicDetail {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;
    @Column(nullable = false)
    private int publisher;
    @Column(nullable = false)
    private String agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_id")
    private MusicSummary musicSummary;

    @Builder
    public MusicDetail(int publisher, String agency) {
        this.publisher = publisher;
        this.agency = agency;
    }

}
