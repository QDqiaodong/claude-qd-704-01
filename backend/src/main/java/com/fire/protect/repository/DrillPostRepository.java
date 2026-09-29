package com.fire.protect.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fire.protect.domain.DrillPost;

public interface DrillPostRepository extends JpaRepository<DrillPost, Long> {

    List<DrillPost> findBySessionIdOrderBySortNoAscIdAsc(Long sessionId);

    Optional<DrillPost> findBySessionIdAndPostCode(Long sessionId, String postCode);

    @Modifying
    @Query("delete from DrillPost p where p.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") Long sessionId);
}
