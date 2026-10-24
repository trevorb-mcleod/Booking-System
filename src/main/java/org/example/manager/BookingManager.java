package org.example.manager;

import org.example.model.*;
import org.example.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Core booking engine — all data is persisted to SQLite via Spring Data JPA.
 */
@Component
@Transactional
public class BookingManager {

    private final ServiceRepository     serviceRepo;
    private final ProviderRepository    providerRepo;
    private final ClientRepository      clientRepo;
    private final AppointmentRepository appointmentRepo;

    public BookingManager(ServiceRepository serviceRepo,
                          ProviderRepository providerRepo,
                          ClientRepository clientRepo,
                          AppointmentRepository appointmentRepo) {
        this.serviceRepo     = serviceRepo;
        this.providerRepo    = providerRepo;
        this.clientRepo      = clientRepo;
        this.appointmentRepo = appointmentRepo;
    }

    // ── Services ─────────────────────────────────────────────────────────────

    public Service addService(String name, String description,
                              int durationMinutes, double price) {
        return serviceRepo.save(new Service(name, description, durationMinutes, price));
    }

    public Optional<Service> findServiceById(String id) {
        return serviceRepo.findById(id);
    }

    public List<Service> getAllServices() {
        return serviceRepo.findAll();
    }

    public boolean updateService(String id, String name, String description,
                                 int durationMinutes, double price) {
        return serviceRepo.findById(id).map(s -> {
            s.setName(name);
            s.setDescription(description);
            s.setDurationMinutes(durationMinutes);
            s.setPrice(price);
            serviceRepo.save(s);
            return true;
        }).orElse(false);
    }

    public boolean removeService(String id) {
        if (!serviceRepo.existsById(id)) return false;
        appointmentRepo.deleteByServiceId(id);
        serviceRepo.deleteById(id);
        return true;
    }

    // ── Providers ────────────────────────────────────────────────────────────

    public Provider addProvider(String name, String email, String phone,
                                LocalTime workStart, LocalTime workEnd) {
        return providerRepo.save(new Provider(name, email, phone, workStart, workEnd));
    }

    public Optional<Provider> findProviderById(String id) {
        return providerRepo.findById(id);
    }

    public List<Provider> getAllProviders() {
        return providerRepo.findAll();
    }

    public boolean removeProvider(String id) {
        if (!providerRepo.existsById(id)) return false;
        appointmentRepo.deleteByProviderId(id);
        providerRepo.deleteById(id);
        return true;
    }

    public boolean updateProvider(String id, String name, String email, String phone,
                                  LocalTime workStart, LocalTime workEnd) {
        return providerRepo.findById(id).map(p -> {
            p.setName(name);
            p.setEmail(email);
            p.setPhone(phone);
            p.setWorkdayStart(workStart);
            p.setWorkdayEnd(workEnd);
            providerRepo.save(p);
            return true;
        }).orElse(false);
    }

    public boolean assignServiceToProvider(String providerId, String serviceId) {
        Optional<Provider> pOpt = providerRepo.findById(providerId);
        if (pOpt.isEmpty() || !serviceRepo.existsById(serviceId)) return false;
        Provider p = pOpt.get();
        p.addService(serviceId);
        providerRepo.save(p);
        return true;
    }

    // ── Clients ──────────────────────────────────────────────────────────────

    public Client addClient(String name, String email, String phone) {
        return clientRepo.save(new Client(name, email, phone));
    }

    public Optional<Client> findClientById(String id) {
        return clientRepo.findById(id);
    }

    public Optional<Client> findClientByEmail(String email) {
        return clientRepo.findByEmailIgnoreCase(email);
    }

    public List<Client> getAllClients() {
        return clientRepo.findAll();
    }

    public boolean removeClient(String id) {
        if (!clientRepo.existsById(id)) return false;
        appointmentRepo.deleteByClientId(id);
        clientRepo.deleteById(id);
        return true;
    }

    // ── Availability ─────────────────────────────────────────────────────────

    public List<LocalDateTime> getAvailableSlots(String providerId,
                                                  String serviceId,
                                                  LocalDate date) {
        Optional<Provider> pOpt = providerRepo.findById(providerId);
        Optional<Service>  sOpt = serviceRepo.findById(serviceId);
        if (pOpt.isEmpty() || sOpt.isEmpty()) return Collections.emptyList();
        if (date.isBefore(LocalDate.now()))   return Collections.emptyList();

        Provider provider = pOpt.get();
        Service  service  = sOpt.get();

        List<Appointment> existing =
                appointmentRepo.findByProviderIdAndStatusNot(providerId, AppointmentStatus.CANCELLED);

        List<LocalDateTime> slots  = new ArrayList<>();
        LocalDateTime       cursor = LocalDateTime.of(date, provider.getWorkdayStart());
        LocalDateTime       dayEnd = LocalDateTime.of(date, provider.getWorkdayEnd());

        while (!cursor.plusMinutes(service.getDurationMinutes()).isAfter(dayEnd)) {
            final LocalDateTime slotStart = cursor;
            final LocalDateTime slotEnd   = cursor.plusMinutes(service.getDurationMinutes());

            boolean conflict = existing.stream()
                    .anyMatch(a -> slotStart.isBefore(a.getEndTime())
                                && slotEnd.isAfter(a.getStartTime()));

            if (!conflict) slots.add(slotStart);
            cursor = cursor.plusMinutes(30);
        }
        return slots;
    }

