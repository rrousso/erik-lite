package com.github.rrousso.erik_lite.persistence.repositories;

import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StanzaRepository extends JpaRepository<Stanza, Long> {

    List<Stanza> findByPersonaId(Long personaId);

    @Query("SELECT s FROM Stanza s WHERE s.persona.id = :personaId AND s.status = 'active'")
    Stanza findActiveByPersonaId(@Param("personaId") Long personaId);

    List<Stanza> findByStatus(String status);

    List<Stanza> findByWorldIdentifier(String worldIdentifier);

    @Query("SELECT s FROM Stanza s WHERE LOWER(s.setting) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stanza> searchBySetting(@Param("keyword") String keyword);

    @Query("SELECT s FROM Stanza s WHERE LOWER(s.premise) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stanza> searchByPremise(@Param("keyword") String keyword);

    @Query("SELECT s FROM Stanza s WHERE LOWER(s.tone) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stanza> searchByTone(@Param("keyword") String keyword);

    @Query(value = "SELECT * FROM stanzas WHERE search_vector @@ to_tsquery('english', :query)",
           nativeQuery = true)
    List<Stanza> fullTextSearch(@Param("query") String query);

    @Query("SELECT s FROM Stanza s WHERE s.persona.id = :personaId AND s.status = 'completed' ORDER BY s.createdAt DESC")
    List<Stanza> findCompletedByPersonaId(@Param("personaId") Long personaId);

    @Query("SELECT DISTINCT s FROM Stanza s JOIN s.characters c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stanza> searchByCharacter(@Param("keyword") String keyword);
}