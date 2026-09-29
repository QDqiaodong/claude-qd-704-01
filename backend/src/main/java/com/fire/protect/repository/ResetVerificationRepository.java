package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.ResetVerification;

public interface ResetVerificationRepository extends JpaRepository<ResetVerification, Long> {

    List<ResetVerification> findByPermitIdOrderByIdAsc(Long permitId);

    List<ResetVerification> findByPermitIdAndBatchNoOrderByIdAsc(Long permitId, String batchNo);
}
