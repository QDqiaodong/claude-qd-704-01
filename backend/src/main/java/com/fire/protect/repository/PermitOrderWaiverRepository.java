package com.fire.protect.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.PermitOrderWaiver;

public interface PermitOrderWaiverRepository extends JpaRepository<PermitOrderWaiver, Long> {

    List<PermitOrderWaiver> findByPermitIdOrderByIdAsc(Long permitId);

    Optional<PermitOrderWaiver> findByPermitIdAndOrderId(Long permitId, Long orderId);
}
