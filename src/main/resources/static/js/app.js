/**
 * PatientCase - Main Application JavaScript
 * Vanilla JS only - no external frameworks
 */

'use strict';

// Auto-dismiss alerts after 5 seconds
(function initAlerts() {
    document.addEventListener('DOMContentLoaded', function () {
        const alerts = document.querySelectorAll('.alert.alert-dismissible.fade.show');
        alerts.forEach(function (alert) {
            setTimeout(function () {
                const bsAlert = bootstrap.Alert.getOrCreateInstance(alert);
                if (bsAlert) bsAlert.close();
            }, 5000);
        });
    });
}());

// Confirm dialogs for destructive actions
(function initConfirmDialogs() {
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-confirm]').forEach(function (el) {
            el.addEventListener('click', function (e) {
                const message = el.getAttribute('data-confirm') || 'Are you sure?';
                if (!confirm(message)) {
                    e.preventDefault();
                    e.stopPropagation();
                }
            });
        });
    });
}());

// Active sidebar link highlighting
(function highlightActiveSidebarLink() {
    document.addEventListener('DOMContentLoaded', function () {
        const currentPath = window.location.pathname;
        document.querySelectorAll('.sidebar-link').forEach(function (link) {
            const href = link.getAttribute('href');
            if (href && currentPath.startsWith(href) && href !== '/') {
                link.classList.add('active');
            }
        });
    });
}());
