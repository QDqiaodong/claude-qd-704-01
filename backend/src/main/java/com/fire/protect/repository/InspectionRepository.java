package com.fire.protect.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.Inspection;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    List<Inspection> findAllByBuildingId(Long buildingId, Sort sort);

    Optional<Inspection> findByInspectNo(String inspectNo);

    long countByStatus(String status);
}
