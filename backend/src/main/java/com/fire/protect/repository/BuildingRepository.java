package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.Building;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    List<Building> findAllByOrderByCodeAsc();

    Building findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByName(String name);
}
