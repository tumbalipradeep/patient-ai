package com.patientcase.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findByPatientIdOrderByGrantedAtDesc(Long patientId);

    List<Consent> findTop3ByPatientIdOrderByGrantedAtDesc(Long patientId);
}