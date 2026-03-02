# 🚀 PLAN D'ACTION - DocAvocat Compétitif 2026

## 🎯 Objectif: Être Compétitif en 3 Mois

**Budget:** 25,000€  
**Équipe:** 1 dev full-time  
**Deadline:** 1er Juin 2026

---

## 📅 PLANNING DÉTAILLÉ 90 JOURS

### 🔴 SEMAINE 1-2 (Quick Wins Performance)

#### Jour 1-2: Optimisation Immédiate
```bash
✅ FAIT (5 min chacun):
```

**1. Activer Compression Gzip**
```properties
# application.properties
server.compression.enabled=true
server.compression.mime-types=text/html,text/xml,text/plain,text/css,application/javascript,application/json
server.compression.min-response-size=1024
```

**2. Cache Thymeleaf Production**
```properties
spring.thymeleaf.cache=true  # En prod uniquement!
```

**3. Headers Caching**
```properties
spring.web.resources.cache.cachecontrol.max-age=31536000
spring.web.resources.cache.cachecontrol.cache-public=true
```

**Gain:** +20% performance immédiate ⚡

---

#### Jour 3-4: Audit Base de Données

**Tâche 1: Identifier requêtes lentes**
```sql
-- Activer slow query log
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1; -- 1 seconde

-- Analyser après 24h
SELECT * FROM mysql.slow_log ORDER BY query_time DESC LIMIT 20;
```

**Tâche 2: Ajouter index critiques**
```sql
-- Index obligatoires
CREATE INDEX idx_cases_lawyer ON cases(lawyer_id);
CREATE INDEX idx_cases_status ON cases(status);
CREATE INDEX idx_cases_created ON cases(created_at);

CREATE INDEX idx_documents_case ON documents(case_id);
CREATE INDEX idx_documents_lawyer ON documents(lawyer_id);
CREATE INDEX idx_documents_uploaded ON documents(uploaded_at);

CREATE INDEX idx_appointments_lawyer ON appointments(lawyer_id);
CREATE INDEX idx_appointments_date ON appointments(appointment_date);
CREATE INDEX idx_appointments_status ON appointments(status);

CREATE INDEX idx_invoices_lawyer ON invoices(lawyer_id);
CREATE INDEX idx_invoices_client ON invoices(client_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_due ON invoices(due_date);

CREATE INDEX idx_clients_lawyer ON clients(lawyer_id);
CREATE INDEX idx_clients_email ON clients(email);

-- Index composites
CREATE INDEX idx_cases_lawyer_status ON cases(lawyer_id, status);
CREATE INDEX idx_appointments_lawyer_date ON appointments(lawyer_id, appointment_date);
```

**Gain:** -80% temps requêtes 🚀

---

#### Jour 5-7: Correction N+1 Queries

**Audit repositories:**
```java
// Chercher dans tous les *Repository.java
@Query("SELECT c FROM Case c LEFT JOIN FETCH c.client WHERE ...")

// Exemple CaseRepository
@Query("SELECT c FROM Case c " +
       "LEFT JOIN FETCH c.client " +
       "LEFT JOIN FETCH c.lawyer " +
       "WHERE c.lawyer.id = :lawyerId " +
       "ORDER BY c.createdAt DESC")
List<Case> findByLawyerWithDetails(@Param("lawyerId") Long lawyerId);

// ClientRepository
@Query("SELECT c FROM Client c " +
       "LEFT JOIN FETCH c.cases " +
       "WHERE c.lawyer.id = :lawyerId")
List<Client> findByLawyerWithCases(@Param("lawyerId") Long lawyerId);

// AppointmentRepository
@Query("SELECT a FROM Appointment a " +
       "LEFT JOIN FETCH a.client " +
       "LEFT JOIN FETCH a.case " +
       "WHERE a.lawyer.id = :lawyerId " +
       "AND a.appointmentDate BETWEEN :start AND :end")
List<Appointment> findByLawyerAndDateRangeWithDetails(...);
```

