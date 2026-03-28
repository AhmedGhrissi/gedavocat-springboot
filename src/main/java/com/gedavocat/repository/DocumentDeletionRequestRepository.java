package com.gedavocat.repository;

import com.gedavocat.model.DocumentDeletionRequest;
import com.gedavocat.model.DocumentDeletionRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentDeletionRequestRepository extends JpaRepository<DocumentDeletionRequest, String> {

    /** Demandes en attente pour un avocat (via les documents de ses clients) */
    @Query("SELECT ddr FROM DocumentDeletionRequest ddr " +
           "JOIN FETCH ddr.document d " +
           "JOIN FETCH ddr.requestedBy " +
           "WHERE d.caseEntity.lawyer.id = :lawyerId " +
           "AND ddr.status = :status " +
           "ORDER BY ddr.createdAt DESC")
    List<DocumentDeletionRequest> findByLawyerAndStatus(
            @Param("lawyerId") String lawyerId,
            @Param("status") RequestStatus status);

    /** Vérifier si une demande PENDING existe déjà pour ce document */
    boolean existsByDocumentIdAndStatus(String documentId, RequestStatus status);

    /** Demandes faites par un client */
    @Query("SELECT ddr FROM DocumentDeletionRequest ddr " +
           "JOIN FETCH ddr.document " +
           "WHERE ddr.requestedBy.id = :userId " +
           "ORDER BY ddr.createdAt DESC")
    List<DocumentDeletionRequest> findByRequestedById(@Param("userId") String userId);
}
