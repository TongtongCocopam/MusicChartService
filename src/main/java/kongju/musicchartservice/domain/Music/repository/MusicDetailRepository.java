package kongju.musicchartservice.domain.Music.repository;

import java.util.List;

import kongju.musicchartservice.domain.Music.entity.MusicDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


public interface MusicDetailRepository extends JpaRepository<MusicDetail, Long> {
    @Query("SELECT md FROM MusicDetail md " +
            "JOIN MusicSummary ms " +
            "WHERE ms.vendor = :vendor")
    List<MusicDetail> findByVendor(String vendor);

    @Query("SELECT md FROM MusicDetail md " +
            "JOIN MusicSummary ms " +
            "WHERE ms.vendor = :vendor " +
            "and ms.songId= :musicId")
    MusicDetail findByVendorAndMusicId(String vendor, String musicId);
}