    // ── Appointments ─────────────────────────────────────────────────────────

    public Optional<Appointment> bookAppointment(String clientId, String providerId,
                                                  String serviceId,
                                                  LocalDateTime startTime) {
        return bookAppointment(clientId, providerId, serviceId, startTime, "");
    }

    public Optional<Appointment> bookAppointment(String clientId, String providerId,
                                                  String serviceId,
                                                  LocalDateTime startTime,
                                                  String notes) {
        return bookAppointment(clientId, providerId, serviceId, startTime, notes, ReminderChannel.EMAIL);
    }

    public Optional<Appointment> bookAppointment(String clientId, String providerId,
                                                  String serviceId,
                                                  LocalDateTime startTime,
                                                  String notes,
                                                  ReminderChannel reminderChannel) {
        return bookAppointment(clientId, providerId, serviceId, startTime, notes, reminderChannel, null);
    }

    public Optional<Appointment> bookAppointment(String clientId, String providerId,
                                                  String serviceId,
                                                  LocalDateTime startTime,
                                                  String notes,
                                                  ReminderChannel reminderChannel,
                                                  String reminderPhone) {
        Optional<Client>   cOpt = clientRepo.findById(clientId);
        Optional<Provider> pOpt = providerRepo.findById(providerId);
        Optional<Service>  sOpt = serviceRepo.findById(serviceId);
        if (cOpt.isEmpty() || pOpt.isEmpty() || sOpt.isEmpty()) return Optional.empty();

        Service service = sOpt.get();
        LocalDateTime endTime = startTime.plusMinutes(service.getDurationMinutes());

        boolean conflict = appointmentRepo
                .findByProviderIdAndStatusNot(providerId, AppointmentStatus.CANCELLED)
                .stream()
                .anyMatch(a -> startTime.isBefore(a.getEndTime())
                            && endTime.isAfter(a.getStartTime()));

        if (conflict) return Optional.empty();

        Appointment appt = new Appointment(cOpt.get(), pOpt.get(), service, startTime);
        appt.setStatus(AppointmentStatus.CONFIRMED);
        if (notes != null && !notes.isBlank()) appt.setNotes(notes);
        appt.setReminderChannel(reminderChannel != null ? reminderChannel : ReminderChannel.EMAIL);
        if (appt.getReminderChannel() == ReminderChannel.SMS
                && reminderPhone != null && !reminderPhone.isBlank()) {
            appt.setReminderPhone(reminderPhone.trim());
        }
        return Optional.of(appointmentRepo.save(appt));
    }

    public boolean markReminderSent(String appointmentId) {
        return appointmentRepo.findById(appointmentId).map(a -> {
            a.setReminderSent(true);
            appointmentRepo.save(a);
            return true;
        }).orElse(false);
    }

    public boolean cancelAppointment(String appointmentId) {
        return appointmentRepo.findById(appointmentId).map(a -> {
            if (a.getStatus() == AppointmentStatus.CANCELLED) return false;
            a.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepo.save(a);
            return true;
        }).orElse(false);
    }

    public boolean completeAppointment(String appointmentId) {
        return appointmentRepo.findById(appointmentId).map(a -> {
            if (a.getStatus() != AppointmentStatus.CONFIRMED) return false;
            a.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepo.save(a);
            return true;
        }).orElse(false);
    }

    public boolean updateAppointmentNotes(String appointmentId, String notes) {
        return appointmentRepo.findById(appointmentId).map(a -> {
            a.setNotes(notes == null ? "" : notes.trim());
            appointmentRepo.save(a);
            return true;
        }).orElse(false);
    }

    public Optional<Appointment> findAppointmentById(String id) {
        return appointmentRepo.findById(id);
    }

    public Optional<Appointment> findAppointmentByCancelToken(String token) {
        return appointmentRepo.findByCancelToken(token);
    }

    // ── Appointment queries ──────────────────────────────────────────────────

    public List<Appointment> getAppointmentsByClient(String clientId) {
        return appointmentRepo.findByClientIdOrderByStartTimeAsc(clientId);
    }

    public List<Appointment> getAppointmentsByProvider(String providerId) {
        return appointmentRepo.findByProviderIdOrderByStartTimeAsc(providerId);
    }

    public List<Appointment> getAppointmentsByDate(LocalDate date) {
        return appointmentRepo.findByStartTimeBetweenOrderByStartTimeAsc(
                date.atStartOfDay(), date.atTime(LocalTime.MAX));
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepo.findAllByOrderByStartTimeAsc();
    }

    /** Applies date, provider, and client filters together (AND). Omit a criterion by passing null/blank. */
    public List<Appointment> getAppointmentsFiltered(LocalDate date,
                                                     String providerId,
                                                     String clientId) {
        String provider = (providerId != null && !providerId.isBlank()) ? providerId : null;
        String client   = (clientId != null && !clientId.isBlank()) ? clientId : null;

        return appointmentRepo.findAllByOrderByStartTimeAsc().stream()
                .filter(a -> date == null || a.getStartTime().toLocalDate().equals(date))
                .filter(a -> provider == null || a.getProvider().getId().equals(provider))
                .filter(a -> client == null || a.getClient().getId().equals(client))
                .toList();
    }
}
