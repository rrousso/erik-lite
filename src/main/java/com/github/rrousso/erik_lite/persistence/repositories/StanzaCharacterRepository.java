package com.github.rrousso.erik_lite.persistence.repositories;

import com.github.rrousso.erik_lite.persistence.entities.StanzaCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StanzaCharacterRepository extends JpaRepository<StanzaCharacter, Long> {

    List<StanzaCharacter> findByStanzaId(Long stanzaId);

    Optional<StanzaCharacter> findByStanzaIdAndNameIgnoreCase(Long stanzaId, String name);

    List<StanzaCharacter> findByStanzaIdAndPresenceStatus(Long stanzaId, String presenceStatus);
}