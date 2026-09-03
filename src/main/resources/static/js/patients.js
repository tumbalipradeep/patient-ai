/**
 * PatientCase - Patients JavaScript
 */

'use strict';

(function () {
    document.addEventListener('DOMContentLoaded', function () {

        // Auto-submit search form on clear
        const searchInput = document.querySelector('input[name="search"]');
        if (searchInput) {
            // Clear button clears and submits
            const clearBtn = document.querySelector('[href*="patients"][href$="patients"]');
        }

        // Date of birth age calculation display
        const dobInput = document.getElementById('dateOfBirth');
        const ageDisplay = document.getElementById('ageDisplay');

        if (dobInput) {
            dobInput.addEventListener('change', function () {
                if (this.value && ageDisplay) {
                    const dob = new Date(this.value);
                    const today = new Date();
                    let age = today.getFullYear() - dob.getFullYear();
                    const m = today.getMonth() - dob.getMonth();
                    if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) {
                        age--;
                    }
                    if (age >= 0 && age <= 150) {
                        ageDisplay.textContent = age + ' years';
                    }
                }
            });
        }

    });
}());
