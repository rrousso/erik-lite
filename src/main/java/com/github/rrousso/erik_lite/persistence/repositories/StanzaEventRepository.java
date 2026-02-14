package com.github.rrousso.erik_lite.persistence.repositories;

import com.github.rrousso.erik_lite.persistence.entities.StanzaEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StanzaEventRepository extends JpaRepository<StanzaEvent, Long> {

    List<StanzaEvent> findByStanzaId(Long stanzaId);

    List<StanzaEvent> findByStanzaIdAndBeatNumber(Long stanzaId, Integer beatNumber);

    List<StanzaEvent> findByStanzaIdAndIsMajorTrue(Long stanzaId);
}