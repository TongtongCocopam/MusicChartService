package kongju.musicchartservice.domain.Music.repository;

import kongju.musicchartservice.domain.Music.constant.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import kongju.musicchartservice.domain.Music.entity.MusicSummary;

import java.time.LocalDateTime;
import java.util.List;

public interface MusicSummaryRepository extends JpaRepository<MusicSummary, Long> {
    List<MusicSummary> findByVendor(Vendor vendor);

    MusicSummary findByVendorAndSongId(Vendor vendor, String songId);


    boolean existsByVendorAndCreatedAtAfter(Vendor vendor, LocalDateTime expiredAt);
}