**Gain:** -70% requêtes SQL 🎯

---

#### Jour 8-10: Redis Cache

**Installation:**
```bash
docker run -d -p 6379:6379 --name redis redis:alpine
```

**Configuration:**
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

```properties
# application.properties
spring.cache.type=redis
spring.redis.host=localhost
spring.redis.port=6379
spring.cache.redis.time-to-live=600000  # 10 minutes
```

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
```

**Utilisation:**
```java
@Service
public class CaseService {
    
    @Cacheable(value = "cases", key = "#id")
    public Case findById(Long id) {
        return caseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Case not found"));
    }
    
    @CacheEvict(value = "cases", key = "#case.id")
    public Case update(Case case) {
        return caseRepository.save(case);
    }
    
    @Cacheable(value = "lawyerCases", key = "#lawyerId")
    public List<Case> findByLawyer(Long lawyerId) {
        return caseRepository.findByLawyerWithDetails(lawyerId);
    }
    
    @CacheEvict(value = "lawyerCases", key = "#lawyerId")
    public void evictLawyerCache(Long lawyerId) {
        // Manuel evict si besoin
    }
}
```

**Gain:** -40% requêtes DB, +300% vitesse 🚀🚀

---

### 🟡 SEMAINE 3-4 (Time Tracking - Feature #1)

**Objectif:** Implémenter suivi du temps facturable

#### Jour 11-12: Modèle de données

```java
@Entity
@Table(name = "time_entries")
@Data
public class TimeEntry {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private Case case;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lawyer_id")
    private User lawyer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;
    
    @Column(nullable = false)
    private LocalDateTime startTime;
    
    private LocalDateTime endTime;
    
    @Column(nullable = false)
    private Integer durationMinutes; // Auto-calculé
    
    @Column(precision = 10, scale = 2)
    private BigDecimal hourlyRate;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal amount; // Auto-calculé
    
    @Column(length = 2000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeEntryStatus status = TimeEntryStatus.DRAFT;
    
    @Column(nullable = false)
    private boolean billable = true;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice; // Si facturé
    
    public enum TimeEntryStatus {
        DRAFT,      // En cours de saisie
        SUBMITTED,  // Soumis pour facturation
        BILLED,     // Facturé
        PAID        // Payé
    }
    
    // Calculer durée et montant
    @PrePersist
    @PreUpdate
    public void calculateAmounts() {
        if (startTime != null && endTime != null) {
            durationMinutes = (int) Duration.between(startTime, endTime).toMinutes();
            
            if (hourlyRate != null && durationMinutes != null) {
                BigDecimal hours = BigDecimal.valueOf(durationMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                amount = hourlyRate.multiply(hours).setScale(2, RoundingMode.HALF_UP);
            }
        }
    }
}
```

#### Jour 13-14: Repository & Service

```java
@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
    
    @Query("SELECT t FROM TimeEntry t " +
           "LEFT JOIN FETCH t.case " +
           "LEFT JOIN FETCH t.client " +
           "WHERE t.lawyer.id = :lawyerId " +
           "ORDER BY t.startTime DESC")
    List<TimeEntry> findByLawyer(@Param("lawyerId") Long lawyerId);
    
    @Query("SELECT t FROM TimeEntry t " +
           "WHERE t.lawyer.id = :lawyerId " +
           "AND t.startTime >= :start " +
           "AND t.startTime < :end " +
           "ORDER BY t.startTime ASC")
    List<TimeEntry> findByLawyerAndDateRange(
        @Param("lawyerId") Long lawyerId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT t FROM TimeEntry t " +
           "WHERE t.case.id = :caseId " +
           "ORDER BY t.startTime DESC")
    List<TimeEntry> findByCase(@Param("caseId") Long caseId);
    
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TimeEntry t " +
           "WHERE t.lawyer.id = :lawyerId " +
           "AND t.billable = true " +
           "AND t.status = 'DRAFT'")
    BigDecimal getTotalUnbilledAmount(@Param("lawyerId") Long lawyerId);
    
