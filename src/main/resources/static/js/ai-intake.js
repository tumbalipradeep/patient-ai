/**
 * AI Intake Assistant — client-side chat logic.
 *
 * Communicates with POST /api/ai/chat (Spring Security CSRF is exempted for
 * /api/** in SecurityConfig, so no CSRF token is required in the request body).
 * However, we still read the meta tags emitted by head.html and include the
 * header defensively in case the exemption is ever narrowed.
 *
 * SECURITY:
 * - API key is NEVER present here — it lives server-side only.
 * - Patient messages are sent to our own backend, not to any third-party directly.
 * - Structured data from the AI is displayed as text, never eval'd.
 */

(function () {
    'use strict';

    // ── DOM references ────────────────────────────────────────────────────────
    const dataEl       = document.getElementById('ai-intake-data');
    const messagesEl   = document.getElementById('chat-messages');
    const inputEl      = document.getElementById('chat-input');
    const sendBtn      = document.getElementById('btn-send');
    const clearBtn     = document.getElementById('btn-clear');
    const loadingEl    = document.getElementById('chat-loading');
    const errorEl      = document.getElementById('chat-error');
    const errorTextEl  = document.getElementById('chat-error-text');
    const charCountEl  = document.getElementById('char-count');
    const completeCard = document.getElementById('ai-complete-card');
    const structuredEl = document.getElementById('ai-structured-output');

    if (!dataEl || !messagesEl || !inputEl || !sendBtn) {
        // Guard: page elements missing — do nothing.
        return;
    }

    // ── Configuration read from data attributes (no inline script needed) ────
    const encounterId  = dataEl.getAttribute('data-encounter-id');
    const sessionStatus = dataEl.getAttribute('data-session-status') || 'IN_PROGRESS';
    const hasDraft     = dataEl.getAttribute('data-has-draft') === 'true';

    // ── CSRF token from meta tags (defensive — /api/** is CSRF-exempt) ────────
    const csrfToken  = (document.querySelector('meta[name="_csrf"]')        || {}).content;
    const csrfHeader = (document.querySelector('meta[name="_csrf_header"]') || {}).content;

    // ── Conversation history (client-side mirror sent with each request) ──────
    /** @type {Array<{role: string, content: string}>} */
    const history = [];

    // ── Character counter ─────────────────────────────────────────────────────
    inputEl.addEventListener('input', function () {
        const len = inputEl.value.length;
        charCountEl.textContent = len + ' / 4000';
        charCountEl.classList.toggle('text-danger', len >= 3800);
    });

    // ── Keyboard shortcut: Ctrl+Enter sends ──────────────────────────────────
    inputEl.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            sendMessage();
        }
    });

    // ── Send button ───────────────────────────────────────────────────────────
    sendBtn.addEventListener('click', sendMessage);

    // ── Clear button ──────────────────────────────────────────────────────────
    clearBtn.addEventListener('click', function () {
        // Remove all messages except the initial greeting (first child)
        while (messagesEl.children.length > 1) {
            messagesEl.removeChild(messagesEl.lastChild);
        }
        history.length = 0;
        hideError();
        hideCompleteCard();
        inputEl.value = '';
        charCountEl.textContent = '0 / 4000';
        inputEl.focus();
    });

    // ── Core send logic ───────────────────────────────────────────────────────
    function sendMessage() {
        const text = inputEl.value.trim();
        if (!text) {
            inputEl.focus();
            return;
        }

        // Render the user's message immediately
        appendMessage('user', text);
        history.push({ role: 'user', content: text });

        inputEl.value = '';
        charCountEl.textContent = '0 / 4000';

        setLoading(true);
        hideError();

        const payload = {
            encounterId: parseInt(encounterId, 10),
            conversationHistory: history.slice(0, -1), // history before this message
            userMessage: text
        };

        const headers = { 'Content-Type': 'application/json' };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }

        fetch('/api/ai/chat', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify(payload)
        })
        .then(function (response) {
            if (!response.ok) {
                // HTTP-level error (401, 403, 404, 500…)
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
                // AI not configured — show as an info message in the chat
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
                history.push({ role: 'assistant', content: data.reply });
            }

            if (data.complete && data.structuredData) {
                showCompleteCard(data.structuredData);
            }

            // Server has persisted and validated the draft — show the review link
            if (data.draftReady) {
                showDraftReadyBanner();
            }
        })
        .catch(function (err) {
            setLoading(false);
            showError(err.message || 'An unexpected error occurred. Please try again.');
        });
    }

    // ── DOM helpers ───────────────────────────────────────────────────────────

    /**
     * Append a chat bubble to the message area.
     * @param {'user'|'assistant'} role
     * @param {string} text  — treated as plain text, never innerHTML
     * @param {string} [extraClass]
     */
    function appendMessage(role, text, extraClass) {
        const isUser = role === 'user';

        const wrapper = document.createElement('div');
        wrapper.className = 'd-flex mb-3' + (isUser ? ' flex-row-reverse' : '');

        // Avatar
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

        // Bubble
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

    function showCompleteCard(structuredJson) {
        // Pretty-print if valid JSON; fall back to raw string
        try {
            const parsed = JSON.parse(structuredJson);
            structuredEl.textContent = JSON.stringify(parsed, null, 2);
        } catch (_) {
            structuredEl.textContent = structuredJson;
        }
        completeCard.classList.remove('d-none');
        completeCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function hideCompleteCard() {
        completeCard.classList.add('d-none');
        structuredEl.textContent = '';
    }

    /**
     * Show the persistent "draft ready" banner with a link to the review page.
     * This fires when the server returns draftReady=true (draft saved server-side).
     * It replaces the raw JSON complete card as the primary call-to-action.
     */
    function showDraftReadyBanner() {
        // Re-use or create a prominent banner above the chat
        let banner = document.getElementById('draft-ready-banner');
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'draft-ready-banner';
            banner.className = 'alert alert-success d-flex align-items-center gap-2 mb-3';
            banner.setAttribute('role', 'alert');
            const draftUrl = '/encounters/' + encounterId + '/ai-intake/draft';
            banner.innerHTML =
                '<i class="bi bi-clipboard2-check-fill fs-5 flex-shrink-0"></i>' +
                '<div><strong>AI intake draft is ready for clinician review.</strong>' +
                ' <a href="' + draftUrl + '" class="alert-link">' +
                '<i class="bi bi-arrow-right-circle me-1"></i>Review and apply draft</a></div>';
            // Insert before the chat card
            const chatCard = messagesEl.closest('.card');
            if (chatCard && chatCard.parentNode) {
                chatCard.parentNode.insertBefore(banner, chatCard);
            }
        }
        banner.classList.remove('d-none');
    }

    // ── Show draft-ready banner on page load if server says draft exists ──────
    if (hasDraft) {
        showDraftReadyBanner();
    }

    // ── Auto-focus input on page load ─────────────────────────────────────────
    inputEl.focus();

}());
