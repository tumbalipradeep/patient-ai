package com.patientcase.kiosk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RedFlagRepository extends JpaRepository<RedFlag, Long> {

    List<RedFlag> findTop10ByResolvedFalseOrderByCreatedAtDesc();

    List<RedFlag> findByIntakeIdOrderByCreatedAtAsc(Long intakeId);

    List<RedFlag> findByIntakeId(Long intakeId);

    List<RedFlag> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    long countByResolvedFalse();
}