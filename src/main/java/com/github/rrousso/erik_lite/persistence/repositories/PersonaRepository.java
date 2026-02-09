package com.github.rrousso.erik_lite.persistence.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.github.rrousso.erik_lite.persistence.entities.Persona;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, Long> {

}