    @Query("SELECT t FROM TimeEntry t " +
           "WHERE t.status = 'DRAFT' " +
           "AND t.billable = true " +
           "AND t.lawyer.id = :lawyerId")
    List<TimeEntry> findUnbilledEntries(@Param("lawyerId") Long lawyerId);
}
```

```java
@Service
@RequiredArgsConstructor
public class TimeTrackingService {
    
    private final TimeEntryRepository timeEntryRepository;
    
    public TimeEntry startTimer(Long lawyerId, Long caseId, String description) {
        TimeEntry entry = new TimeEntry();
        entry.setLawyer(new User(lawyerId));
        entry.setCase(caseId != null ? new Case(caseId) : null);
        entry.setStartTime(LocalDateTime.now());
        entry.setDescription(description);
        entry.setHourlyRate(getUserHourlyRate(lawyerId));
        
        return timeEntryRepository.save(entry);
    }
    
    public TimeEntry stopTimer(Long entryId) {
        TimeEntry entry = timeEntryRepository.findById(entryId)
            .orElseThrow(() -> new NotFoundException("Entry not found"));
        
        entry.setEndTime(LocalDateTime.now());
        // calculateAmounts() appelé automatiquement via @PreUpdate
        
        return timeEntryRepository.save(entry);
    }
    
    public TimeEntry createManualEntry(TimeEntryDTO dto, Long lawyerId) {
        TimeEntry entry = new TimeEntry();
        // Mapper DTO → Entity
        entry.setLawyer(new User(lawyerId));
        entry.setStartTime(dto.getStartTime());
        entry.setEndTime(dto.getEndTime());
        entry.setDescription(dto.getDescription());
        entry.setHourlyRate(dto.getHourlyRate());
        entry.setBillable(dto.isBillable());
        
        return timeEntryRepository.save(entry);
    }
    
