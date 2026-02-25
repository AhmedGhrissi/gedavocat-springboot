package com.gedavocat.repository;

import com.gedavocat.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    /** Les 20 dernières notifications d'un utilisateur (lues et non lues) */
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId ORDER BY n.createdAt DESC")
    List<Notification> findTop20ByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    /** Nombre de notifications non lues */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.read = false")
    long countUnreadByUserId(@Param("userId") String userId);

    /** Notifications non lues (pour le dropdown) */
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.read = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByUserId(@Param("userId") String userId);

    /** Marquer toutes les notifications d'un utilisateur comme lues */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    void markAllAsRead(@Param("userId") String userId);

    /** Supprimer les anciennes notifications (nettoyage) */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteByUserId(@Param("userId") String userId);
}
