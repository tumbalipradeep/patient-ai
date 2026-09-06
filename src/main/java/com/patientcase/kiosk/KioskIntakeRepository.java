package com.patientcase.kiosk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KioskIntakeRepository extends JpaRepository<KioskIntake, Long> {

    List<KioskIntake> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    Optional<KioskIntake> findFirstByPatientIdAndStatusInOrderByCreatedAtDesc(
            Long patientId, List<KioskIntakeStatus> statuses);

    @Query("SELECT k FROM KioskIntake k JOIN FETCH k.patient p " +
           "WHERE k.status IN :statuses ORDER BY k.createdAt DESC")
    List<KioskIntake> findByStatusIn(@Param("statuses") List<KioskIntakeStatus> statuses);

    long countByStatus(KioskIntakeStatus status);
}