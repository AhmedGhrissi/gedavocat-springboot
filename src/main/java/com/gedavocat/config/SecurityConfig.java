package com.gedavocat.config;

import com.gedavocat.security.JwtAuthenticationFilter;
import com.gedavocat.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
	private final Environment env;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// CSRF désactivé uniquement pour les endpoints API REST et webhooks externes
				.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/subscription/webhook", "/payment/webhook"))
				.authorizeHttpRequests(auth -> auth
						// Pages publiques
						.requestMatchers("/", "/login", "/register", "/maintenance", "/subscription/pricing",
						"/api/auth/**", "/css/**", "/js/**", "/images/**", "/img/**", "/favicon.ico", "/favicon.svg",
						"/robots.txt", "/sitemap.xml",
							"/forgot-password", "/reset-password", "/verify-email", "/verify-email/resend",
							"/clients/accept-invitation",
							"/collaborators/accept-invitation", "/collaborators/invitation-info",
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

						// Pages client
						.requestMatchers("/my-cases/**", "/my-documents/**", "/my-appointments", "/my-signatures").hasAnyRole("CLIENT", "LAWYER", "ADMIN")

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
							boolean isDevOrLocal = env.acceptsProfiles(Profiles.of("dev", "local"));
							final String connectSrc = isDevOrLocal
									? "'self' https://api.stripe.com https://api.payplug.com https://cdn.jsdelivr.net"
									: "'self' https://api.stripe.com https://api.payplug.com";

							// Always allow the CDN for script/style/font resources so external UI libs (FullCalendar, cdnjs, Google Fonts) can load
							final String extraScriptOrigins = " https://cdn.jsdelivr.net";
							final String extraStyleOrigins = " https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com";
							final String extraFontOrigins = " https://cdnjs.cloudflare.com https://fonts.gstatic.com";

							final String scriptSrc = "'self' 'unsafe-inline' https://js.stripe.com" + extraScriptOrigins;
							final String styleSrc = "'self' 'unsafe-inline'" + extraStyleOrigins;
							final String fontSrc  = "'self'" + extraFontOrigins;

							h.contentSecurityPolicy(csp -> csp.policyDirectives(
									"default-src 'self'; " +
									"script-src " + scriptSrc + "; " +
									"style-src " + styleSrc + "; " +
									"font-src " + fontSrc + "; " +
									"img-src 'self' data: https:; " +
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
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.authenticationProvider(authenticationProvider())
				// Filtre JWT uniquement pour les API REST
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login") // Spring Security gère POST
																	// /login
							.usernameParameter("email") // Le champ input s'appelle "email"
							.passwordParameter("password")
							.successHandler((request, response, authentication) -> {
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
								// Par défaut, envoyer vers le dashboard (avocat / autre)
								response.sendRedirect("/dashboard");
							})
						.failureUrl("/login?error=true").permitAll())
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
	 * Configuration d'un HttpFirewall plus permissif pour éviter les erreurs
	 * "RequestRejectedException" avec les caractères spéciaux dans les URLs
	 */
	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		StrictHttpFirewall firewall = new StrictHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		firewall.setAllowUrlEncodedPercent(true);
		firewall.setAllowUrlEncodedPeriod(true);
		// Désactivé : setAllowSemicolon et setAllowBackSlash — risque de path traversal
		// firewall.setAllowSemicolon(true);
		// firewall.setAllowBackSlash(true);
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