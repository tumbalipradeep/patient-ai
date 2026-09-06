/**
 * Kiosk AI Intake — client-side chat logic for patients.
 *
 * Communicates with POST /api/kiosk/chat using the CSRF token emitted by head.html.
 *
 * SECURITY:
 * - The AI provider key is NEVER present here — it lives server-side only.
 * - Messages go only to our own backend.
 * - Structured data is rendered as plain text (textContent), never eval'd or innerHTML.
 */

(function () {
    'use strict';

    const dataEl       = document.getElementById('kiosk-intake-data');
    const messagesEl   = document.getElementById('chat-messages');
    const inputEl      = document.getElementById('chat-input');
    const sendBtn      = document.getElementById('btn-send');
    const clearBtn     = document.getElementById('btn-clear');
    const loadingEl    = document.getElementById('chat-loading');
    const errorEl      = document.getElementById('chat-error');
    const errorTextEl  = document.getElementById('chat-error-text');
    const charCountEl  = document.getElementById('char-count');
    const completeCard = document.getElementById('ai-complete-card');
    const consentWarn  = document.getElementById('consent-warning');

    if (!dataEl || !messagesEl || !inputEl || !sendBtn) {
        return;
    }

    const intakeId   = dataEl.getAttribute('data-intake-id');
    const status     = dataEl.getAttribute('data-status') || 'IN_PROGRESS';
    const hasDraft   = dataEl.getAttribute('data-has-draft') === 'true';
    const hasConsent = dataEl.getAttribute('data-consent') === 'true';

    const csrfToken  = (document.querySelector('meta[name="_csrf"]')        || {}).content;
    const csrfHeader = (document.querySelector('meta[name="_csrf_header"]') || {}).content;

    inputEl.addEventListener('input', function () {
        const len = inputEl.value.length;
        charCountEl.textContent = len + ' / 4000';
        charCountEl.classList.toggle('text-danger', len >= 3800);
    });

    inputEl.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            sendMessage();
        }
    });

    sendBtn.addEventListener('click', sendMessage);

    clearBtn.addEventListener('click', function () {
        while (messagesEl.children.length > 1) {
            messagesEl.removeChild(messagesEl.lastChild);
        }
        hideError();
        hideCompleteCard();
        inputEl.value = '';
        charCountEl.textContent = '0 / 4000';
        inputEl.focus();
    });

    function sendMessage() {
        if (status !== 'IN_PROGRESS' || hasDraft || !hasConsent) {
            if (consentWarn) {
                consentWarn.classList.remove('d-none');
                messagesEl.scrollTop = messagesEl.scrollHeight;
            }
            inputEl.focus();
            return;
        }

        const text = inputEl.value.trim();
        if (!text) {
            inputEl.focus();
            return;
        }

        appendMessage('user', text);
        inputEl.value = '';
        charCountEl.textContent = '0 / 4000';
        setLoading(true);
        hideError();

        const payload = { intakeId: parseInt(intakeId, 10), userMessage: text };

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
            setLoading(false);

            if (data.disabled) {
                appendMessage('assistant',
                    '⚠️ ' + (data.reply || 'AI assistance is not currently configured.'),
                    'ai-disabled');
                return;
            }
            if (data.error) {
                showError(data.error);
                return;
            }
            if (data.reply) {
                appendMessage('assistant', data.reply);
            }
            if (data.redFlags && data.redFlags.length > 0) {
                showRedFlags(data.redFlags, data.urgentFlag);
            }
            if (data.complete && data.structuredData) {
                showCompleteCard();
            }
            if (data.draftReady) {
                showDraftReadyBanner();
            }
        })
        .catch(function (err) {
            setLoading(false);
            showError(err.message || 'An unexpected error occurred. Please try again.');
        });
    }

    function appendMessage(role, text, extraClass) {
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
        bubble.className = 'border rounded-3 px-3 py-2 shadow-sm' + (extraClass ? ' ' + extraClass : '');
        bubble.style.maxWidth = '520px';
        bubble.style.backgroundColor = isUser ? '#e9ecef' : '#ffffff';

        const p = document.createElement('p');
        p.className = 'mb-0 small';
        p.textContent = text;  // textContent — XSS-safe
        bubble.appendChild(p);
        bubbleWrap.appendChild(bubble);

        const label = document.createElement('div');
        label.className = 'text-muted mt-1';
        label.style.fontSize = '0.7rem';
        label.textContent = isUser ? 'You' : 'AI Assistant';
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

    function setLoading(visible) {
        sendBtn.disabled = visible;
        inputEl.disabled = visible;
        loadingEl.classList.toggle('d-none', !visible);
        if (visible) scrollToBottom();
    }

    function showError(message) {
        errorTextEl.textContent = message;
        errorEl.classList.remove('d-none');
        scrollToBottom();
    }

    function hideError() {
        errorEl.classList.add('d-none');
        errorTextEl.textContent = '';
    }

    function showCompleteCard() {
        completeCard.classList.remove('d-none');
        completeCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function hideCompleteCard() {
        completeCard.classList.add('d-none');
    }

    function showDraftReadyBanner() {
        if (hasDraft) return;
        let banner = document.getElementById('draft-ready-banner');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'draft-ready-banner';
            banner.className = 'alert alert-success d-flex align-items-center gap-2 mb-4';
            banner.setAttribute('role', 'alert');
            banner.innerHTML =
                '<i class="bi bi-clipboard2-check-fill fs-5 flex-shrink-0"></i>' +
                '<div class="flex-grow-1"><strong>Your intake summary is ready to review.</strong></div>' +
                '<a href="/kiosk/intake/' + intakeId + '/summary" class="btn btn-sm btn-success">' +
                'Review summary <i class="bi bi-arrow-right"></i></a>';
            const chatCard = messagesEl.closest('.card');
            if (chatCard && chatCard.parentNode) {
                chatCard.parentNode.insertBefore(banner, chatCard);
            }
        }
        banner.classList.remove('d-none');
    }

    function showRedFlags(flags, urgent) {
        let container = document.getElementById('red-flag-notice');
        if (!container) {
            container = document.createElement('div');
            container.id = 'red-flag-notice';
            container.className = urgent ? 'alert alert-danger mb-3' : 'alert alert-warning mb-3';
            container.setAttribute('role', 'alert');

            const heading = document.createElement('strong');
            heading.textContent = urgent
                ? '⚠️ Important — Please seek immediate medical attention.'
                : '⚠️ Note for your physician:';
            container.appendChild(heading);

            const disclaimer = document.createElement('p');
            disclaimer.className = 'mb-1 small';
            disclaimer.textContent = urgent
                ? 'Based on what you have described, this may require urgent care. Please contact ' +
                  'emergency services or go to your nearest emergency department immediately.'
                : 'The following notes are based only on what you reported. They are not a ' +
                  'diagnosis. A clinician will review them.';
            container.appendChild(disclaimer);

            const list = document.createElement('ul');
            list.className = 'mb-0 small';
            flags.forEach(function (flag) {
                const li = document.createElement('li');
                li.textContent = flag;  // textContent — XSS-safe
                list.appendChild(li);
            });
            container.appendChild(list);

            if (messagesEl.parentNode) {
                messagesEl.parentNode.insertBefore(container, messagesEl);
            }
        }
    }

    if (hasDraft || status !== 'IN_PROGRESS') {
        showDraftReadyBanner();
        sendBtn.disabled = true;
        inputEl.disabled = true;
    }

    inputEl.focus();
}());