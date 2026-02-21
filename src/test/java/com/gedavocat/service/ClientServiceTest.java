package com.gedavocat.service;

import com.gedavocat.model.Client;
import com.gedavocat.model.User;
import com.gedavocat.repository.AppointmentRepository;
import com.gedavocat.repository.CaseRepository;
import com.gedavocat.repository.ClientRepository;
import com.gedavocat.repository.RpvaCommunicationRepository;
import com.gedavocat.repository.UserRepository;

import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests du service client")
class ClientServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private RpvaCommunicationRepository rpvaCommunicationRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private CaseService caseService;

    @InjectMocks
    private ClientService clientService;

    private User lawyer;
    private Client client;

    @BeforeEach
    void setUp() {
        lawyer = new User();
        lawyer.setId("lawyer-001");
        lawyer.setEmail("jean.dupont@gedavocat.com");
        lawyer.setRole(User.UserRole.LAWYER);
        lawyer.setMaxClients(100);

        client = new Client();
        client.setId("client-001");
        client.setName("Paul Durand");
        client.setEmail("paul.durand@email.com");
        client.setPhone("0612345678");
    }

    @Test
    @DisplayName("✓ Récupérer la liste des clients d'un avocat")
    void getClientsByLawyer() {
        when(clientRepository.findByLawyerId("lawyer-001"))
            .thenReturn(List.of(client));

        List<Client> result = clientService.getClientsByLawyer("lawyer-001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Paul Durand");
    }

    @Test
    @DisplayName("✓ Récupérer un client par ID")
    void getClientById() {
        when(clientRepository.findById("client-001"))
            .thenReturn(Optional.of(client));

        Client result = clientService.getClientById("client-001");

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("paul.durand@email.com");
    }

    @Test
    @DisplayName("✗ Client introuvable lève une exception")
    void getClientByIdThrowsWhenNotFound() {
        when(clientRepository.findById("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.getClientById("inconnu"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("✓ Créer un nouveau client")
    void createClient() {
        when(userRepository.findById("lawyer-001")).thenReturn(Optional.of(lawyer));
        when(clientRepository.save(any(Client.class))).thenReturn(client);

        Client result = clientService.createClient(client, "lawyer-001");

        assertThat(result).isNotNull();
        verify(clientRepository).save(any(Client.class));
    }

    @Test
    @DisplayName("✓ Supprimer un client")
    void deleteClient() {
        when(clientRepository.findById("client-001")).thenReturn(Optional.of(client));
        when(caseRepository.findByClientId("client-001")).thenReturn(Collections.emptyList());
        doNothing().when(appointmentRepository).clearClientByClientId("client-001");
        doNothing().when(clientRepository).delete(client);

        clientService.deleteClient("client-001");

        verify(caseRepository).findByClientId("client-001");
        verify(clientRepository).delete(client);
    }
}
