package com.gedavocat.repository;

import com.gedavocat.model.FirmMember;
import com.gedavocat.model.FirmMember.FirmRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour l'entité FirmMember
 * MULTI-TENANT: Isolation automatique via filtre Hibernate
 * 
 * Sécurité :
 * - Toutes les requêtes sont automatiquement filtrées par firm_id
 * - Utilisation du filtre "firmFilter" activé par MultiTenantFilter
 * - Prévention des fuites de données cross-tenant
 * 
 * @author DocAvocat Security Team
 * @version 1.0
 */
@Repository
public interface FirmMemberRepository extends JpaRepository<FirmMember, String> {

    /**
     * Trouve tous les membres actifs d'un cabinet
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT fm FROM FirmMember fm WHERE fm.firm.id = :firmId AND fm.isActive = true")
    List<FirmMember> findActiveMembersByFirmId(@Param("firmId") String firmId);

    /**
     * Trouve tous les membres d'un cabinet (actifs et inactifs)
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT fm FROM FirmMember fm WHERE fm.firm.id = :firmId")
    List<FirmMember> findAllByFirmId(@Param("firmId") String firmId);

    /**
     * Trouve un membre spécifique par cabinet et utilisateur
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT fm FROM FirmMember fm WHERE fm.firm.id = :firmId AND fm.user.id = :userId")
    Optional<FirmMember> findByFirmIdAndUserId(@Param("firmId") String firmId, @Param("userId") String userId);

    /**
     * Trouve les administrateurs actifs d'un cabinet
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT fm FROM FirmMember fm WHERE fm.firm.id = :firmId AND fm.role = 'ADMIN' AND fm.isActive = true")
    List<FirmMember> findAdminsByFirmId(@Param("firmId") String firmId);

    /**
     * Trouve les membres par rôle
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT fm FROM FirmMember fm WHERE fm.firm.id = :firmId AND fm.role = :role AND fm.isActive = true")
    List<FirmMember> findByFirmIdAndRole(@Param("firmId") String firmId, @Param("role") FirmRole role);

    /**
     * Compte le nombre de membres actifs dans un cabinet
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT COUNT(fm) FROM FirmMember fm WHERE fm.firm.id = :firmId AND fm.isActive = true")
    long countActiveMembersByFirmId(@Param("firmId") String firmId);

    /**
     * Vérifie si un utilisateur est membre actif d'un cabinet
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT CASE WHEN COUNT(fm) > 0 THEN true ELSE false END FROM FirmMember fm " +
           "WHERE fm.firm.id = :firmId AND fm.user.id = :userId AND fm.isActive = true")
    boolean existsActiveMember(@Param("firmId") String firmId, @Param("userId") String userId);

    /**
     * Vérifie si un utilisateur est administrateur d'un cabinet
     * MULTI-TENANT: Filtre automatique sur firm_id
     */
    @Query("SELECT CASE WHEN COUNT(fm) > 0 THEN true ELSE false END FROM FirmMember fm " +
           "WHERE fm.firm.id = :firmId AND fm.user.id = :userId AND fm.role = 'ADMIN' AND fm.isActive = true")
    boolean isAdmin(@Param("firmId") String firmId, @Param("userId") String userId);

    /**
     * Trouve tous les cabinets dont un utilisateur est membre
     * Utile pour gérer les utilisateurs multi-cabinets
     */
    @Query("SELECT fm FROM FirmMember fm WHERE fm.user.id = :userId AND fm.isActive = true")
    List<FirmMember> findFirmsByUserId(@Param("userId") String userId);

    /**
     * Trouve les membres ajoutés par un utilisateur spécifique
     * AUDIT: Traçabilité des actions
     */
    @Query("SELECT fm FROM FirmMember fm WHERE fm.firm.id = :firmId AND fm.addedByUser.id = :userId")
    List<FirmMember> findMembersAddedBy(@Param("firmId") String firmId, @Param("userId") String userId);
}
