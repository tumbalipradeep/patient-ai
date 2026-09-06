/**
 * MediKiosk Clinical History Assistant — patient-facing AI intake client.
 *
 * Conversational UX:
 *  - One clinical question at a time (big question from the last assistant bubble).
 *  - Tappable quick-answer chips with an "Other" free-text path.
 *  - Full conversation is stored server-side; this client renders the server
 *    history on load and only appends new turns.
 *  - Send is disabled while a turn is in flight (no duplicate submits), and a
 *    clientTurnId makes repeated sends idempotent server-side.
 *  - On a transient provider/timeout failure a Retry action re-sends the same
 *    turn (same clientTurnId) so the server answers from its stored record
 *    instead of producing a second response.
 *  - "Start over" clears the server-side conversation (POST /api/kiosk/chat/reset)
 *    and reloads, so the browser and server can never disagree.
 *
 * Communicates with POST /api/kiosk/chat using the CSRF token emitted by head.html.
 *
 * SECURITY:
 *  - The AI provider key is NEVER present here — it lives server-side only.
 *  - Messages go only to our own backend.
 *  - All dynamic content is rendered with textContent/createElement — never innerHTML.
 *  - The panel only exists in the page when the server rendered it (AI enabled).
 */

(function () {
    'use strict';

    const dataEl       = document.getElementById('kiosk-intake-data');
    const messagesEl   = document.getElementById('chat-messages');
    const inputEl      = document.getElementById('chat-input');
    const sendBtn      = document.getElementById('btn-send');
    const resetBtn     = document.getElementById('btn-reset');
    const loadingEl    = document.getElementById('chat-loading');
    const errorEl      = document.getElementById('chat-error');
    const errorTextEl  = document.getElementById('chat-error-text');
    const errorCloseBtn= document.getElementById('chat-error-close');
    const retryBtn     = document.getElementById('btn-retry');
    const charCountEl  = document.getElementById('char-count');
    const chipsEl      = document.getElementById('chat-chips');
    const sectionLabelEl = document.getElementById('ai-section-label');
    const sectionValueEl = document.getElementById('ai-section-value');
    const progressBarEl  = document.getElementById('ai-progress-bar');
    const factsPanelEl   = document.getElementById('facts-panel');
    const factsBodyEl    = document.getElementById('facts-body');
    const inferredPanelEl= document.getElementById('inferred-panel');
    const inferredBodyEl = document.getElementById('inferred-body');
    const completeCard   = document.getElementById('ai-complete-card');
    const consentWarn    = document.getElementById('consent-warning');

    if (!dataEl) {
        return;
    }

    const intakeId   = dataEl.getAttribute('data-intake-id');
    const status     = dataEl.getAttribute('data-status') || 'IN_PROGRESS';
    const hasDraft   = dataEl.getAttribute('data-has-draft') === 'true';
    const hasConsent = dataEl.getAttribute('data-consent') === 'true';

    const csrfToken  = (document.querySelector('meta[name="_csrf"]')        || {}).content;
    const csrfHeader = (document.querySelector('meta[name="_csrf_header"]') || {}).content;

    if (!messagesEl || !inputEl || !sendBtn) {
        return;
    }

    const SECTION_LABELS = {
        CHIEF_COMPLAINT: 'About your main concern',
        HPI:               'About your symptoms',
        PAST_HISTORY:      'About your past health',
        MEDICATIONS:       'About medications',
        ALLERGIES:         'About allergies',
        LIFESTYLE:         'About your daily life',
        SAFETY:            'Safety questions',
        OTHER:             'About your visit'
    };

    let busy = false;
    let lastAttempt = null; // { id, text } — kept for idempotent retry

    // ---- Turn identity ------------------------------------------------------

    function nextTurnId() {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
            return 't-' + window.crypto.randomUUID();
        }
        return 't-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10);
    }

    // ---- Input plumbing -----------------------------------------------------

    inputEl.addEventListener('input', function () {
        const len = inputEl.value.length;
        charCountEl.textContent = len + ' / 4000';
        charCountEl.classList.toggle('text-danger', len >= 3800);
    });

    function submitFromInput() {
        sendMessage(inputEl.value);
    }

    inputEl.addEventListener('keydown', function (e) {
        // Enter sends (Shift+Enter inserts a newline); Ctrl/Cmd+Enter also sends.
        if (e.key === 'Enter' && !e.shiftKey && !e.ctrlKey && !e.metaKey) {
            e.preventDefault();
            submitFromInput();
        } else if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            submitFromInput();
        }
    });

    sendBtn.addEventListener('click', submitFromInput);

    if (errorCloseBtn) {
        errorCloseBtn.addEventListener('click', hideError);
    }

    if (retryBtn) {
        retryBtn.addEventListener('click', function () {
            if (!lastAttempt) return;
            sendMessage(lastAttempt.text, { retryId: lastAttempt.id });
        });
    }

    if (resetBtn) {
        resetBtn.addEventListener('click', startOver);
    }

    // ---- Core send ----------------------------------------------------------

    /**
     * @param {string} rawText the answer, possibly blank
     * @param {{retryId?: string}} [opts] pass retryId to re-send the same turn
     *        idempotently after a transient failure
     */
    function sendMessage(rawText, opts) {
        if (busy) {
            return; // duplicate submit — ignored while a turn is in flight
        }
        if (status !== 'IN_PROGRESS' || hasDraft || !hasConsent) {
            if (consentWarn) {
                consentWarn.classList.remove('d-none');
                scrollToBottom();
            }
            inputEl.focus();
            return;
        }

        const text = (rawText || '').trim();
        const isRetry = opts && opts.retryId;

        if (!text) {
            if (isRetry && lastAttempt) {
                // nothing to do
                return;
            }
            inputEl.focus();
            return;
        }

        const turnId = isRetry ? opts.retryId : nextTurnId();
        lastAttempt = { id: turnId, text: text };

        if (!isRetry) {
            appendBubble('user', text);
            clearChips();
        }
        inputEl.value = '';
        charCountEl.textContent = '0 / 4000';
        setBusy(true);

        const payload = {
            intakeId: parseInt(intakeId, 10),
            userMessage: text,
            clientTurnId: turnId
        };

        const headers = { 'Content-Type': 'application/json' };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }

        fetch('/api/kiosk/chat', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(payload)
        })
        .then(function (response) {
            if (!response.ok) {
                return response.json().catch(function () {
                    return { error: 'Server returned status ' + response.status };
                }).then(function (body) {
                    throw new Error(body.error || 'Unexpected server error.');
                });
            }
            return response.json();
        })
        .then(function (data) {
            setBusy(false);
            hideError();

            if (data.disabled) {
                appendBubble('assistant',
                    (data.reply || 'The AI assistant is not available right now.') +
                    ' You can continue with the guided questions on this page.');
                freezeChat();
                return;
            }
            if (data.error) {
                showError(data.error, data.retryable);
                return;
            }
            lastAttempt = null;

            if (data.reply) {
                appendBubble('assistant', data.reply);
            }
            if (data.redFlags && data.redFlags.length > 0) {
                showRedFlags(data.redFlags, data.urgentFlag);
            }
            applySection(data.section, data.sectionProgress);
            renderFacts(data.patientReportedFacts || [], data.inferredInformation || []);
            renderChips(data.suggestedAnswers || [], data.allowOtherText !== false);
            if (data.complete && data.structuredData) {
                showCompleteCard();
                freezeChat();
            }
            if (data.draftReady) {
                showDraftReadyBanner();
            }
        })
        .catch(function (err) {
            setBusy(false);
            showError(err.message || 'An unexpected error occurred. Please try again.', true);
        });
    }

    // ---- UI helpers ---------------------------------------------------------

    function appendBubble(role, text) {
        const isUser = role === 'user';

        const wrapper = document.createElement('div');
        wrapper.className = 'd-flex mb-3' + (isUser ? ' flex-row-reverse' : '');

        const avatarWrap = document.createElement('div');
        avatarWrap.className = 'flex-shrink-0 ' + (isUser ? 'ms-2' : 'me-2');

        const avatar = document.createElement('span');
        avatar.className = 'avatar-sm rounded-circle d-flex align-items-center justify-content-center';
        avatar.style.cssText = 'width:32px;height:32px;';
        avatar.style.backgroundColor = isUser ? '#6c757d' : '#0d6efd';

        const icon = document.createElement('i');
        icon.className = (isUser ? 'bi bi-person-fill' : 'bi bi-robot') + ' text-white';
        icon.style.fontSize = '0.85rem';
        avatar.appendChild(icon);
        avatarWrap.appendChild(avatar);

        const bubbleWrap = document.createElement('div');
        const bubble = document.createElement('div');
        bubble.className = 'border rounded-3 px-3 py-2 shadow-sm';
        bubble.style.maxWidth = '520px';
        bubble.style.backgroundColor = isUser ? '#e9ecef' : '#ffffff';
        bubble.style.whiteSpace = 'pre-wrap';

        const p = document.createElement('p');
        p.className = 'mb-0';
        p.textContent = text;  // textContent — XSS-safe
        bubble.appendChild(p);
        bubbleWrap.appendChild(bubble);

        const label = document.createElement('div');
        label.className = 'text-muted mt-1';
        label.style.fontSize = '0.7rem';
        label.textContent = isUser ? 'You' : 'MediKiosk Assistant';
        bubbleWrap.appendChild(label);

        if (isUser) {
            wrapper.appendChild(bubbleWrap);
            wrapper.appendChild(avatarWrap);
        } else {
            wrapper.appendChild(avatarWrap);
            wrapper.appendChild(bubbleWrap);
        }

        messagesEl.appendChild(wrapper);
        scrollToBottom();
    }

    function scrollToBottom() {
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function setBusy(visible) {
        busy = visible;
        sendBtn.disabled = visible;
        inputEl.disabled = visible;
        loadingEl.classList.toggle('d-none', !visible);
        if (visible) scrollToBottom();
    }

    function freezeChat() {
        sendBtn.disabled = true;
        inputEl.disabled = true;
    }

    function showError(message, retryable) {
        errorTextEl.textContent = message;
        errorEl.classList.remove('d-none');
        retryBtn.classList.toggle('d-none', !retryable);
        scrollToBottom();
    }

    function hideError() {
        if (!errorEl) return;
        errorEl.classList.add('d-none');
        if (errorTextEl) errorTextEl.textContent = '';
        if (retryBtn) retryBtn.classList.add('d-none');
    }

    function clearChips() {
        while (chipsEl.firstChild) {
            chipsEl.removeChild(chipsEl.firstChild);
        }
    }

    function renderChips(suggestedAnswers, allowOther) {
        clearChips();
        (suggestedAnswers || []).forEach(function (answer) {
            const chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'btn btn-outline-primary btn-sm rounded-pill';
            chip.textContent = answer; // textContent — XSS-safe
            chip.setAttribute('aria-label', 'Answer: ' + answer);
            chip.addEventListener('click', function () {
                sendMessage(answer);
            });
            chipsEl.appendChild(chip);
        });
        if (allowOther && suggestedAnswers && suggestedAnswers.length > 0) {
            const other = document.createElement('button');
            other.type = 'button';
            other.className = 'btn btn-outline-secondary btn-sm rounded-pill';
            other.textContent = 'Other';
            other.addEventListener('click', function () {
                inputEl.focus();
            });
            chipsEl.appendChild(other);
        }
    }

    function applySection(section, progress) {
        const label = SECTION_LABELS[section] || SECTION_LABELS.OTHER;
        if (sectionLabelEl) sectionLabelEl.textContent = label;
        const pct = (typeof progress === 'number') ? Math.max(0, Math.min(100, Math.round(progress))) : 0;
        if (sectionValueEl) sectionValueEl.textContent = pct + '%';
        if (progressBarEl) {
            progressBarEl.style.width = pct + '%';
            progressBarEl.setAttribute('aria-valuenow', String(pct));
        }
    }

    function renderFacts(patientFacts, inferred) {
        if (!factsPanelEl || !factsBodyEl) return;
        if (!patientFacts || patientFacts.length === 0) {
            factsPanelEl.classList.add('d-none');
            while (factsBodyEl.firstChild) factsBodyEl.removeChild(factsBodyEl.firstChild);
        } else {
            while (factsBodyEl.firstChild) factsBodyEl.removeChild(factsBodyEl.firstChild);
            patientFacts.forEach(function (fact) {
                const li = document.createElement('li');
                li.textContent = fact; // textContent — XSS-safe
                factsBodyEl.appendChild(li);
            });
            factsPanelEl.classList.remove('d-none');
        }

        if (!inferredPanelEl || !inferredBodyEl) return;
        if (!inferred || inferred.length === 0) {
            inferredPanelEl.classList.add('d-none');
            while (inferredBodyEl.firstChild) inferredBodyEl.removeChild(inferredBodyEl.firstChild);
        } else {
            while (inferredBodyEl.firstChild) inferredBodyEl.removeChild(inferredBodyEl.firstChild);
            inferred.forEach(function (item) {
                const li = document.createElement('li');
                li.textContent = item; // textContent — XSS-safe
                inferredBodyEl.appendChild(li);
            });
            inferredPanelEl.classList.remove('d-none');
        }
    }

    function showRedFlags(flags, urgent) {
        let container = document.getElementById('red-flag-notice');
        if (container) {
            container.parentNode.removeChild(container);
        }
        container = document.createElement('div');
        container.id = 'red-flag-notice';
        container.className = urgent ? 'alert alert-danger mb-3' : 'alert alert-warning mb-3';
        container.setAttribute('role', 'alert');

        const heading = document.createElement('strong');
        heading.textContent = urgent
            ? 'Important — Please seek immediate medical attention.'
            : 'A note for your physician:';
        container.appendChild(heading);

        const disclaimer = document.createElement('p');
        disclaimer.className = 'mb-1 small';
        disclaimer.textContent = urgent
            ? 'Based only on what you have described, this may require urgent care. ' +
              'Please contact emergency services or go to your nearest emergency department immediately. ' +
              'The notes below are not a diagnosis — a clinician will review them.'
            : 'The following notes are based only on what you reported. They are not a ' +
              'diagnosis. A clinician will review them.';
        container.appendChild(disclaimer);

        const list = document.createElement('ul');
        list.className = 'mb-0 small';
        flags.forEach(function (flag) {
            const li = document.createElement('li');
            li.textContent = flag; // textContent — XSS-safe
            list.appendChild(li);
        });
        container.appendChild(list);

        const insertBefore = completeCard || messagesEl;
        if (insertBefore && insertBefore.parentNode) {
            insertBefore.parentNode.insertBefore(container, insertBefore);
        }
    }

    function showCompleteCard() {
        completeCard.classList.remove('d-none');
        completeCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function showDraftReadyBanner() {
        if (hasDraft) return;
        let banner = document.getElementById('draft-ready-banner');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'draft-ready-banner';
            banner.className = 'alert alert-success d-flex align-items-center gap-2 mb-4';
            banner.setAttribute('role', 'alert');

            const icon = document.createElement('i');
            icon.className = 'bi bi-clipboard2-check-fill fs-5 flex-shrink-0';
            banner.appendChild(icon);

            const body = document.createElement('div');
            body.className = 'flex-grow-1';
            const strong = document.createElement('strong');
            strong.textContent = 'Your intake summary is ready to review.');
            body.appendChild(strong);
            banner.appendChild(body);

            const link = document.createElement('a');
            link.href = '/kiosk/intake/' + intakeId + '/summary';
            link.className = 'btn btn-sm btn-success';
            link.textContent = 'Review summary';
            banner.appendChild(link);

            const chatCard = messagesEl.closest('.card');
            if (chatCard && chatCard.parentNode) {
                chatCard.parentNode.insertBefore(banner, chatCard);
            }
        }
        banner.classList.remove('d-none');
    }

    // ---- Start over ---------------------------------------------------------

    function startOver() {
        if (busy) return;
        if (!window.confirm('Start the interview over from the beginning? Your current ' +
                'answers to the assistant will be cleared.')) {
            return;
        }
        const payload = { intakeId: parseInt(intakeId, 10) };
        const headers = { 'Content-Type': 'application/json' };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }
        fetch('/api/kiosk/chat/reset', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(payload)
        })
        .then(function (response) {
            if (!response.ok) {
                return response.json().catch(function () {
                    return { error: 'Server returned status ' + response.status };
                }).then(function (body) {
                    throw new Error(body.error || 'Could not start over.');
                });
            }
            return response.json();
        })
        .then(function (data) {
            if (data.error) {
                showError(data.error, false);
                return;
            }
            // Server conversation cleared — reload to render the fresh, empty
            // conversation from the server (source of truth).
            window.location.reload();
        })
        .catch(function (err) {
            showError(err.message || 'Could not start over. Please refresh the page.', false);
        });
    }

    // ---- Init on page load ----------------------------------------------------

    if (hasDraft || status !== 'IN_PROGRESS') {
        showDraftReadyBanner();
        sendBtn.disabled = true;
        inputEl.disabled = true;
    }

    inputEl.focus();
}());