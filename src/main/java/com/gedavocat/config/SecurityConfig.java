package com.gedavocat.config;

import com.gedavocat.security.JwtAuthenticationFilter;
import com.gedavocat.security.SubscriptionEnforcementFilter;
import com.gedavocat.security.UserDetailsServiceImpl;
import com.gedavocat.security.AccountLockoutService;
import com.gedavocat.repository.UserRepository;
import com.gedavocat.security.mfa.MultiFactorAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
	private final SubscriptionEnforcementFilter subscriptionFilter;
	private final AccountLockoutService accountLockoutService;
	private final UserRepository userRepository;
	private final MultiFactorAuthenticationService mfaService;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// SEC-CSRF-HARDEN : CSRF désactivé uniquement pour endpoints JWT stateless et webhooks externes
				// Restreint /api/auth à des endpoints spécifiques (pas wildcard) pour éviter les oublis
				.csrf(csrf -> csrf.ignoringRequestMatchers(
						"/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout",
						"/subscription/webhook", "/payment/webhook", "/api/webhooks/**"))
				.authorizeHttpRequests(auth -> auth
						// SEC FIX H-12 : bloquer /h2-console en prod
						.requestMatchers("/h2-console/**").denyAll()
						// SEC FIX M-10 : bloquer /test/** en prod (même si @Profile le fait déjà)
						.requestMatchers("/test/**").denyAll()
						// SEC FIX M-11 : bloquer /api/debug-status
						.requestMatchers("/api/debug-status").denyAll()
						// Actuator — health et prometheus accessibles en interne (Prometheus scrape)
						// Sécurisé côté nginx : /actuator bloqué pour les clients externes (404)
						.requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
						// Pages publiques
						.requestMatchers("/", "/login", "/register", "/maintenance", "/subscription/pricing",
						"/subscription/success", "/subscription/cancel",
						"/api/auth/**", "/css/**", "/js/**", "/images/**", "/img/**", "/favicon.ico", "/favicon.svg",
						"/robots.txt", "/sitemap.xml", "/webjars/**", "/.well-known/**",
							"/forgot-password", "/reset-password", "/verify-email", "/verify-email/resend",
							"/mfa-challenge",
							"/clients/accept-invitation",
							"/collaborators/accept-invitation", "/collaborators/invitation-info",
						"/huissiers/accept-invitation", "/huissiers/invitation-info",
							"/cases/shared", "/cases/shared-expired",
							"/legal/**")
						.permitAll()

						// Pages administrateur
						.requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
						
						// Pages de paiement publiques et webhooks
						.requestMatchers("/payment/pricing", "/payment/webhook", "/payment/success", "/payment/cancel").permitAll()
						.requestMatchers("/payment/**").authenticated()
						.requestMatchers("/api/webhooks/**").permitAll()
						// Allow CLIENTs to GET individual invoices and PDFs (view/download)
						.requestMatchers(HttpMethod.GET, "/invoices/*", "/invoices/*/pdf").hasAnyRole("CLIENT", "LAWYER", "ADMIN", "AVOCAT_ADMIN")
						.requestMatchers("/invoices/my-invoices").hasAnyRole("CLIENT", "LAWYER", "ADMIN", "AVOCAT_ADMIN")
						.requestMatchers("/invoices/**", "/api/invoices/**").hasAnyRole("LAWYER", "ADMIN", "AVOCAT_ADMIN")
						
						// Pages avocat et admin (collaborateurs bloqués pour documents)
						.requestMatchers("/dashboard", "/clients/**", "/cases/**", "/signatures/**",
							"/rpva/**", "/permissions/**", "/api/clients/**", "/api/cases/**")
						.hasAnyRole("LAWYER", "ADMIN", "LAWYER_SECONDARY", "AVOCAT_ADMIN")
						.requestMatchers("/documents/**", "/api/documents/**")
						.hasAnyRole("LAWYER", "ADMIN", "AVOCAT_ADMIN")

						// Gestion du cabinet (avocat principal et avocat admin)
						.requestMatchers("/firm/**").hasAnyRole("LAWYER", "ADMIN", "AVOCAT_ADMIN")

						// Portail huissier
						.requestMatchers("/my-cases-collab/**").hasRole("LAWYER_SECONDARY")
						.requestMatchers("/my-cases-huissier/**").hasRole("HUISSIER")

						// Pages client
.requestMatchers("/my-cases/**", "/my-documents/**", "/my-appointments", "/my-appointments/**", "/my-signatures").hasRole("CLIENT")

					.anyRequest().authenticated())
				// En-têtes de sécurité ANSSI/OWASP/RGPD — niveau bancaire
				// Headers HTTP configurés : X-Frame-Options, Strict-Transport-Security (HSTS),
				// Content-Security-Policy (CSP), X-Content-Type-Options, Referrer-Policy,
				// Permissions-Policy, Cross-Origin-Opener-Policy, Cross-Origin-Resource-Policy
				.headers(h -> {
							// X-Frame-Options SAMEORIGIN (au lieu de DENY) : permet l'affichage
							// dans les portails Zero-Trust (Zscaler, Cisco Umbrella) qui wrappent
							// les pages dans un iframe same-origin. Clickjacking reste bloqué.
							h.frameOptions(f -> f.sameOrigin());
							h.contentTypeOptions(Customizer.withDefaults());
							// HSTS : 1 an, includeSubDomains + preload (niveau bancaire ANSSI/OWASP).
							// Preload permet d'inscrire le domaine dans la liste HSTS des navigateurs,
							// includeSubDomains protège tous les sous-domaines.
							// Valeur min pour preload list : 31536000 (1 an).
							h.httpStrictTransportSecurity(hsts -> hsts
									.includeSubDomains(true)
									.preload(true)
									.maxAgeInSeconds(31536000)); // 1 an (niveau bancaire sécurité/compatibilité)
							h.referrerPolicy(r -> r.policy(
									ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
							// Permissions-Policy déprécié dans Spring Security 6.4+ - fonctionnalité retirée
							// Alternative : configurer via response headers personnalisés si nécessaire

							// Build connect-src and other origin lists once (effectively final) so they can be referenced by nested lambdas
						// CSP header is now set dynamically by CspNonceFilter (per-request nonce).
						// Spring Security's static CSP is disabled to avoid duplicate headers.
						// See CspNonceFilter.java for the full CSP policy.
							// COOP/CORP : SAME_ORIGIN_ALLOW_POPUPS au lieu de SAME_ORIGIN strict.
							// SAME_ORIGIN bloque les proxys corporate qui injectent du JS monitoring/DLP.
							// SAME_ORIGIN_ALLOW_POPUPS maintient l'isolation contre les attaques Spectre
							// tout en permettant les popups légitimes (Stripe checkout, OAuth).
							h.crossOriginOpenerPolicy(coop -> coop.policy(
									org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN_ALLOW_POPUPS));
							// CORP : CROSS_ORIGIN pour permettre aux proxys de charger les ressources.
							// La CSP (default-src 'self') protège déjà contre le chargement de ressources tierces.
							h.crossOriginResourcePolicy(corp -> corp.policy(
									org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.CROSS_ORIGIN));
							})
				// Session avec état pour le formLogin (pas stateless)
				// SEC FIX L-02 : protection contre session fixation
				// SEC FIX L-03 : limite de sessions concurrentes à 1
				.sessionManagement(session -> session
					.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
					.sessionFixation(fix -> fix.newSession())
					.maximumSessions(1)
					.expiredUrl("/login?expired"))
				.authenticationProvider(authenticationProvider())
				// Filtre JWT uniquement pour les API REST
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				// Filtre de vérification d'abonnement (après authentification)
				.addFilterAfter(subscriptionFilter, UsernamePasswordAuthenticationFilter.class)
				.formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login") // Spring Security gère POST
																	// /login
							.usernameParameter("email") // Le champ input s'appelle "email"
							.passwordParameter("password")
							.successHandler((request, response, authentication) -> {
								// SEC FIX L-04 : réinitialiser les tentatives après succès
								accountLockoutService.resetAttempts(authentication.getName());
								// Déterminer les rôles présents de manière sûre
								java.util.Set<String> roles = new java.util.HashSet<>();
								authentication.getAuthorities().forEach(a -> roles.add(a.getAuthority()));
								if (roles.contains("ROLE_ADMIN")) {
									// SEC FIX F-06 : vérifier MFA avant accès admin
									var optAdmin = userRepository.findByEmail(authentication.getName());
									if (optAdmin.isPresent()) {
										var adminUser = optAdmin.get();
										if (mfaService.requiresMFA(adminUser) && adminUser.isMfaEnabled()) {
											var mfaSession = request.getSession(true);
											mfaSession.setAttribute("MFA_PENDING_EMAIL", authentication.getName());
											mfaSession.setAttribute("MFA_TARGET_URL", "/admin");
											org.springframework.security.core.context.SecurityContextHolder.clearContext();
											mfaSession.removeAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
											response.sendRedirect("/mfa-challenge");
											return;
										}
										response.sendRedirect("/admin");
										return;
									} else {
										// SEC FIX N-01 : admin introuvable en DB — invalider la session, bloquer l'accès
										request.getSession().invalidate();
										response.sendError(403);
										return;
									}
								}
								if (roles.contains("ROLE_CLIENT")) {
									response.sendRedirect("/my-cases");
									return;
								}
								if (roles.contains("ROLE_LAWYER_SECONDARY")) {
									response.sendRedirect("/my-cases-collab");
									return;
								}
								if (roles.contains("ROLE_HUISSIER")) {
									response.sendRedirect("/my-cases-huissier");
									return;
								}
								// LAWYER / AVOCAT_ADMIN : vérifier si l'abonnement est actif
								if (roles.contains("ROLE_LAWYER") || roles.contains("ROLE_AVOCAT_ADMIN")) {
									var optUser = userRepository.findByEmail(authentication.getName());
									if (optUser.isPresent() && !optUser.get().hasActiveSubscription()) {
										// Vérifier s'il y a une URL sauvegardée (ex: retour Stripe)
										var savedRequest = new org.springframework.security.web.savedrequest.HttpSessionRequestCache()
											.getRequest(request, response);
										if (savedRequest != null && savedRequest.getRedirectUrl() != null
												&& savedRequest.getRedirectUrl().contains("/subscription/success")) {
											response.sendRedirect(savedRequest.getRedirectUrl());
											return;
										}
										response.sendRedirect("/subscription/pricing");
										return;
									}
								}
								// Par défaut, envoyer vers le dashboard (avocat / autre)
								response.sendRedirect("/dashboard");
							})
						.failureHandler((request, response, exception) -> {
								// SEC FIX L-04 : enregistrer l'échec de tentative
								String email = request.getParameter("email");
								if (email != null) {
									accountLockoutService.recordFailedAttempt(email);
								}
								response.sendRedirect("/login?error=true");
							}).permitAll())
				.logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login")
							.deleteCookies("DOCAVOCAT_SESSION", "JSESSIONID").invalidateHttpSession(true).permitAll());

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(passwordEncoder());
		authProvider.setUserDetailsService(userDetailsService);
		return authProvider;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	/**
	 * SEC-FIREWALL FIX : Configuration HttpFirewall stricté
	 * Suppression des relaxations inutiles (encoded slash/percent/period)
	 * qui pouvaient faciliter des attaques de path traversal.
	 */
	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		StrictHttpFirewall firewall = new StrictHttpFirewall();
		// Tous les encodages restent interdits (défaut strict)
		firewall.setAllowUrlEncodedSlash(false);
		firewall.setAllowUrlEncodedPercent(false);
		firewall.setAllowUrlEncodedPeriod(false);
		firewall.setAllowUrlEncodedDoubleSlash(false);
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
