package com.github.rrousso.erik_lite.persistence.repositories;

import com.github.rrousso.erik_lite.persistence.entities.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    List<Chat> findByPersonaIdAndStatusOrderByCreatedAtDesc(Long personaId, String status);

    List<Chat> findByStanzaId(Long stanzaId);
}