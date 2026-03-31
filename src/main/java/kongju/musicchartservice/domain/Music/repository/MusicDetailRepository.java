package kongju.musicchartservice.domain.Music.repository;

import java.util.List;

import io.lettuce.core.dynamic.annotation.Param;
import kongju.musicchartservice.domain.Music.constant.Vendor;
import kongju.musicchartservice.domain.Music.entity.MusicDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


public interface MusicDetailRepository extends JpaRepository<MusicDetail, Long> {
    @Query("SELECT md FROM MusicDetail md " +
            "JOIN MusicSummary ms " +
            "WHERE ms.vendor = :vendor")
    List<MusicDetail> findByVendor(Vendor vendor);

    @Query("SELECT md FROM MusicDetail md " +
            "JOIN MusicSummary ms " +
            "WHERE ms.vendor = :vendor " +
            "and ms.songId= :musicId")
    MusicDetail findByVendorAndMusicId(Vendor vendor, String musicId);

    @Query("SELECT d FROM MusicDetail d JOIN FETCH d.musicSummary s WHERE s.vendor = :vendor")
    List<MusicDetail> findByVendorWithSummary(@Param("vendor") Vendor vendor);
}