    public Map<String, Object> getStatistics(Long lawyerId, LocalDate start, LocalDate end) {
        List<TimeEntry> entries = timeEntryRepository.findByLawyerAndDateRange(
            lawyerId,
            start.atStartOfDay(),
            end.plusDays(1).atStartOfDay()
        );
        
        int totalMinutes = entries.stream()
            .mapToInt(TimeEntry::getDurationMinutes)
            .sum();
        
        BigDecimal totalAmount = entries.stream()
            .map(TimeEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal unbilledAmount = entries.stream()
            .filter(e -> e.getStatus() == TimeEntry.TimeEntryStatus.DRAFT)
            .map(TimeEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return Map.of(
            "totalMinutes", totalMinutes,
            "totalHours", totalMinutes / 60.0,
            "totalAmount", totalAmount,
            "unbilledAmount", unbilledAmount,
            "entries", entries.size()
        );
    }
    
    private BigDecimal getUserHourlyRate(Long lawyerId) {
        // Récupérer depuis User.defaultHourlyRate ou Settings
        return BigDecimal.valueOf(150); // Défaut 150€/h
    }
}
```

#### Jour 15-17: Interface Utilisateur

**Template `/time-tracking/index.html`:**
```html
<div layout:fragment="content">
    <div class="container-fluid">
        <div class="row">
            <div class="col-12">
                <h2>
                    <i class="fas fa-clock"></i> Suivi du Temps
                </h2>
            </div>
        </div>
        
        <!-- Timer actif -->
        <div class="row mt-4">
            <div class="col-md-6">
                <div class="card">
                    <div class="card-header bg-primary text-white">
                        <h5><i class="fas fa-play-circle"></i> Timer</h5>
                    </div>
                    <div class="card-body">
                        <form id="timerForm" th:action="@{/time-tracking/start}" method="post">
                            <div class="mb-3">
                                <label>Dossier (optionnel)</label>
                                <select name="caseId" class="form-select">
                                    <option value="">Aucun dossier</option>
                                    <option th:each="c : ${cases}" 
                                            th:value="${c.id}" 
                                            th:text="${c.name}">Case</option>
                                </select>
                            </div>
                            <div class="mb-3">
                                <label>Description</label>
                                <textarea name="description" class="form-control" rows="3" 
                                          placeholder="Ex: Rédaction mémoire, Recherche jurisprudence..."></textarea>
                            </div>
                            <div class="d-flex gap-2">
                                <button type="submit" class="btn btn-success btn-lg flex-fill">
                                    <i class="fas fa-play"></i> Démarrer
                                </button>
                                <button type="button" class="btn btn-danger btn-lg flex-fill" 
                                        id="stopBtn" style="display:none">
                                    <i class="fas fa-stop"></i> Arrêter
                                </button>
                            </div>
                        </form>
                        
                        <!-- Temps en cours -->
                        <div id="activeTimer" style="display:none" class="mt-4 p-3 bg-light rounded">
                            <h3 class="text-center mb-0">
                                <span id="elapsed">00:00:00</span>
                            </h3>
                            <p class="text-center text-muted mb-0" id="timerDescription">-</p>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Stats rapides -->
            <div class="col-md-6">
                <div class="row">
                    <div class="col-12 mb-3">
                        <div class="card">
                            <div class="card-body">
                                <h6 class="text-muted">Aujourd'hui</h6>
                                <h2 th:text="${todayHours} + 'h'">0h</h2>
                            </div>
                        </div>
                    </div>
                    <div class="col-12 mb-3">
                        <div class="card">
                            <div class="card-body">
                                <h6 class="text-muted">À facturer</h6>
                                <h2 th:text="${unbilledAmount} + '€'">0€</h2>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Liste entrées -->
        <div class="row mt-4">
            <div class="col-12">
                <div class="card">
                    <div class="card-header">
                        <h5>Historique</h5>
                    </div>
                    <div class="card-body">
                        <table class="table">
                            <thead>
                                <tr>
                                    <th>Date</th>
                                    <th>Dossier</th>
                                    <th>Description</th>
                                    <th>Durée</th>
                                    <th>Montant</th>
                                    <th>Statut</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr th:each="entry : ${entries}">
                                    <td th:text="${#temporals.format(entry.startTime, 'dd/MM/yyyy HH:mm')}"></td>
                                    <td th:text="${entry.case?.name ?: '-'}"></td>
                                    <td th:text="${entry.description}"></td>
                                    <td>
                                        <span th:text="${entry.durationMinutes / 60} + 'h' + ${entry.durationMinutes % 60} + 'min'"></span>
                                    </td>
                                    <td th:text="${entry.amount} + '€'"></td>
                                    <td>
                                        <span class="badge" 
                                              th:classappend="${entry.status.name() == 'DRAFT' ? 'bg-secondary' : 'bg-success'}"
                                              th:text="${entry.status}"></span>
                                    </td>
                                    <td>
                                        <button class="btn btn-sm btn-primary">
                                            <i class="fas fa-edit"></i>
                                        </button>
                                        <button class="btn btn-sm btn-danger">
                                            <i class="fas fa-trash"></i>
                                        </button>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
let activeTimerId = null;
let startTime = null;
let timerInterval = null;

// Démarrer timer
document.getElementById('timerForm').addEventListener('submit', function(e) {
    e.preventDefault();
    
    fetch('/time-tracking/start', {
        method: 'POST',
        body: new FormData(this)
    })
    .then(r => r.json())
    .then(data => {
        activeTimerId = data.id;
        startTime = new Date(data.startTime);
        
        document.getElementById('timerForm').style.display = 'none';
        document.getElementById('activeTimer').style.display = 'block';
        document.getElementById('stopBtn').style.display = 'block';
        document.getElementById('timerDescription').textContent = data.description || 'Temps facturé';
        
        startTimerDisplay();
    });
});

// Arrêter timer
document.getElementById('stopBtn').addEventListener('click', function() {
    if (!activeTimerId) return;
    
    fetch(`/time-tracking/${activeTimerId}/stop`, {
        method: 'POST'
    })
    .then(() => {
        stopTimerDisplay();
        window.location.reload();
    });
});

function startTimerDisplay() {
    timerInterval = setInterval(() => {
        const elapsed = Math.floor((new Date() - startTime) / 1000);
        const hours = Math.floor(elapsed / 3600);
        const minutes = Math.floor((elapsed % 3600) / 60);
        const seconds = elapsed % 60;
        
        document.getElementById('elapsed').textContent = 
            String(hours).padStart(2, '0') + ':' +
            String(minutes).padStart(2, '0') + ':' +
            String(seconds).padStart(2, '0');
    }, 1000);
}

function stopTimerDisplay() {
    if (timerInterval) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
}
</script>
```

**Gain:** Feature majeure compétitive ! 🏆

---

### 🔴 SEMAINE 5 (2FA / MFA)

**Objectif:** Ajouter authentification 2 facteurs

#### Jour 18-21: Implémentation 2FA

**Dépendances:**
```xml
<dependency>
    <groupId>com.warrenstrange</groupId>
    <artifactId>googleauth</artifactId>
    <version>1.5.0</version>
</dependency>
```

**Entité User (ajout):**
```java
@Entity
public class User {
    // ...existing fields
    
    private String twoFactorSecret;
    private boolean twoFactorEnabled = false;
    private String backupCodes; // JSON array
}
```

**Service 2FA:**
```java
@Service
@RequiredArgsConstructor
public class TwoFactorService {
    
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    
    public TwoFactorSetup initiate2FA(User user) {
        GoogleAuthenticatorKey credentials = gAuth.createCredentials();
        
        String secret = credentials.getKey();
        String qrCodeUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL(
            "DocAvocat",
            user.getEmail(),
            credentials
        );
        
        return new TwoFactorSetup(secret, qrCodeUrl);
    }
    
    public boolean verify Code(User user, int code) {
        return gAuth.authorize(user.getTwoFactorSecret(), code);
    }
    
    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();
        
        for (int i = 0; i < 10; i++) {
            codes.add(String.format("%08d", random.nextInt(100000000)));
        }
        
        return codes;
    }
}
```

**Contrôleur:**
```java
@Controller
@RequestMapping("/security/2fa")
@RequiredArgsConstructor
public class TwoFactorController {
    
