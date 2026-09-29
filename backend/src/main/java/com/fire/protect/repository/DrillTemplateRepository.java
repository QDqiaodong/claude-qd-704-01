package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillTemplate;

public interface DrillTemplateRepository extends JpaRepository<DrillTemplate, Long> {

    List<DrillTemplate> findAllByOrderByIdDesc();

    boolean existsByName(String name);
}
