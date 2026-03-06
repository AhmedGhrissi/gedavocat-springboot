package com.gedavocat.service;

import com.gedavocat.dto.*;
import com.gedavocat.model.*;
import com.gedavocat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service de gestion du cabinet
 * SÉCURITÉ: Multi-tenant, seuls les administrateurs peuvent gérer les membres
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FirmManagementService {

    private final FirmMemberRepository firmMemberRepository;
    private final CaseAssignmentRepository caseAssignmentRepository;
    private final UserRepository userRepository;
    private final CaseRepository caseRepository;
    private final FirmRepository firmRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FirmMemberResponse> getAllMembers(String firmId, String currentUserId) {
        verifyAdminAccess(firmId, currentUserId);
        List<FirmMember> members = firmMemberRepository.findAllByFirmId(firmId);
        return members.stream().map(this::toMemberResponse).collect(Collectors.toList());
    }

    @Transactional
    public FirmMemberResponse addMember(String firmId, AddFirmMemberRequest request, String currentUserId) {
        verifyAdminAccess(firmId, currentUserId);
        request.normalizeEmail();

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable avec cet email"));

        if (firmMemberRepository.findByFirmIdAndUserId(firmId, user.getId()).isPresent()) {
            throw new IllegalArgumentException("Cet utilisateur est déjà membre du cabinet");
        }

        Firm firm = firmRepository.findById(firmId).orElseThrow(() -> new IllegalArgumentException("Cabinet introuvable"));
        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        FirmMember member = new FirmMember();
        member.setId(UUID.randomUUID().toString());
        member.setFirm(firm);
        member.setUser(user);
        member.setRole(request.getRole());
        member.setTitle(request.getTitle());
        member.setSpecialty(request.getSpecialty());
        member.setNotes(request.getNotes());
        member.setIsActive(true);
        member.setAddedByUser(currentUser);

        member = firmMemberRepository.save(member);
        auditService.log("FIRM_MEMBER_ADDED", "FirmMember", member.getId(),
            "Membre ajouté: " + user.getEmail(), currentUserId);
        log.info("Member added to firm {}: {}", firmId, user.getEmail());

        return toMemberResponse(member);
    }

    @Transactional
    public void removeMember(String firmId, String memberId, String currentUserId) {
        verifyAdminAccess(firmId, currentUserId);
        FirmMember member = firmMemberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable"));

        if (!member.getFirm().getId().equals(firmId)) {
            throw new AccessDeniedException("Ce membre n'appartient pas à votre cabinet");
        }

        member.deactivate();
        firmMemberRepository.save(member);
        auditService.log("FIRM_MEMBER_REMOVED", "FirmMember", member.getId(),
            "Membre retiré: " + member.getUser().getEmail(), currentUserId);
        log.info("Member removed from firm {}", firmId);
    }

    @Transactional
    public CaseAssignmentResponse assignCase(String firmId, AssignCaseRequest request, String currentUserId) {
        verifyCanAssignCases(firmId, currentUserId);

        Case caseEntity = caseRepository.findById(request.getCaseId())
            .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable"));
        FirmMember member = firmMemberRepository.findById(request.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable"));

        if (!caseEntity.getFirm().getId().equals(firmId) || !member.getFirm().getId().equals(firmId)) {
            throw new AccessDeniedException("Accès refusé");
        }

        if (caseAssignmentRepository.findByCaseIdAndMemberId(request.getCaseId(), request.getMemberId()).isPresent()) {
            throw new IllegalArgumentException("Ce membre est déjà affecté à ce dossier");
        }

        User currentUser = userRepository.findById(currentUserId).orElseThrow();

        CaseAssignment assignment = new CaseAssignment();
        assignment.setId(UUID.randomUUID().toString());
        assignment.setFirm(caseEntity.getFirm());
        assignment.setCaseEntity(caseEntity);
        assignment.setMember(member);
        assignment.setAssignmentRole(request.getAssignmentRole());
        assignment.setCanRead(request.getCanRead());
        assignment.setCanWrite(request.getCanWrite());
        assignment.setCanUpload(request.getCanUpload());
        assignment.setCanDelete(request.getCanDelete());
        assignment.setCanManagePermissions(request.getCanManagePermissions());
        assignment.setExpiresAt(request.getExpiresAt());
        assignment.setNotes(request.getNotes());
        assignment.setIsActive(true);
        assignment.setAssignedByUser(currentUser);

        assignment = caseAssignmentRepository.save(assignment);
        auditService.log("CASE_ASSIGNED", "CaseAssignment", assignment.getId(),
            "Dossier affecté: " + caseEntity.getTitle(), currentUserId);
        log.info("Case assigned in firm {}", firmId);

        return toAssignmentResponse(assignment);
    }

    @Transactional
    public void unassignCase(String firmId, String assignmentId, String currentUserId) {
        verifyCanAssignCases(firmId, currentUserId);
        CaseAssignment assignment = caseAssignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new IllegalArgumentException("Affectation introuvable"));

        if (!assignment.getFirm().getId().equals(firmId)) {
            throw new AccessDeniedException("Accès refusé");
        }

        assignment.revoke();
        caseAssignmentRepository.save(assignment);
        auditService.log("CASE_UNASSIGNED", "CaseAssignment", assignment.getId(),
            "Affectation révoquée", currentUserId);
    }

    @Transactional(readOnly = true)
    public List<CaseAssignmentResponse> getCaseAssignments(String firmId, String caseId, String currentUserId) {
        verifyMemberAccess(firmId, currentUserId);
        List<CaseAssignment> assignments = caseAssignmentRepository.findActiveByCaseId(caseId, LocalDateTime.now());
        return assignments.stream().map(this::toAssignmentResponse).collect(Collectors.toList());
    }

    private void verifyAdminAccess(String firmId, String userId) {
        if (!firmMemberRepository.isAdmin(firmId, userId)) {
            throw new AccessDeniedException("Seul un administrateur peut effectuer cette action");
        }
    }

    private void verifyCanAssignCases(String firmId, String userId) {
        FirmMember member = firmMemberRepository.findByFirmIdAndUserId(firmId, userId)
            .orElseThrow(() -> new AccessDeniedException("Accès refusé"));
        if (!member.canAssignCases()) {
            throw new AccessDeniedException("Droits insuffisants");
        }
    }

    private void verifyMemberAccess(String firmId, String userId) {
        if (!firmMemberRepository.existsActiveMember(firmId, userId)) {
            throw new AccessDeniedException("Accès refusé");
        }
    }

    private FirmMemberResponse toMemberResponse(FirmMember member) {
        FirmMemberResponse response = new FirmMemberResponse();
        response.setId(member.getId());
        response.setUserId(member.getUser().getId());
        response.setUserEmail(member.getUser().getEmail());
        response.setUserFirstName(member.getUser().getFirstName());
        response.setUserLastName(member.getUser().getLastName());
        response.setUserPhone(member.getUser().getPhone());
        response.setRole(member.getRole());
        response.setIsActive(member.getIsActive());
        response.setTitle(member.getTitle());
        response.setSpecialty(member.getSpecialty());
        response.setJoinedAt(member.getJoinedAt());
        response.setLeftAt(member.getLeftAt());
        response.setAddedByEmail(member.getAddedByUser() != null ? member.getAddedByUser().getEmail() : null);
        response.setCaseCount(caseAssignmentRepository.countActiveByMemberId(member.getId(), LocalDateTime.now()));
        return response;
    }

    private CaseAssignmentResponse toAssignmentResponse(CaseAssignment assignment) {
        CaseAssignmentResponse response = new CaseAssignmentResponse();
        response.setId(assignment.getId());
        response.setCaseId(assignment.getCaseEntity().getId());
        response.setCaseTitle(assignment.getCaseEntity().getTitle());
        response.setCaseReference(assignment.getCaseEntity().getReference());
        response.setMemberId(assignment.getMember().getId());
        response.setMemberName(assignment.getMember().getUser().getName());
        response.setMemberEmail(assignment.getMember().getUser().getEmail());
        response.setAssignmentRole(assignment.getAssignmentRole());
        response.setCanRead(assignment.getCanRead());
        response.setCanWrite(assignment.getCanWrite());
        response.setCanUpload(assignment.getCanUpload());
        response.setCanDelete(assignment.getCanDelete());
        response.setCanManagePermissions(assignment.getCanManagePermissions());
        response.setIsActive(assignment.getIsActive());
        response.setAssignedAt(assignment.getAssignedAt());
        response.setExpiresAt(assignment.getExpiresAt());
        response.setRevokedAt(assignment.getRevokedAt());
        response.setAssignedByEmail(assignment.getAssignedByUser() != null ? assignment.getAssignedByUser().getEmail() : null);
        response.setNotes(assignment.getNotes());
        return response;
    }
}
