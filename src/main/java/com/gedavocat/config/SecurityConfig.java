package com.gedavocat.config;

import com.gedavocat.security.JwtAuthenticationFilter;
import com.gedavocat.security.SubscriptionEnforcementFilter;
import com.gedavocat.security.UserDetailsServiceImpl;
import com.gedavocat.security.AccountLockoutService;
import com.gedavocat.repository.UserRepository;
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

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// CSRF désactivé uniquement pour les endpoints API auth (JWT stateless) et webhooks externes
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**", "/subscription/webhook", "/payment/webhook", "/api/webhooks/**"))
				.authorizeHttpRequests(auth -> auth
						// SEC FIX H-12 : bloquer /h2-console en prod
						.requestMatchers("/h2-console/**").denyAll()
						// SEC FIX M-10 : bloquer /test/** en prod (même si @Profile le fait déjà)
						.requestMatchers("/test/**").denyAll()
						// SEC FIX M-11 : bloquer /api/debug-status
						.requestMatchers("/api/debug-status").denyAll()
						// Pages publiques
						.requestMatchers("/", "/login", "/register", "/maintenance", "/subscription/pricing",
						"/api/auth/**", "/css/**", "/js/**", "/images/**", "/img/**", "/favicon.ico", "/favicon.svg",
						"/robots.txt", "/sitemap.xml", "/webjars/**", "/.well-known/**",
							"/forgot-password", "/reset-password", "/verify-email", "/verify-email/resend",
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
						.requestMatchers(HttpMethod.GET, "/invoices/*", "/invoices/*/pdf").hasAnyRole("CLIENT", "LAWYER", "ADMIN")
						.requestMatchers("/invoices/my-invoices").hasAnyRole("CLIENT", "LAWYER", "ADMIN")
						.requestMatchers("/invoices/**", "/api/invoices/**").hasAnyRole("LAWYER", "ADMIN")
						
						// Pages avocat et admin (collaborateurs bloqués pour documents)
						.requestMatchers("/dashboard", "/clients/**", "/cases/**", "/signatures/**",
							"/rpva/**", "/permissions/**", "/api/clients/**", "/api/cases/**")
						.hasAnyRole("LAWYER", "ADMIN", "LAWYER_SECONDARY")
						.requestMatchers("/documents/**", "/api/documents/**")
						.hasAnyRole("LAWYER", "ADMIN")

						// Portail huissier
						.requestMatchers("/my-cases-huissier/**").hasRole("HUISSIER")

						// Pages client
.requestMatchers("/my-cases/**", "/my-documents/**", "/my-appointments", "/my-appointments/**", "/my-signatures").hasAnyRole("CLIENT", "LAWYER", "ADMIN")

					.anyRequest().authenticated())
				// En-têtes de sécurité ANSSI/OWASP/RGPD — niveau bancaire
				.headers(h -> {
							h.frameOptions(f -> f.deny());
							h.contentTypeOptions(Customizer.withDefaults());
							h.httpStrictTransportSecurity(hsts -> hsts
									.includeSubDomains(true)
									.preload(true)
									.maxAgeInSeconds(63072000)); // 2 ans (ANSSI recommandation)
							h.referrerPolicy(r -> r.policy(
									ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
							h.permissionsPolicy(p -> p.policy(
									"camera=(), microphone=(), geolocation=(), payment=(), usb=(), magnetometer=(), gyroscope=(), accelerometer=()"));

							// Build connect-src and other origin lists once (effectively final) so they can be referenced by nested lambdas
						// Always include cdn.jsdelivr.net for Chart.js source maps
						final String connectSrc = "'self' https://api.stripe.com https://api.payplug.com https://cdn.jsdelivr.net";
							// Always allow the CDN for script/style/font resources so external UI libs (FullCalendar, cdnjs, Google Fonts) can load
							final String extraScriptOrigins = " https://cdn.jsdelivr.net";
							final String extraStyleOrigins = " https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com";
							// Allow data: URIs for embedded/base64 fonts (some libs inline fonts as data: URIs)
							final String extraFontOrigins = " data: https://cdnjs.cloudflare.com https://fonts.gstatic.com";

							final String scriptSrc = "'self' 'unsafe-inline' https://js.stripe.com" + extraScriptOrigins;
						// Note: 'unsafe-inline' is kept for script-src because Thymeleaf templates
						// use inline onclick/onsubmit handlers. For full CSP Level 3 compliance,
						// migrate to nonce-based CSP with th:attr in a future sprint.
							final String styleSrc = "'self' 'unsafe-inline'" + extraStyleOrigins;
							final String fontSrc  = "'self'" + extraFontOrigins;

							h.contentSecurityPolicy(csp -> csp.policyDirectives(
									"default-src 'self'; " +
									"script-src " + scriptSrc + "; " +
									"style-src " + styleSrc + "; " +
									"font-src " + fontSrc + "; " +
									// SEC-CSP FIX : restrict img-src to self + data: only (no wildcard https:)
						"img-src 'self' data:; " +
									"connect-src " + connectSrc + "; " +
									"frame-src 'self' https://js.stripe.com https://hooks.stripe.com; " +
									"object-src 'none'; " +
									"base-uri 'self'; " +
									"form-action 'self'; " +
									"frame-ancestors 'none'"));
							h.crossOriginOpenerPolicy(coop -> coop.policy(
									org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN));
							h.crossOriginResourcePolicy(corp -> corp.policy(
									org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN));
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
									response.sendRedirect("/admin");
									return;
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
								// LAWYER : vérifier si l'abonnement est actif
								if (roles.contains("ROLE_LAWYER")) {
									var optUser = userRepository.findByEmail(authentication.getName());
									if (optUser.isPresent() && !optUser.get().hasActiveSubscription()) {
										String plan = optUser.get().getSubscriptionPlan() != null
											? optUser.get().getSubscriptionPlan().name() : "ESSENTIEL";
										response.sendRedirect("/subscription/checkout?plan=" + plan + "&period=monthly");
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
							.deleteCookies("JSESSIONID").invalidateHttpSession(true).permitAll());

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
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