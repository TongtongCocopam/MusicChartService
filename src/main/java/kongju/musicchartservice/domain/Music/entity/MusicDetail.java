package kongju.musicchartservice.domain.Music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MusicDetail {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String publisher;
    @Column(nullable = false)
    private String agency;

    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "music_id")
    private MusicSummary musicSummary;

    @Builder
    public MusicDetail(String publisher, String agency,  MusicSummary musicSummary) {
        this.publisher = publisher;
        this.agency = agency;
        this.musicSummary = musicSummary;
    }

}
