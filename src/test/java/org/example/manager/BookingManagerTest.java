package org.example.manager;

import org.example.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingManagerTest {

    @Autowired
    BookingManager manager;

    private Client client;
    private Client otherClient;
    private Provider provider;
    private Service service;
    private LocalDate bookingDate;

    @BeforeEach
    void seed() {
        bookingDate = LocalDate.now().plusDays(14);
        service = manager.addService("Consultation", "30 min session", 30, 50.0);
        provider = manager.addProvider("Alex", "alex@test.com", "555-0001",
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        manager.assignServiceToProvider(provider.getId(), service.getId());
        client = manager.addClient("Jordan Lee", "jordan@test.com", "555-1000");
        otherClient = manager.addClient("Sam Kim", "sam@test.com", "555-2000");
    }

    @Test
    void bookAppointment_succeedsAndStoresReminderChannel() {
        LocalDateTime start = LocalDateTime.of(bookingDate, LocalTime.of(10, 0));

        Appointment appt = manager.bookAppointment(
                client.getId(), provider.getId(), service.getId(), start, "note",
                ReminderChannel.SMS, "+15551234567")
                .orElseThrow();

        assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appt.getNotes()).isEqualTo("note");
        assertThat(appt.getReminderChannel()).isEqualTo(ReminderChannel.SMS);
        assertThat(appt.getReminderPhone()).isEqualTo("+15551234567");
        assertThat(appt.getEndTime()).isEqualTo(start.plusMinutes(30));
    }

    @Test
    void bookAppointment_rejectsOverlappingSlot() {
        LocalDateTime start = LocalDateTime.of(bookingDate, LocalTime.of(11, 0));
        assertThat(manager.bookAppointment(client.getId(), provider.getId(), service.getId(), start))
                .isPresent();

        assertThat(manager.bookAppointment(otherClient.getId(), provider.getId(), service.getId(), start))
                .isEmpty();
    }

    @Test
    void cancelAppointment_allowsRebookingSameSlot() {
        LocalDateTime start = LocalDateTime.of(bookingDate, LocalTime.of(13, 0));
        String id = manager.bookAppointment(client.getId(), provider.getId(), service.getId(), start)
                .orElseThrow().getId();

        assertThat(manager.cancelAppointment(id)).isTrue();

        assertThat(manager.bookAppointment(otherClient.getId(), provider.getId(), service.getId(), start))
                .isPresent();
    }

    @Test
    void getAvailableSlots_omitsBookedTimes() {
        LocalDateTime booked = LocalDateTime.of(bookingDate, LocalTime.of(10, 0));
        manager.bookAppointment(client.getId(), provider.getId(), service.getId(), booked);

        List<LocalDateTime> slots = manager.getAvailableSlots(
                provider.getId(), service.getId(), bookingDate);

        assertThat(slots).doesNotContain(booked);
        assertThat(slots).contains(LocalDateTime.of(bookingDate, LocalTime.of(9, 0)));
    }

    @Test
    void getAppointmentsFiltered_appliesAllCriteriaTogether() {
        LocalDateTime jordanSlot = LocalDateTime.of(bookingDate, LocalTime.of(10, 0));
        LocalDateTime samSlot = LocalDateTime.of(bookingDate, LocalTime.of(14, 0));
        LocalDate otherDay = bookingDate.plusDays(1);
        LocalDateTime jordanOtherDay = LocalDateTime.of(otherDay, LocalTime.of(10, 0));

        manager.bookAppointment(client.getId(), provider.getId(), service.getId(), jordanSlot);
        manager.bookAppointment(otherClient.getId(), provider.getId(), service.getId(), samSlot);
        manager.bookAppointment(client.getId(), provider.getId(), service.getId(), jordanOtherDay);

        List<Appointment> filtered = manager.getAppointmentsFiltered(
                bookingDate, provider.getId(), client.getId());

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getClient().getId()).isEqualTo(client.getId());
        assertThat(filtered.get(0).getStartTime()).isEqualTo(jordanSlot);
    }

    @Test
    void updateAppointmentNotes_persists() {
        LocalDateTime start = LocalDateTime.of(bookingDate, LocalTime.of(15, 0));
        String id = manager.bookAppointment(client.getId(), provider.getId(), service.getId(), start)
                .orElseThrow().getId();

        assertThat(manager.updateAppointmentNotes(id, "Follow up needed")).isTrue();

        assertThat(manager.findAppointmentById(id).orElseThrow().getNotes())
                .isEqualTo("Follow up needed");
    }

    @Test
    void completeAppointment_onlyWhenConfirmed() {
        LocalDateTime start = LocalDateTime.of(bookingDate, LocalTime.of(16, 0));
        String id = manager.bookAppointment(client.getId(), provider.getId(), service.getId(), start)
                .orElseThrow().getId();

        assertThat(manager.completeAppointment(id)).isTrue();
        assertThat(manager.findAppointmentById(id).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(manager.completeAppointment(id)).isFalse();
    }
}
