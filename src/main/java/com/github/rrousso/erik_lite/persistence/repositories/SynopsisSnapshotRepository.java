package com.github.rrousso.erik_lite.persistence.repositories;

import com.github.rrousso.erik_lite.persistence.entities.SynopsisSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SynopsisSnapshotRepository extends JpaRepository<SynopsisSnapshot, Long> {

    /** Get the latest synopsis for a stanza (current state). */
    Optional<SynopsisSnapshot> findFirstByStanzaIdOrderByExchangeNumberDesc(Long stanzaId);

    /** Get the latest synopsis BEFORE a given exchange (for revert). */
    @Query("SELECT s FROM SynopsisSnapshot s WHERE s.stanza.id = :stanzaId AND s.exchangeNumber < :exchangeNumber ORDER BY s.exchangeNumber DESC LIMIT 1")
    Optional<SynopsisSnapshot> findLatestBefore(@Param("stanzaId") Long stanzaId, @Param("exchangeNumber") int exchangeNumber);
}