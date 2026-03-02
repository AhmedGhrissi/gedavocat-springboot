package com.gedavocat.controller;

import com.gedavocat.model.Notification;
import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API REST pour les notifications (dropdown cloche dans la topbar).
 */
@Controller
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * GET /api/notifications — Retourne les notifications récentes + count non lues.
     */
    @GetMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) return ResponseEntity.ok(Map.of("unreadCount", 0, "notifications", List.of()));

        long unreadCount = notificationService.countUnread(user.getId());
        List<Notification> notifications = notificationService.getRecentNotifications(user.getId());

        List<Map<String, Object>> items = notifications.stream()
                .limit(15)
                .map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", n.getId());
                    m.put("title", n.getTitle());
                    m.put("message", n.getMessage());
                    m.put("link", n.getLink());
                    m.put("icon", n.getIcon());
                    m.put("color", n.getColor());
                    m.put("read", n.isRead());
                    m.put("time", formatRelativeTime(n.getCreatedAt()));
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("unreadCount", unreadCount, "notifications", items));
    }

    /**
     * POST /api/notifications/mark-read — Marquer toutes comme lues.
     */
    @PostMapping("/mark-read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) return ResponseEntity.ok(Map.of("success", false));
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * POST /api/notifications/{id}/read — Marquer une notification comme lue.
     */
    @PostMapping("/{id}/read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markRead(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        // SEC-IDOR FIX : passer l'ID utilisateur pour vérification ownership
        User user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);
        if (user == null) return ResponseEntity.ok(Map.of("success", false));
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ── Helper ──

    private String formatRelativeTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        long minutes = ChronoUnit.MINUTES.between(dateTime, LocalDateTime.now());
        if (minutes < 1) return "À l'instant";
        if (minutes < 60) return "Il y a " + minutes + " min";
        long hours = ChronoUnit.HOURS.between(dateTime, LocalDateTime.now());
        if (hours < 24) return "Il y a " + hours + " h";
        long days = ChronoUnit.DAYS.between(dateTime, LocalDateTime.now());
        if (days < 7) return "Il y a " + days + " j";
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
