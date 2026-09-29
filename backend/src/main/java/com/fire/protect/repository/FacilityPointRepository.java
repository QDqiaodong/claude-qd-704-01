package com.fire.protect.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.FacilityPoint;

public interface FacilityPointRepository extends JpaRepository<FacilityPoint, Long> {

    List<FacilityPoint> findAllByBuildingId(Long buildingId, Sort sort);

    List<FacilityPoint> findAllByBuildingIdAndFloorId(Long buildingId, Long floorId, Sort sort);

    /** @PrePersist 里取同前缀的既有编码，用于推算下一个四位序号 */
    List<FacilityPoint> findByPointCodeStartingWith(String prefix);

    Optional<FacilityPoint> findByPointCode(String pointCode);

    long countByBuildingId(Long buildingId);

    long countByBuildingIdAndStatus(Long buildingId, String status);

    long countByStatus(String status);

    long countByNextCheckDateBefore(LocalDate date);

    List<FacilityPoint> findByIdIn(List<Long> ids);

    List<FacilityPoint> findByActiveDisablePermitId(Long permitId);
}
