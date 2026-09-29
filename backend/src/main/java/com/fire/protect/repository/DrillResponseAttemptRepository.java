package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.DrillResponseAttempt;

public interface DrillResponseAttemptRepository extends JpaRepository<DrillResponseAttempt, Long> {

    List<DrillResponseAttempt> findBySessionIdOrderByIdAsc(Long sessionId);

    List<DrillResponseAttempt> findByCardIdOrderByIdAsc(Long cardId);

    List<DrillResponseAttempt> findByPostIdOrderByIdAsc(Long postId);
}
