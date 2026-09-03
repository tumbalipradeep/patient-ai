/**
 * PatientCase - Appointments JavaScript
 */

'use strict';

(function () {
    document.addEventListener('DOMContentLoaded', function () {

        // Set minimum datetime to now for new appointments
        const datetimeInputs = document.querySelectorAll('input[type="datetime-local"]');
        datetimeInputs.forEach(function (input) {
            if (!input.value) {
                const now = new Date();
                now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
                now.setSeconds(0);
                now.setMilliseconds(0);
                const minValue = now.toISOString().slice(0, 16);
                input.min = minValue;
            }
        });

        // Status update confirmation for cancelled/no-show
        document.querySelectorAll('form[action*="/status"]').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                const status = form.querySelector('input[name="status"]').value;
                if (status === 'CANCELLED' || status === 'NO_SHOW') {
                    if (!confirm('Mark appointment as ' + status + '?')) {
                        e.preventDefault();
                    }
                }
            });
        });

    });
}());
