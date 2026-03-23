package kongju.musicchartservice.domain.Music.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import kongju.musicchartservice.domain.Music.entity.MusicSummary;

import java.util.List;

public interface MusicSummaryRepository extends JpaRepository<MusicSummary, Long> {
    List<MusicSummary> findByVendor(String vendor);

    MusicSummary findByVendorAndSongId(String vendor, String songId);
}
