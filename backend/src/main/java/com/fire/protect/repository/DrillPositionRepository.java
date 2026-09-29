package com.fire.protect.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillPosition;

public interface DrillPositionRepository extends JpaRepository<DrillPosition, Long> {

    List<DrillPosition> findByDrillIdOrderBySortNoAscIdAsc(Long drillId);

    Optional<DrillPosition> findByDrillIdAndPosCode(Long drillId, String posCode);
}
