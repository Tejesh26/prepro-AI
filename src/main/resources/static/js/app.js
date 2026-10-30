/**
 * =============================================================================
 * PREPRO AI - Client JavaScript Engine (Vanilla JS)
 * Features:
 * - Theme management (Light/Dark mode with localStorage & system preference)
 * - Corner Toast Notification system
 * - Drag-and-Drop file upload integration
 * - Animated AI progress simulation
 * =============================================================================
 */

(function () {
    'use strict';

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Theme Management (Early execution to avoid Flash of Unstyled Content)
    // ──────────────────────────────────────────────────────────────────────────
    const THEME_STORAGE_KEY = 'prepro_theme';

    function getPreferredTheme() {
        const storedTheme = localStorage.getItem(THEME_STORAGE_KEY);
        if (storedTheme) {
            return storedTheme;
        }
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        document.documentElement.setAttribute('data-bs-theme', theme);
        localStorage.setItem(THEME_STORAGE_KEY, theme);

        // Update any toggle button icon in the DOM
        const toggleBtn = document.getElementById('themeToggleBtn');
        if (toggleBtn) {
            const icon = toggleBtn.querySelector('i');
            if (icon) {
                if (theme === 'dark') {
                    icon.className = 'bi bi-sun-fill text-warning';
                    toggleBtn.setAttribute('title', 'Switch to Light Mode');
                } else {
                    icon.className = 'bi bi-moon-stars-fill text-secondary';
                    toggleBtn.setAttribute('title', 'Switch to Dark Mode');
                }
            }
        }

        window.dispatchEvent(new CustomEvent('prepro:theme-change', { detail: { theme } }));
    }

    // Apply immediately
    applyTheme(getPreferredTheme());

    window.toggleTheme = function () {
        const current = document.documentElement.getAttribute('data-theme') || 'light';
        const next = current === 'dark' ? 'light' : 'dark';
        applyTheme(next);
    };

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Toast Notification System
    // ──────────────────────────────────────────────────────────────────────────
    function ensureToastContainer() {
        let container = document.getElementById('appToastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'appToastContainer';
            container.className = 'toast-container-corner';
            document.body.appendChild(container);
        }
        return container;
    }

    window.showToast = function (message, type = 'info', title = null) {
        const container = ensureToastContainer();

        const toast = document.createElement('div');
        toast.className = 'app-toast';

        let iconClass = 'bi-info-circle-fill text-primary';
        let defaultTitle = 'Notification';

        if (type === 'success') {
            iconClass = 'bi-check-circle-fill text-success';
            defaultTitle = 'Success';
        } else if (type === 'error' || type === 'danger') {
            iconClass = 'bi-exclamation-triangle-fill text-danger';
            defaultTitle = 'Error';
        } else if (type === 'warning') {
            iconClass = 'bi-exclamation-circle-fill text-warning';
            defaultTitle = 'Attention';
        }

        const heading = title || defaultTitle;

        toast.innerHTML = `
            <i class="bi ${iconClass} fs-5 mt-1"></i>
            <div class="flex-grow-1">
                <div class="fw-bold small text-heading mb-1">${heading}</div>
                <div class="small text-muted">${message}</div>
            </div>
            <button type="button" class="btn-close btn-close-sm ms-2" aria-label="Close"></button>
        `;

        const closeBtn = toast.querySelector('.btn-close');
        closeBtn.addEventListener('click', () => removeToast(toast));

        container.appendChild(toast);

        // Trigger animation
        requestAnimationFrame(() => {
            toast.classList.add('show');
        });

        // Auto remove after 4.5s
        const timer = setTimeout(() => {
            removeToast(toast);
        }, 4500);

        toast.addEventListener('mouseenter', () => clearTimeout(timer));
    };

    function removeToast(toast) {
        toast.classList.remove('show');
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. AI Generation Loading State Simulation
    // ──────────────────────────────────────────────────────────────────────────
    let loadingInterval = null;

    window.showAILoading = function (customSteps) {
        const overlay = document.getElementById('aiLoadingOverlay');
        if (!overlay) return;

        const defaultSteps = [
            'Reading your syllabus & chapters...',
            'Analyzing cognitive load & difficulty levels...',
            'Allocating 50/30/20 priority time weights...',
            'Inserting 30-min focus blocks & smart rest breaks...',
            'Scheduling your Zoom online lectures...',
            'Polishing your master schedule...'
        ];

        const steps = customSteps || defaultSteps;
        const textEl = document.getElementById('aiLoadingText');
        const barEl = document.getElementById('aiLoadingProgress');

        let stepIndex = 0;
        if (textEl) textEl.innerText = steps[0];
        if (barEl) barEl.style.width = '15%';

        overlay.classList.add('active');

        if (loadingInterval) clearInterval(loadingInterval);

        loadingInterval = setInterval(() => {
            stepIndex++;
            if (stepIndex < steps.length) {
                if (textEl) {
                    textEl.style.opacity = '0';
                    setTimeout(() => {
                        textEl.innerText = steps[stepIndex];
                        textEl.style.opacity = '1';
                    }, 200);
                }
                if (barEl) {
                    const percent = Math.min(95, Math.round(((stepIndex + 1) / steps.length) * 100));
                    barEl.style.width = percent + '%';
                }
            }
        }, 3200);
    };

    window.hideAILoading = function () {
        const overlay = document.getElementById('aiLoadingOverlay');
        if (overlay) overlay.classList.remove('active');
        if (loadingInterval) clearInterval(loadingInterval);
    };

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Drag & Drop Upload Initializer
    // ──────────────────────────────────────────────────────────────────────────
    window.initDropzone = function (dropzoneEl, inputEl, labelEl) {
        if (!dropzoneEl || !inputEl) return;

        ['dragenter', 'dragover'].forEach(event => {
            dropzoneEl.addEventListener(event, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropzoneEl.classList.add('drag-over');
            });
        });

        ['dragleave', 'drop'].forEach(event => {
            dropzoneEl.addEventListener(event, (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropzoneEl.classList.remove('drag-over');
            });
        });

        dropzoneEl.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            const files = dt.files;
            if (files && files.length > 0) {
                inputEl.files = files;
                if (labelEl) {
                    labelEl.innerText = files[0].name + ' (' + (files[0].size / 1024).toFixed(1) + ' KB)';
                    labelEl.classList.remove('d-none');
                }
                // Trigger change event
                inputEl.dispatchEvent(new Event('change', { bubbles: true }));
            }
        });

        dropzoneEl.addEventListener('click', () => {
            inputEl.click();
        });

        inputEl.addEventListener('change', () => {
            if (inputEl.files && inputEl.files[0] && labelEl) {
                labelEl.innerText = inputEl.files[0].name + ' (' + (inputEl.files[0].size / 1024).toFixed(1) + ' KB)';
                labelEl.classList.remove('d-none');
            }
        });
    };

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Document Ready Event Bindings
    // ──────────────────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        // Apply theme icon state
        applyTheme(getPreferredTheme());

        // Auto-convert flash messages into modern toasts if present
        const successAlert = document.querySelector('.alert-success');
        if (successAlert && successAlert.innerText.trim()) {
            // Keep original alert or show toast
        }
    });

})();
