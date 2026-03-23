package com.gedavocat.repository;

import com.gedavocat.model.ClientArchiveToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientArchiveTokenRepository extends JpaRepository<ClientArchiveToken, String> {

    Optional<ClientArchiveToken> findByToken(String token);
}
