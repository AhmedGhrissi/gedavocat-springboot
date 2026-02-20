package com.gedavocat.config;

import com.gedavocat.security.JwtAuthenticationFilter;
import com.gedavocat.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final UserDetailsServiceImpl userDetailsService;
	private final JwtAuthenticationFilter jwtAuthFilter;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// CSRF désactivé uniquement pour les endpoints API REST
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/subscription/webhook"))
				.authorizeHttpRequests(auth -> auth
						// Pages publiques
						.requestMatchers("/", "/login", "/register", "/maintenance", "/subscription/pricing",
							"/api/auth/**", "/css/**", "/js/**", "/images/**", "/favicon.ico",
							"/subscription/webhook", "/legal/**",
							"/forgot-password", "/reset-password", "/verify-email", "/verify-email/resend",
							"/clients/accept-invitation",
							"/cases/shared", "/cases/shared-expired")
						.permitAll()

						// Pages administrateur
						.requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
						
						// Pages de paiement et webhooks
						.requestMatchers("/payment/**").permitAll()
						.requestMatchers("/api/webhooks/**").permitAll()
						.requestMatchers("/invoices/**", "/api/invoices/**").hasAnyRole("LAWYER", "ADMIN")
						
						// Pages avocat et admin
						.requestMatchers("/dashboard", "/clients/**", "/cases/**", "/documents/**", "/signatures/**",
								"/rpva/**", "/permissions/**", "/api/clients/**", "/api/cases/**", "/api/documents/**")
						.hasAnyRole("LAWYER", "ADMIN", "LAWYER_SECONDARY")

						// Pages client
						.requestMatchers("/my-cases/**", "/my-documents/**").hasAnyRole("CLIENT", "LAWYER", "ADMIN")

					.anyRequest().authenticated())
				// En-têtes de sécurité ANSSI/OWASP
				.headers(h -> h
						.frameOptions(f -> f.deny())
						.contentTypeOptions(Customizer.withDefaults())
						.httpStrictTransportSecurity(hsts -> hsts
								.includeSubDomains(true)
								.maxAgeInSeconds(31536000))
						.referrerPolicy(r -> r.policy(
								ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
						.permissionsPolicy(p -> p.policy(
								"camera=(), microphone=(), geolocation=(), payment=()")
						))
				// Session avec état pour le formLogin (pas stateless)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.authenticationProvider(authenticationProvider())
				// Filtre JWT uniquement pour les API REST
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login") // Spring Security gère POST
																							// /login
						.usernameParameter("email") // Le champ input s'appelle "email"
						.passwordParameter("password")
						.successHandler((request, response, authentication) -> {
							// Redirection selon le rôle
							String role = authentication.getAuthorities().stream()
									.findFirst()
									.map(a -> a.getAuthority())
									.orElse("");
							
							if (role.equals("ROLE_ADMIN")) {
								response.sendRedirect("/admin");
							} else if (role.equals("ROLE_CLIENT")) {
								response.sendRedirect("/my-cases");
							} else {
								response.sendRedirect("/dashboard");
							}
						})
						.failureUrl("/login?error=true").permitAll())
				.logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout=true")
						.deleteCookies("JSESSIONID").invalidateHttpSession(true).permitAll());

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService);
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	/**
	 * Configuration d'un HttpFirewall plus permissif pour éviter les erreurs
	 * "RequestRejectedException" avec les caractères spéciaux dans les URLs
	 */
	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		StrictHttpFirewall firewall = new StrictHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		firewall.setAllowUrlEncodedPercent(true);
		firewall.setAllowUrlEncodedPeriod(true);
		firewall.setAllowSemicolon(true); // Permet les points-virgules dans les URLs
		firewall.setAllowBackSlash(true);
		firewall.setAllowUrlEncodedDoubleSlash(true);
		return firewall;
	}

	/**
	 * Configuration pour utiliser le firewall personnalisé
	 */
	@Bean
	public WebSecurityCustomizer webSecurityCustomizer() {
		return (web) -> web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
	}
}
