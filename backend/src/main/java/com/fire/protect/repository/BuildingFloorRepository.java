package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.BuildingFloor;

public interface BuildingFloorRepository extends JpaRepository<BuildingFloor, Long> {

    List<BuildingFloor> findAllByBuildingId(Long buildingId, Sort sort);

    void deleteByBuildingId(Long buildingId);
}