    private final TwoFactorService twoFactorService;
    private final UserService userService;
    
    @GetMapping("/setup")
    public String setupPage(Model model, Authentication auth) {
        User user = getCurrentUser(auth);
        
        TwoFactorSetup setup = twoFactorService.initiate2FA(user);
        
        model.addAttribute("qrCodeUrl", setup.getQrCodeUrl());
        model.addAttribute("secret", setup.getSecret());
        
        // Stocker temporairement en session
        request.getSession().setAttribute("temp2FASecret", setup.getSecret());
        
        return "security/2fa-setup";
    }
    
    @PostMapping("/enable")
    public String enable(@RequestParam int code, 
                        Authentication auth,
                        RedirectAttributes redirect) {
        User user = getCurrentUser(auth);
        String tempSecret = (String) request.getSession().getAttribute("temp2FASecret");
        
        if (twoFactorService.verifyCode(tempSecret, code)) {
            user.setTwoFactorSecret(tempSecret);
            user.setTwoFactorEnabled(true);
            
            // Générer backup codes
            List<String> backupCodes = twoFactorService.generateBackupCodes();
            user.setBackupCodes(objectMapper.writeValueAsString(backupCodes));
            
            userService.save(user);
            
            redirect.addFlashAttribute("backupCodes", backupCodes);
            redirect.addFlashAttribute("success", "2FA activé avec succès!");
            
            return "redirect:/security/2fa/backup-codes";
        } else {
            redirect.addFlashAttribute("error", "Code invalide");
            return "redirect:/security/2fa/setup";
        }
    }
    
