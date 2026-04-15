package org.example.repository;

import org.example.model.Appointment;
import org.example.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {

    /** Used for conflict checking — all non-cancelled appointments for a provider. */
    List<Appointment> findByProviderIdAndStatusNot(String providerId, AppointmentStatus status);

    List<Appointment> findByClientIdOrderByStartTimeAsc(String clientId);

    List<Appointment> findByProviderIdOrderByStartTimeAsc(String providerId);

    List<Appointment> findByStartTimeBetweenOrderByStartTimeAsc(LocalDateTime start, LocalDateTime end);

    List<Appointment> findAllByOrderByStartTimeAsc();

    /** Used for confirmed appointments in a time window. */
    List<Appointment> findByStatusAndStartTimeBetween(AppointmentStatus status, LocalDateTime start, LocalDateTime end);

    Optional<Appointment> findByCancelToken(String cancelToken);

    void deleteByClientId(String clientId);
    void deleteByProviderId(String providerId);
    void deleteByServiceId(String serviceId);
}

