package com.gedavocat.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Demande de suppression d'un document par un client.
 * L'avocat doit valider ou rejeter la demande.
 */
@Entity
@Table(name = "document_deletion_requests", indexes = {
    @Index(name = "idx_ddr_document", columnList = "document_id"),
    @Index(name = "idx_ddr_requester", columnList = "requested_by_id"),
    @Index(name = "idx_ddr_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"document", "requestedBy", "reviewedBy"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DocumentDeletionRequest {

    @Id
    @Column(length = 36)
    @EqualsAndHashCode.Include
    private String id = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @JsonIgnore
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    @JsonIgnore
    private User requestedBy;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    @JsonIgnore
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum RequestStatus {
        PENDING("En attente"),
        APPROVED("Approuvée"),
        REJECTED("Rejetée");

        private final String displayName;

        RequestStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
