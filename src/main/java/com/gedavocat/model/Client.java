package com.gedavocat.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Entité représentant un client du cabinet d'avocats
 */
@Entity
@Table(name = "clients", indexes = {
    @Index(name = "idx_lawyer_id", columnList = "lawyer_id"),
    @Index(name = "idx_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"lawyer", "clientUser", "cases"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Client {
    
    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id", nullable = false)
    private User lawyer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_user_id")
    private User clientUser;
    
    @NotBlank(message = "Le nom est obligatoire")
    @Column(nullable = false, length = 100)
    private String name;
    
    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Email invalide")
    @Column(nullable = false, length = 255)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    @Column(columnDefinition = "TEXT")
    private String address;
    
    @Column(name = "access_ends_at")
    private LocalDateTime accessEndsAt;
    
    @Column(name = "invitation_id", length = 36)
    private String invitationId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Relations
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Case> cases = new HashSet<>();
    
    // Méthodes utilitaires
    public boolean hasActiveAccess() {
        return accessEndsAt == null || accessEndsAt.isAfter(LocalDateTime.now());
    }
    
    @PrePersist
    public void prePersist() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }
}
