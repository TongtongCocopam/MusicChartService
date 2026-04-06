package kongju.musicchartservice.domain.Music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;

import kongju.musicchartservice.domain.Music.dto.MusicScrapingContext;

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

    @Builder
    public MusicDetail(String publisher, String agency) {
        this.publisher = publisher;
        this.agency = agency;
    }

    public static MusicDetail of(MusicScrapingContext context) {
        return MusicDetail.builder()
                .publisher(context.getPublisher())
                .agency(context.getAgency())
                .build();
    }
}
