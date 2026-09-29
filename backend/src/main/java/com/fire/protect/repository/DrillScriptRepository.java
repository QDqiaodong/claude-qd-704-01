package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillScript;

public interface DrillScriptRepository extends JpaRepository<DrillScript, Long> {

    List<DrillScript> findAllByOrderByIdDesc();

    boolean existsByName(String name);
}
