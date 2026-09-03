/**
 * PatientCase - Case Taking JavaScript
 * Handles dynamic rows for symptoms, examinations, diagnoses, treatments
 */

'use strict';

(function () {
    document.addEventListener('DOMContentLoaded', function () {

        // ---- Dynamic Row Helpers ----

        function getRowCount(containerSelector, rowClass) {
            return document.querySelectorAll(containerSelector + ' .' + rowClass).length;
        }

        function removeEmptyMessages(containerSelector) {
            const msgs = document.querySelectorAll(containerSelector + ' [class*="no-"]');
            msgs.forEach(function (msg) { msg.style.display = 'none'; });
        }

        // Remove row buttons
        document.addEventListener('click', function (e) {
            if (e.target.closest('.remove-row')) {
                const row = e.target.closest('.symptom-row, .examination-row, .diagnosis-row, .treatment-row');
                if (row) {
                    if (confirm('Remove this entry?')) {
                        row.remove();
                    }
                }
            }
        });

        // ---- Symptoms ----

        const addSymptomBtn = document.getElementById('addSymptomBtn');
        const symptomsContainer = document.getElementById('symptomsContainer');

        if (addSymptomBtn && symptomsContainer) {
            addSymptomBtn.addEventListener('click', function () {
                removeEmptyMessages('#symptomsContainer');
                const idx = symptomsContainer.querySelectorAll('.symptom-row').length;
                const html = createSymptomRow(idx);
                symptomsContainer.insertAdjacentHTML('beforeend', html);
            });
        }

        function createSymptomRow(idx) {
            return `
            <div class="symptom-row card bg-light mb-3 p-3">
                <div class="row g-2 align-items-end">
                    <div class="col-md-3">
                        <label class="form-label small">Symptom</label>
                        <input type="text" name="symptoms[${idx}].name"
                               class="form-control form-control-sm" placeholder="Symptom name"/>
                    </div>
                    <div class="col-md-2">
                        <label class="form-label small">Duration</label>
                        <input type="text" name="symptoms[${idx}].duration"
                               class="form-control form-control-sm" placeholder="e.g. 2 weeks"/>
                    </div>
                    <div class="col-md-2">
                        <label class="form-label small">Severity</label>
                        <select name="symptoms[${idx}].severity" class="form-select form-select-sm">
                            <option value="MILD">MILD</option>
                            <option value="MODERATE">MODERATE</option>
                            <option value="SEVERE">SEVERE</option>
                        </select>
                    </div>
                    <div class="col-md-2">
                        <label class="form-label small">Onset</label>
                        <select name="symptoms[${idx}].onset" class="form-select form-select-sm">
                            <option value="UNKNOWN">UNKNOWN</option>
                            <option value="SUDDEN">SUDDEN</option>
                            <option value="GRADUAL">GRADUAL</option>
                        </select>
                    </div>
                    <div class="col-md-2">
                        <label class="form-label small">Notes</label>
                        <input type="text" name="symptoms[${idx}].notes"
                               class="form-control form-control-sm" placeholder="Notes"/>
                    </div>
                    <div class="col-md-1">
                        <button type="button" class="btn btn-outline-danger btn-sm remove-row" aria-label="Remove symptom">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </div>
            </div>`;
        }

        // ---- Examinations ----

        const addExaminationBtn = document.getElementById('addExaminationBtn');
        const examinationsContainer = document.getElementById('examinationsContainer');

        if (addExaminationBtn && examinationsContainer) {
            addExaminationBtn.addEventListener('click', function () {
                removeEmptyMessages('#examinationsContainer');
                const idx = examinationsContainer.querySelectorAll('.examination-row').length;
                examinationsContainer.insertAdjacentHTML('beforeend', createExaminationRow(idx));
            });
        }

        function createExaminationRow(idx) {
            return `
            <div class="examination-row card bg-light mb-3 p-3">
                <div class="row g-2 align-items-end">
                    <div class="col-md-3">
                        <label class="form-label small">Examination Area</label>
                        <input type="text" name="examinations[${idx}].examinationArea"
                               class="form-control form-control-sm" placeholder="e.g. Cardiovascular"/>
                    </div>
                    <div class="col-md-5">
                        <label class="form-label small">Findings</label>
                        <input type="text" name="examinations[${idx}].findings"
                               class="form-control form-control-sm" placeholder="Clinical findings"/>
                    </div>
                    <div class="col-md-3">
                        <label class="form-label small">Notes</label>
                        <input type="text" name="examinations[${idx}].notes"
                               class="form-control form-control-sm" placeholder="Notes"/>
                    </div>
                    <div class="col-md-1">
                        <button type="button" class="btn btn-outline-danger btn-sm remove-row" aria-label="Remove">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </div>
            </div>`;
        }

        // ---- Diagnoses ----

        const addDiagnosisBtn = document.getElementById('addDiagnosisBtn');
        const diagnosesContainer = document.getElementById('diagnosesContainer');

        if (addDiagnosisBtn && diagnosesContainer) {
            addDiagnosisBtn.addEventListener('click', function () {
                removeEmptyMessages('#diagnosesContainer');
                const idx = diagnosesContainer.querySelectorAll('.diagnosis-row').length;
                diagnosesContainer.insertAdjacentHTML('beforeend', createDiagnosisRow(idx));
            });
        }

        function createDiagnosisRow(idx) {
            return `
            <div class="diagnosis-row card bg-light mb-3 p-3">
                <div class="row g-2 align-items-end">
                    <div class="col-md-4">
                        <label class="form-label small">Diagnosis</label>
                        <input type="text" name="diagnoses[${idx}].diagnosis"
                               class="form-control form-control-sm" placeholder="Diagnosis"/>
                    </div>
                    <div class="col-md-2">
                        <label class="form-label small">Status</label>
                        <select name="diagnoses[${idx}].status" class="form-select form-select-sm">
                            <option value="SUSPECTED">SUSPECTED</option>
                            <option value="CONFIRMED">CONFIRMED</option>
                            <option value="RULED_OUT">RULED_OUT</option>
                        </select>
                    </div>
                    <div class="col-md-5">
                        <label class="form-label small">Notes</label>
                        <input type="text" name="diagnoses[${idx}].notes"
                               class="form-control form-control-sm" placeholder="Notes"/>
                    </div>
                    <div class="col-md-1">
                        <button type="button" class="btn btn-outline-danger btn-sm remove-row" aria-label="Remove">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </div>
            </div>`;
        }

        // ---- Treatments ----

        const addTreatmentBtn = document.getElementById('addTreatmentBtn');
        const treatmentsContainer = document.getElementById('treatmentsContainer');

        if (addTreatmentBtn && treatmentsContainer) {
            addTreatmentBtn.addEventListener('click', function () {
                removeEmptyMessages('#treatmentsContainer');
                const idx = treatmentsContainer.querySelectorAll('.treatment-row').length;
                treatmentsContainer.insertAdjacentHTML('beforeend', createTreatmentRow(idx));
            });
        }

        function createTreatmentRow(idx) {
            return `
            <div class="treatment-row card bg-light mb-3 p-3">
                <div class="row g-2 align-items-end">
                    <div class="col-md-3">
                        <label class="form-label small">Treatment</label>
                        <input type="text" name="treatments[${idx}].treatment"
                               class="form-control form-control-sm" placeholder="Treatment"/>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label small">Instructions</label>
                        <input type="text" name="treatments[${idx}].instructions"
                               class="form-control form-control-sm" placeholder="Instructions"/>
                    </div>
                    <div class="col-md-4">
                        <label class="form-label small">Notes</label>
                        <input type="text" name="treatments[${idx}].notes"
                               class="form-control form-control-sm" placeholder="Notes"/>
                    </div>
                    <div class="col-md-1">
                        <button type="button" class="btn btn-outline-danger btn-sm remove-row" aria-label="Remove">
                            <i class="bi bi-trash"></i>
                        </button>
                    </div>
                </div>
            </div>`;
        }

        // ---- Form submission handling ----
        const caseTakingForm = document.getElementById('caseTakingForm');
        if (caseTakingForm) {
            // Re-index rows before submit to ensure sequential indices
            caseTakingForm.addEventListener('submit', function () {
                reindexRows('symptom-row', 'symptoms');
                reindexRows('examination-row', 'examinations');
                reindexRows('diagnosis-row', 'diagnoses');
                reindexRows('treatment-row', 'treatments');
            });
        }

        function reindexRows(rowClass, prefix) {
            const rows = document.querySelectorAll('.' + rowClass);
            rows.forEach(function (row, idx) {
                row.querySelectorAll('[name]').forEach(function (el) {
                    const name = el.getAttribute('name');
                    // Replace the index in the name e.g. symptoms[0].name -> symptoms[idx].name
                    const newName = name.replace(/\[\d+\]/, '[' + idx + ']');
                    el.setAttribute('name', newName);
                });
            });
        }

    });
}());
