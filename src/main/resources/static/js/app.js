/**
 * GED AVOCAT - Application JavaScript
 * Interactions UI modernes et animations
 */

(function() {
  'use strict';

  // ==================== UTILITY FUNCTIONS ====================
  const GEDAvocat = {
    
    // Initialize all components
    init() {
      this.initSidebar();
      this.initDropdowns();
      this.initModals();
      this.initTooltips();
      this.initAnimations();
      this.initSearchBar();
      this.initNotifications();
    },

    // ==================== SIDEBAR ====================
    initSidebar() {
      const sidebarToggle = document.querySelector('[data-sidebar-toggle]');
      const sidebar = document.querySelector('.sidebar');
      const mainContent = document.querySelector('.main-content');

      if (sidebarToggle && sidebar) {
        sidebarToggle.addEventListener('click', () => {
          sidebar.classList.toggle('open');
          mainContent?.classList.toggle('sidebar-open');
        });
      }

      // Active nav item highlight
      const currentPath = window.location.pathname;
      const navItems = document.querySelectorAll('.nav-item');
      
      navItems.forEach(item => {
        const href = item.getAttribute('href');
        if (currentPath.includes(href)) {
          item.classList.add('active');
        }
      });
    },

    // ==================== DROPDOWNS ====================
    initDropdowns() {
      const dropdowns = document.querySelectorAll('.dropdown');
      
      dropdowns.forEach(dropdown => {
        const trigger = dropdown.querySelector('[data-dropdown-trigger]');
        
        if (trigger) {
          trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            dropdown.classList.toggle('open');
          });
        }
      });

      // Close dropdowns when clicking outside
      document.addEventListener('click', () => {
        dropdowns.forEach(dropdown => dropdown.classList.remove('open'));
      });
    },

    // ==================== MODALS ====================
    initModals() {
      // Open modal
      document.querySelectorAll('[data-modal-open]').forEach(trigger => {
        trigger.addEventListener('click', (e) => {
          e.preventDefault();
          const modalId = trigger.getAttribute('data-modal-open');
          const modal = document.getElementById(modalId);
          if (modal) {
            modal.classList.add('active');
            document.body.style.overflow = 'hidden';
          }
        });
      });

      // Close modal
      document.querySelectorAll('[data-modal-close]').forEach(closeBtn => {
        closeBtn.addEventListener('click', () => {
          const modal = closeBtn.closest('.modal-overlay');
          if (modal) {
            modal.classList.remove('active');
            document.body.style.overflow = '';
          }
        });
      });

      // Close modal on overlay click
      document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', (e) => {
          if (e.target === overlay) {
            overlay.classList.remove('active');
            document.body.style.overflow = '';
          }
        });
      });

      // Close modal on ESC key
      document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
          document.querySelectorAll('.modal-overlay.active').forEach(modal => {
            modal.classList.remove('active');
            document.body.style.overflow = '';
          });
        }
      });
    },

    // ==================== TOOLTIPS ====================
    initTooltips() {
      const tooltipElements = document.querySelectorAll('[data-tooltip]');
      
      tooltipElements.forEach(element => {
        const tooltipText = element.getAttribute('data-tooltip');
        const tooltip = document.createElement('span');
        tooltip.className = 'tooltip-text';
        tooltip.textContent = tooltipText;
        element.appendChild(tooltip);
        element.classList.add('tooltip');
      });
    },

    // ==================== ANIMATIONS ====================
    initAnimations() {
      // Intersection Observer for scroll animations
      const observerOptions = {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px'
      };

      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            entry.target.classList.add('animate-fade-in');
          }
        });
      }, observerOptions);

      // Observe elements with animation classes
      document.querySelectorAll('.card, .stat-card, .table-modern').forEach(el => {
        observer.observe(el);
      });

      // Smooth scroll
      document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function(e) {
          const href = this.getAttribute('href');
          if (!href || href === '#') return;
          e.preventDefault();
          const target = document.querySelector(href);
          if (target) {
            target.scrollIntoView({ behavior: 'smooth' });
          }
        });
      });
    },

    // ==================== SEARCH BAR ====================
    initSearchBar() {
      const searchInputs = document.querySelectorAll('.search-input');
      
      searchInputs.forEach(input => {
        input.addEventListener('input', (e) => {
          const value = e.target.value.toLowerCase();
          const searchResults = document.querySelector('[data-search-results]');
          
          if (searchResults) {
            const items = searchResults.querySelectorAll('[data-search-item]');
            items.forEach(item => {
              const text = item.textContent.toLowerCase();
              item.style.display = text.includes(value) ? '' : 'none';
            });
          }
        });
      });
    },

    // ==================== NOTIFICATIONS ====================
    initNotifications() {
      // Auto-hide alerts after 5 seconds
      document.querySelectorAll('.alert').forEach(alert => {
        setTimeout(() => {
          alert.style.opacity = '0';
          setTimeout(() => alert.remove(), 300);
        }, 5000);
      });

      // Close button for alerts
      document.querySelectorAll('.alert [data-dismiss]').forEach(btn => {
        btn.addEventListener('click', () => {
          const alert = btn.closest('.alert');
          alert.style.opacity = '0';
          setTimeout(() => alert.remove(), 300);
        });
      });
    },

    // ==================== HELPER FUNCTIONS ====================
    
    // Show toast notification
    showToast(message, type = 'info', duration = 3000) {
      const toast = document.createElement('div');
      toast.className = `alert alert-${type}`;
      toast.style.cssText = 'position: fixed; top: 20px; right: 20px; z-index: 9999; min-width: 300px;';
      toast.innerHTML = `
        <div class="alert-icon">
          <i class="fas fa-${this.getIconForType(type)}"></i>
        </div>
        <div class="alert-content">
          <div class="alert-message">${message}</div>
        </div>
      `;
      
      document.body.appendChild(toast);
      
      setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
      }, duration);
    },

    getIconForType(type) {
      const icons = {
        success: 'check-circle',
        warning: 'exclamation-triangle',
        danger: 'times-circle',
        info: 'info-circle'
      };
      return icons[type] || 'info-circle';
    },

    // Confirm dialog
    confirm(message, callback) {
      const confirmed = window.confirm(message);
      if (confirmed && callback) {
        callback();
      }
    },

    // Loading state
    showLoading(element) {
      const spinner = document.createElement('span');
      spinner.className = 'spinner spinner-sm';
      spinner.setAttribute('data-loading-spinner', '');
      element.disabled = true;
      element.appendChild(spinner);
    },

    hideLoading(element) {
      const spinner = element.querySelector('[data-loading-spinner]');
      if (spinner) {
        spinner.remove();
        element.disabled = false;
      }
    },

    // Form validation helper
    validateForm(formElement) {
      const inputs = formElement.querySelectorAll('input[required], textarea[required], select[required]');
      let isValid = true;

      inputs.forEach(input => {
        if (!input.value.trim()) {
          input.classList.add('is-invalid');
          isValid = false;
        } else {
          input.classList.remove('is-invalid');
        }
      });

      return isValid;
    },

    // Format date
    formatDate(dateString) {
      const date = new Date(dateString);
      return new Intl.DateTimeFormat('fr-FR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      }).format(date);
    },

    // Format currency
    formatCurrency(amount) {
      return new Intl.NumberFormat('fr-FR', {
        style: 'currency',
        currency: 'EUR'
      }).format(amount);
    }
  };

  // ==================== CHARTS ====================
  const ChartManager = {
    
    // Initialize dashboard chart
    initActivityChart() {
      const canvas = document.getElementById('activityChart');
      if (!canvas) return;

      const ctx = canvas.getContext('2d');
      
      // Sample data - replace with actual data from backend
      const data = {
        labels: ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'],
        datasets: [
          {
            label: 'Nouveaux dossiers',
            data: [12, 19, 8, 15, 22, 13, 8],
            borderColor: '#1e3a5f',
            backgroundColor: 'rgba(30, 58, 95, 0.1)',
            tension: 0.4
          },
          {
            label: 'Documents ajoutés',
            data: [25, 32, 28, 35, 40, 30, 22],
            borderColor: '#d4af37',
            backgroundColor: 'rgba(212, 175, 55, 0.1)',
            tension: 0.4
          }
        ]
      };

      // Create gradient
      const gradient = ctx.createLinearGradient(0, 0, 0, 400);
      gradient.addColorStop(0, 'rgba(30, 58, 95, 0.2)');
      gradient.addColorStop(1, 'rgba(30, 58, 95, 0)');

      data.datasets[0].backgroundColor = gradient;

      // Chart configuration
      if (typeof Chart !== 'undefined') {
        new Chart(ctx, {
          type: 'line',
          data: data,
          options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
              legend: {
                display: true,
                position: 'bottom'
              }
            },
            scales: {
              y: {
                beginAtZero: true,
                grid: {
                  color: 'rgba(0, 0, 0, 0.05)'
                }
              },
              x: {
                grid: {
                  display: false
                }
              }
            }
          }
        });
      }
    }
  };

  // ==================== INITIALIZE ON DOM READY ====================
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      GEDAvocat.init();
      ChartManager.initActivityChart();
    });
  } else {
    GEDAvocat.init();
    ChartManager.initActivityChart();
  }

  // Expose to global scope
  window.GEDAvocat = GEDAvocat;
  window.ChartManager = ChartManager;

})();
