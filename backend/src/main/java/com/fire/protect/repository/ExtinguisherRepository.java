package com.fire.protect.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.Extinguisher;

public interface ExtinguisherRepository extends JpaRepository<Extinguisher, Long> {

    List<Extinguisher> findAllByBuildingId(Long buildingId, Sort sort);

    /** @PrePersist 里取同前缀的既有编码 */
    List<Extinguisher> findByPointCodeStartingWith(String prefix);

    Optional<Extinguisher> findBySteelNo(String steelNo);

    long countByStatus(String status);

    long countByHydroTestDueBefore(LocalDate date);

    long countByScrapDateBefore(LocalDate date);
}
