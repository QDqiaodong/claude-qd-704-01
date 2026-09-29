package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DisableMeasure;

public interface DisableMeasureRepository extends JpaRepository<DisableMeasure, Long> {

    List<DisableMeasure> findByPermitIdOrderByIdAsc(Long permitId);

    long countByPermitId(Long permitId);

    long countByPermitIdAndConfirmedAtIsNull(Long permitId);

    void deleteByPermitId(Long permitId);
}
