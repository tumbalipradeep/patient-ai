/**
 * Kiosk Guided Intake — client-side wizard navigation and quick-answer chips.
 *
 * The guided questionnaire is the primary patient intake flow and works entirely
 * without an AI provider. Answers are posted together as a normal form submission
 * (no structured JSON, no AI) to /kiosk/intake/{id}/case.
 *
 * Behavior:
 *  - One section of the form visible at a time, with a progress bar and section list.
 *  - On the final section the "Next" button becomes "Save & review summary".
 *  - Quick-answer chips fill hidden bound fields so posted values match the
 *    server-side GuidedIntakeForm exactly.
 *  - Only presentation logic: all validation happens server-side.
 */

(function () {
    'use strict';

    const sections = Array.prototype.slice.call(document.querySelectorAll('.guide-section'));
    const prevBtn  = document.getElementById('guide-prev');
    const nextBtn  = document.getElementById('guide-next');
    const submitBtn= document.getElementById('guide-submit');
    const progress = document.getElementById('guide-progress');
    const stepLabel= document.getElementById('guide-step-label');
    const sectionList = document.getElementById('guide-sections-list');

    if (!sections.length || !nextBtn) {
        return;
    }

    let current = 0;

    function showSection(index) {
        sections.forEach(function (section) {
            section.classList.toggle('d-none', section.dataset.guideIndex !== String(index));
        });

        const total = sections.length;
        const pct = Math.round(((index + 1) / total) * 100);
        if (progress) {
            progress.style.width = pct + '%';
            progress.setAttribute('aria-valuenow', String(pct));
        }
        if (stepLabel) {
            stepLabel.textContent = 'Section ' + (index + 1) + ' of ' + total;
        }
        if (sectionList) {
            sectionList.querySelectorAll('li[data-guide-label]').forEach(function (li) {
                li.classList.toggle('fw-bold', li.dataset.guideLabel === String(index));
                li.classList.toggle('text-primary', li.dataset.guideLabel === String(index));
            });
        }

        if (prevBtn) {
            prevBtn.disabled = index === 0;
        }

        const last = index === total - 1;
        if (nextBtn) {
            nextBtn.classList.toggle('d-none', last);
        }
        if (submitBtn) {
            submitBtn.classList.toggle('d-none', !last);
        }
    }

    function advanceNext() {
        // Soft client-side gate so patients are less likely to send an empty form.
        const activeInput = sections[current].querySelector('#chiefComplaint');
        if (current === 0 && activeInput && !activeInput.value.trim()) {
            activeInput.classList.add('is-invalid');
            activeInput.addEventListener('input', function () {
                activeInput.classList.remove('is-invalid');
            }, { once: true });
            activeInput.focus();
            return;
        }
        if (current < sections.length - 1) {
            current += 1;
            showSection(current);
        }
    }

    function advancePrev() {
        if (current > 0) {
            current -= 1;
            showSection(current);
        }
    }

    if (nextBtn) nextBtn.addEventListener('click', advanceNext);
    if (prevBtn) prevBtn.addEventListener('click', advancePrev);

    // --- Single-select chips (chief complaint quick choices) -----------------
    document.querySelectorAll('.chip-single').forEach(function (chip) {
        chip.addEventListener('click', function () {
            const targetId = chip.dataset.target;
            const input = document.getElementById(targetId);
            if (!input) return;

            const isActive = chip.classList.contains('btn-primary');
            // Deselect the previously selected chip in this group.
            document.querySelectorAll('.chip-single[data-target="' + targetId + '"]').forEach(function (c) {
                c.classList.remove('btn-primary');
                c.classList.add('btn-outline-primary');
            });

            if (!isActive) {
                chip.classList.remove('btn-outline-primary');
                chip.classList.add('btn-primary');
                input.value = chip.textContent.trim();
                input.dispatchEvent(new Event('input', { bubbles: true }));
            }
            input.focus();
        });
    });

    // --- Multi-select chips (symptoms, safety signals) -----------------------
    document.querySelectorAll('.chip-multi').forEach(function (chip) {
        chip.addEventListener('click', function () {
            const targetName = chip.dataset.target;
            const value = chip.textContent.trim();
            const box = document.querySelector('input[name="' + targetName + '"][data-name="' +
                        CSS.escape(value) + '"]');
            if (!box) return;

            const isActive = chip.classList.contains('btn-primary') ||
                             chip.classList.contains('btn-danger');
            box.checked = !isActive;

            if (!isActive) {
                chip.classList.remove('btn-outline-primary', 'btn-outline-danger');
                chip.classList.add(chip.dataset.target === 'safetySignals'
                    ? 'btn-danger' : 'btn-primary');
            } else {
                chip.classList.remove('btn-primary', 'btn-danger');
                chip.classList.add(chip.dataset.target === 'safetySignals'
                    ? 'btn-outline-danger' : 'btn-outline-primary');
            }
        });
    });

    // Sync chip visual state based on bound checkbox values on load (e.g. after
    // a validation redirect the form is pre-filled server-side).
    document.querySelectorAll('input[type="checkbox"][data-name]').forEach(function (box) {
        const value = box.dataset.name;
        const chip = Array.prototype.slice.call(document.querySelectorAll('.chip-multi'))
            .find(function (c) { return c.textContent.trim() === value; });
        if (chip && box.checked) {
            chip.classList.remove('btn-outline-primary', 'btn-outline-danger');
            chip.classList.add(chip.dataset.target === 'safetySignals'
                ? 'btn-danger' : 'btn-primary');
        }
    });

    showSection(0);
}());