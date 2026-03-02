package com.gedavocat.security;

import com.gedavocat.model.User;
import com.gedavocat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implémentation de UserDetailsService pour Spring Security
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserRepository userRepository;
    private final AccountLockoutService accountLockoutService;
    
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // SEC FIX L-04 : vérifier le verrouillage de compte avant l'authentification
        if (accountLockoutService.isLocked(email)) {
            long minutes = accountLockoutService.getRemainingLockoutMinutes(email);
            throw new UsernameNotFoundException(
                "Compte temporairement verrouillé. Réessayez dans " + minutes + " minute(s).");
        }
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                    "Identifiants invalides"
                ));
        
        boolean enabled = user.isAccountEnabled() && user.isEmailVerified();

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPassword(),
            enabled,          // account enabled
            true,             // accountNonExpired
            true,             // credentialsNonExpired
            true,             // accountNonLocked
            getAuthorities(user)
        );
    }
    
    /**
     * Convertit le rôle de l'utilisateur en autorités Spring Security
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return authorities;
    }
}
