package com.patientcase.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPatientNumber(String patientNumber);

    Optional<Patient> findByUserId(Long userId);

    boolean existsByPatientNumber(String patientNumber);

    boolean existsByEmail(String email);

    @Query("SELECT p FROM Patient p WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.patientNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "p.phone LIKE CONCAT('%', :search, '%'))")
    Page<Patient> searchPatients(@Param("search") String search, Pageable pageable);

    long count();
}
