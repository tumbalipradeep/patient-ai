package com.patientcase.appointment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientIdOrderByAppointmentDatetimeDesc(Long patientId);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.clinician " +
           "WHERE a.appointmentDatetime >= :from AND a.appointmentDatetime < :to " +
           "ORDER BY a.appointmentDatetime ASC")
    List<Appointment> findTodayAppointments(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.clinician " +
           "WHERE a.appointmentDatetime >= :from " +
           "AND a.status IN ('SCHEDULED', 'CONFIRMED') " +
           "ORDER BY a.appointmentDatetime ASC")
    List<Appointment> findUpcomingAppointments(@Param("from") LocalDateTime from, Pageable pageable);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE " +
           "a.appointmentDatetime >= :from AND a.appointmentDatetime < :to")
    long countTodayAppointments(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.patient JOIN FETCH a.clinician ORDER BY a.appointmentDatetime DESC")
    Page<Appointment> findAllWithDetails(Pageable pageable);
}