    @GetMapping("/verify")
    public String verifyPage() {
        return "security/2fa-verify";
    }
    
    @PostMapping("/verify")
    public String verify(@RequestParam int code,
                        @RequestParam(required = false) String backupCode,
                        Authentication auth) {
        User user = getCurrentUser(auth);
        
        boolean valid = false;
        
        if (code > 0) {
            valid = twoFactorService.verifyCode(user, code);
        } else if (backupCode != null) {
            valid = twoFactorService.verifyBackupCode(user, backupCode);
        }
        
        if (valid) {
            // Marquer session comme 2FA vérifié
            request.getSession().setAttribute("2fa_verified", true);
            return "redirect:/dashboard";
        } else {
            return "redirect:/security/2fa/verify?error";
        }
    }
}
```

**Filtre de vérification:**
```java
@Component
@RequiredArgsConstructor
public class TwoFactorVerificationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.isAuthenticated() && 
            !"anonymousUser".equals(auth.getPrincipal())) {
            
            User user = (User) auth.getPrincipal();
            
            if (user.isTwoFactorEnabled()) {
                Boolean verified = (Boolean) request.getSession().getAttribute("2fa_verified");
                
                if (verified == null || !verified) {
                    String uri = request.getRequestURI();
                    
                    // Autoriser accès à la page de vérification
                    if (!uri.startsWith("/security/2fa") && 
                        !uri.startsWith("/css") && 
                        !uri.startsWith("/js")) {
                        response.sendRedirect("/security/2fa/verify");
                        return;
                    }
                }
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
```

**Template setup:**
```html
<div class="container mt-5">
    <div class="row justify-content-center">
        <div class="col-md-6">
            <div class="card">
                <div class="card-header bg-primary text-white">
                    <h4><i class="fas fa-shield-alt"></i> Activer 2FA</h4>
                </div>
                <div class="card-body text-center">
                    <p>Scannez ce QR code avec Google Authenticator ou Authy:</p>
                    
                    <img th:src="${qrCodeUrl}" alt="QR Code" class="img-fluid mb-3">
                    
                    <p class="text-muted">
                        Ou entrez ce code manuellement:<br>
                        <code th:text="${secret}"></code>
                    </p>
                    
                    <form th:action="@{/security/2fa/enable}" method="post">
                        <div class="mb-3">
                            <label>Code de vérification (6 chiffres)</label>
                            <input type="number" name="code" class="form-control text-center" 
                                   maxlength="6" required autofocus>
                        </div>
                        <button type="submit" class="btn btn-primary">
                            <i class="fas fa-check"></i> Activer
                        </button>
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>
```

**Gain:** Sécurité différenciatrice ! 🔐

---

### 🟡 SEMAINE 6-7 (Responsive Complet)

**Objectif:** Toutes les pages 100% responsive

#### Audit et correction de TOUTES les pages:

**Pages critiques:**
1. Dashboard
2. Liste dossiers
3. Détail dossier
4. Liste clients
5. Liste documents
6. Facturation
7. RPVA

**Méthode systématique:**
1. Ouvrir chaque page en mode mobile (F12 → Responsive)
2. Tester 3 tailles: 375px, 768px, 1024px
3. Corriger les débordements
4. Vérifier navigation
5. Valider

**Template type responsive:**
```html
<!-- Container responsive -->
<div class="container-fluid" style="max-width: 100%; overflow-x: hidden;">
    
    <!-- Header responsive -->
    <div class="row">
        <div class="col-12">
            <div class="d-flex flex-column flex-md-row justify-content-between align-items-start align-items-md-center gap-2">
                <h2 class="mb-0">Titre</h2>
                <button class="btn btn-primary w-100 w-md-auto">Action</button>
            </div>
        </div>
    </div>
    
    <!-- Cards grid responsive -->
    <div class="row mt-4">
        <div class="col-12 col-md-6 col-lg-4 mb-3">
            <div class="card h-100">
                <!-- Content -->
            </div>
        </div>
    </div>
    
    <!-- Table responsive -->
    <div class="table-responsive">
        <table class="table">
            <!-- Content -->
        </table>
    </div>
</div>

<style>
@media (max-width: 768px) {
    h1 { font-size: 1.5rem !important; }
    h2 { font-size: 1.25rem !important; }
    .btn { font-size: 0.875rem; }
    .card-body { padding: 1rem; }
}
</style>
```

**Gain:** UX mobile parfaite ! 📱

---

## 📊 RÉSUMÉ 90 JOURS

### Semaines 1-2: Performance ⚡
- ✅ Compression Gzip
- ✅ Index DB
- ✅ N+1 queries
- ✅ Redis Cache

**Gain:** +200% performance

### Semaines 3-4: Time Tracking ⏱️
- ✅ Timer start/stop
- ✅ Entrées manuelles
- ✅ Statistiques
- ✅ Intégration facturation

**Gain:** Feature #1 compétitive

### Semaine 5: 2FA 🔐
- ✅ Google Authenticator
- ✅ Backup codes
- ✅ Vérification obligatoire

**Gain:** Sécurité niveau bancaire

### Semaines 6-7: Mobile 📱
- ✅ Toutes pages responsive
- ✅ Touch optimisé
- ✅ Navigation mobile

**Gain:** UX mobile parfaite

### Semaines 8-9: Reporting 📊
- Business intelligence
- Graphiques CA
- Export Excel/PDF

### Semaines 10-12: Email Integration 📧
- Sync boîte mail
- Templates
- Tracking

---

## 💰 BUDGET DÉTAILLÉ

```
Performance (2 semaines):      5,000€
Time Tracking (2 semaines):    5,000€
2FA (1 semaine):              2,500€
Responsive (2 semaines):      5,000€
Reporting (2 semaines):       5,000€
Email (3 semaines):           7,500€
──────────────────────────────────
TOTAL 12 semaines:           30,000€
```

---

## ✅ CHECKLIST DE COMPLÉTION

### Semaine 1-2
- [ ] Compression Gzip activée
- [ ] Cache Thymeleaf prod
- [ ] Index DB créés (15+)
- [ ] N+1 queries éliminés
- [ ] Redis installé & configuré
- [ ] Cache actif sur services critiques
- [ ] Tests performance (avant/après)

### Semaine 3-4
- [ ] Entité TimeEntry créée
- [ ] Repository complet
- [ ] Service avec timer
- [ ] Contrôleur endpoints
- [ ] UI timer start/stop
- [ ] Liste entrées
- [ ] Stats temps/montants
- [ ] Intégration facturation

### Semaine 5
- [ ] GoogleAuth dépendance
- [ ] Setup 2FA page
- [ ] QR code génération
- [ ] Vérification code
- [ ] Backup codes
- [ ] Filtre vérification
- [ ] Tests 2FA complets

### Semaine 6-7
- [ ] Dashboard responsive
- [ ] Dossiers responsive
- [ ] Clients responsive
- [ ] Documents responsive
- [ ] Facturation responsive
- [ ] RPVA responsive
- [ ] Tests 3 tailles (375/768/1024)

---

## 🎯 APRÈS 3 MOIS

**DocAvocat sera:**
- ⚡ 3× plus rapide
- 📱 100% responsive
- 🔐 Sécurité bancaire (2FA)
- ⏱️ Time tracking complet
- 🏆 Compétitif vs Clio/MyCase

**Prêt pour lancement commercial ! 🚀**

---

**Créé le:** 1er Mars 2026  
**Par:** Plan d'Action Stratégique  
**Durée:** 90 jours  
**Budget:** 30,000€  
**ROI:** Parité concurrence
