package com.gedavocat.repository;

import com.gedavocat.model.CaseShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseShareLinkRepository extends JpaRepository<CaseShareLink, String> {

    Optional<CaseShareLink> findByToken(String token);

    @Query("SELECT l FROM CaseShareLink l WHERE l.sharedCase.id = :caseId ORDER BY l.createdAt DESC")
    List<CaseShareLink> findByCaseId(@Param("caseId") String caseId);

    @Query("SELECT l FROM CaseShareLink l WHERE l.owner.id = :ownerId ORDER BY l.createdAt DESC")
    List<CaseShareLink> findByOwnerId(@Param("ownerId") String ownerId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM CaseShareLink l WHERE l.sharedCase.id = :caseId")
    void deleteAllByCaseId(@Param("caseId") String caseId);
}
