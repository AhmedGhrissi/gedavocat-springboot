package com.gedavocat.service;

import com.gedavocat.model.Notification;
import com.gedavocat.model.User;
import com.gedavocat.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service de gestion des notifications in-app.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Crée une notification pour un utilisateur.
     */
    @Transactional
    public Notification create(User user, String type, String title, String message, String link, String icon, String color) {
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        n.setIcon(icon);
        n.setColor(color);
        return notificationRepository.save(n);
    }

    // ── Raccourcis pour les types fréquents ──

    public void notifySignaturePending(User lawyer, String signerName, String documentName, String signatureId) {
        create(lawyer,
                "SIGNATURE_PENDING",
                "Signature envoyée",
                "Demande de signature envoyée à " + signerName + " pour « " + documentName + " »",
                "/signatures/" + signatureId,
                "fa-file-signature",
                "warning");
    }

    public void notifySignatureSigned(User lawyer, String signerName, String documentName, String signatureId) {
        create(lawyer,
                "SIGNATURE_SIGNED",
                "Document signé !",
                signerName + " a signé « " + documentName + " »",
                "/signatures/" + signatureId,
                "fa-check-circle",
                "success");
    }

    public void notifySignatureRejected(User lawyer, String signerName, String documentName, String signatureId) {
        create(lawyer,
                "SIGNATURE_REJECTED",
                "Signature refusée",
                signerName + " a refusé de signer « " + documentName + " »",
                "/signatures/" + signatureId,
                "fa-times-circle",
                "danger");
    }

    public void notifyClientSignatureRequest(User clientUser, String lawyerName, String documentName) {
        create(clientUser,
                "SIGNATURE_REQUEST",
                "Signature demandée",
                "Me " + lawyerName + " vous demande de signer « " + documentName + " »",
                "/my-signatures",
                "fa-file-signature",
                "primary");
    }

    public void notifyDocumentUploaded(User user, String documentName, String caseId) {
        create(user,
                "DOCUMENT_UPLOADED",
                "Nouveau document",
                "Le document « " + documentName + " » a été ajouté à votre dossier",
                "/my-cases/" + caseId,
                "fa-file-upload",
                "primary");
    }

    public void notifyAppointmentCreated(User user, String lawyerName, String dateStr) {
        create(user,
                "APPOINTMENT_CREATED",
                "Nouveau rendez-vous",
                "Rendez-vous avec Me " + lawyerName + " le " + dateStr,
                "/my-appointments",
                "fa-calendar-check",
                "primary");
    }

    // ── Lecture ──

    @Transactional(readOnly = true)
    public List<Notification> getRecentNotifications(String userId) {
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(String userId) {
        return notificationRepository.findUnreadByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(String userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsRead(userId);
    }

    /**
     * SEC-IDOR FIX : vérification ownership avant marquage comme lu
     */
    @Transactional
    public void markAsRead(String notificationId, String userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            // SÉCURITÉ : vérifier que la notification appartient bien à l'utilisateur
            if (n.getUser() != null && !n.getUser().getId().equals(userId)) {
                throw new SecurityException("Accès non autorisé à cette notification");
            }
            n.setRead(true);
            notificationRepository.save(n);
        });
    }
}
