package com.fire.protect.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DisablePermitPoint;

public interface DisablePermitPointRepository extends JpaRepository<DisablePermitPoint, Long> {

    List<DisablePermitPoint> findByPermitIdOrderByIdAsc(Long permitId);

    List<DisablePermitPoint> findByPointIdAndMarkedTrue(Long pointId);

    Optional<DisablePermitPoint> findByPermitIdAndPointId(Long permitId, Long pointId);

    long countByPermitId(Long permitId);

    void deleteByPermitId(Long permitId);
}
