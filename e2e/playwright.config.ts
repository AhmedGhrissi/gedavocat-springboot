import { defineConfig, devices } from '@playwright/test';

/**
 * Configuration Playwright E2E Tests - GedAvocat
 * 
 * Tests d'isolation multi-tenant (VULN-01)
 * 
 * Documentation: https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: './tests',
  
  /* Timeout par test (30s) */
  timeout: 30 * 1000,
  
  /* Timeout global pour toute la suite */
  globalTimeout: 60 * 60 * 1000,
  
  /* Délai entre tentatives */
  expect: {
    timeout: 5000
  },
  
  /* Fail après le premier échec pour accélérer le feedback */
  fullyParallel: false,
  
  /* Ne pas rejouer les tests échoués en CI */
  forbidOnly: !!process.env.CI,
  
  /* Retry 2 fois en cas d'échec en CI, 0 en local */
  retries: process.env.CI ? 2 : 0,
  
  /* Nombre de workers (parallel) */
  workers: process.env.CI ? 1 : undefined,
  
  /* Reporter : HTML + ligne de commande */
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list']
  ],
  
  /* Configuration globale pour tous les tests */
  use: {
    /* URL de base de l'application */
    baseURL: process.env.BASE_URL || 'http://localhost:8092',
    
    /* Capturer traces uniquement en cas d'échec */
    trace: 'on-first-retry',
    
    /* Screenshots */
    screenshot: 'only-on-failure',
    
    /* Vidéos */
    video: 'retain-on-failure',
    
    /* Locale française */
    locale: 'fr-FR',
    
    /* Timezone Paris */
    timezoneId: 'Europe/Paris',
  },

  /* Configuration des projets (browsers) */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },

    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },

    /* Tests mobiles */
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 12'] },
    },
  ],

  /* Serveur de développement local (optionnel) */
  // Temporairement désactivé - à démarrer manuellement avec: mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
  /* webServer: process.env.CI ? undefined : {
    command: 'cd .. && mvn spring-boot:run -Dspring-boot.run.profiles=test',
    url: 'http://localhost:8080/actuator/health',
    timeout: 120 * 1000,
    reuseExistingServer: !process.env.CI,
  }, */
});
