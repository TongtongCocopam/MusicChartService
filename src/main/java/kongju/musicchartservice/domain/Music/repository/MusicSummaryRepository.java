package kongju.musicchartservice.domain.Music.repository;

import io.lettuce.core.dynamic.annotation.Param;
import kongju.musicchartservice.domain.Music.constant.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import kongju.musicchartservice.domain.Music.entity.MusicSummary;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MusicSummaryRepository extends JpaRepository<MusicSummary, Long> {
    List<MusicSummary> findByVendor(Vendor vendor);

    MusicSummary findByVendorAndSongId(Vendor vendor, String songId);


    boolean existsByVendorAndCreatedAtAfter(Vendor vendor, LocalDateTime expiredAt);

    @Query("SELECT s FROM MusicSummary s LEFT JOIN FETCH s.musicDetail d WHERE s.vendor = :vendor")
    List<MusicSummary> findByVendorWithDetail(@Param("vendor") Vendor vendor);
}
