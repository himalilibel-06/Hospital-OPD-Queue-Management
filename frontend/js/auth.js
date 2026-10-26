/**
 * SmartOPD – Authentication & Role Selection Controller
 * File: js/auth.js
 * 
 * Manages:
 * - Real API authentication via POST /api/auth/login
 * - Role mapping: RECEPTIONIST -> Receptionist, DOCTOR -> Doctor
 * - Lockout message display on HTTP 429
 * - Unreachable server red banner notification
 * - Session expired message display (?expired=1)
 * - Zero local demo fallback passwords
 */

document.addEventListener('DOMContentLoaded', () => {
  // Role metadata for selection cards (UI labels and descriptions)
  const ROLE_CONFIG = {
    'Receptionist': {
      displayName: 'Front Desk',
      desc: 'Patient intake, triage severity & token generation'
    },
    'Doctor': {
      displayName: 'Doctor',
      desc: 'Live consultation queue & patient call controls'
    }
  };

  // DOM Elements
  const loginForm = document.getElementById('login-form');
  const roleCards = document.querySelectorAll('.role-card');
  const usernameInput = document.getElementById('username');
  const passwordInput = document.getElementById('password');
  const passwordToggleBtn = document.getElementById('toggle-password-btn');
  const submitBtnText = document.getElementById('submit-btn-text');
  const usernameError = document.getElementById('username-error');
  const passwordError = document.getElementById('password-error');
  const formGeneralError = document.getElementById('form-general-error');
  const submitBtn = document.getElementById('submit-btn');

  let activeRole = 'Receptionist'; // Default selected role

  // Check URL query parameters for session expiration notice
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.get('expired') === '1') {
    showLoginError('Session expired, please log in again.', 'warning');
  }

  /**
   * Set active role card and update button text & placeholder
   */
  function setActiveRole(roleName) {
    activeRole = roleName;

    // Update role card visual active states
    roleCards.forEach(card => {
      const cardRole = card.getAttribute('data-role');
      if (cardRole === roleName) {
        card.classList.add('active');
        card.setAttribute('aria-selected', 'true');
      } else {
        card.classList.remove('active');
        card.setAttribute('aria-selected', 'false');
      }
    });

    const config = ROLE_CONFIG[roleName];
    const roleTitle = config ? config.displayName : (roleName === 'Doctor' ? 'Doctor' : 'Front Desk');

    // Button label must always match the selected role ("Sign In as Front Desk" or "Sign In as Doctor")
    const btnTextEl = document.getElementById('submit-btn-text');
    if (btnTextEl) {
      btnTextEl.textContent = `Sign In as ${roleTitle}`;
    }

    if (usernameInput) {
      usernameInput.placeholder = roleName === 'Doctor' ? 'Enter doctor username' : 'Enter receptionist username';
    }
    clearInlineErrors();
  }

  // Bind click listeners to role cards
  roleCards.forEach(card => {
    card.addEventListener('click', () => {
      const role = card.getAttribute('data-role');
      setActiveRole(role, true);
    });

    // Keyboard accessibility for role cards (Enter / Space)
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        const role = card.getAttribute('data-role');
        setActiveRole(role, true);
      }
    });
  });

  // Password Visibility Toggle
  if (passwordToggleBtn && passwordInput) {
    passwordToggleBtn.addEventListener('click', () => {
      const isPassword = passwordInput.type === 'password';
      passwordInput.type = isPassword ? 'text' : 'password';

      if (isPassword) {
        passwordToggleBtn.innerHTML = `
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
            <line x1="1" y1="1" x2="23" y2="23"></line>
          </svg>
        `;
        passwordToggleBtn.setAttribute('aria-label', 'Hide password');
      } else {
        passwordToggleBtn.innerHTML = `
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
            <circle cx="12" cy="12" r="3"></circle>
          </svg>
        `;
        passwordToggleBtn.setAttribute('aria-label', 'Show password');
      }
    });
  }

  // Clear errors when typing
  if (usernameInput) {
    usernameInput.addEventListener('input', () => {
      usernameInput.classList.remove('is-invalid');
      if (usernameError) usernameError.classList.remove('active');
      if (formGeneralError) formGeneralError.classList.remove('active');
    });
  }

  if (passwordInput) {
    passwordInput.addEventListener('input', () => {
      passwordInput.classList.remove('is-invalid');
      if (passwordError) passwordError.classList.remove('active');
      if (formGeneralError) formGeneralError.classList.remove('active');
    });
  }

  function clearInlineErrors() {
    if (usernameInput) usernameInput.classList.remove('is-invalid');
    if (passwordInput) passwordInput.classList.remove('is-invalid');
    if (usernameError) usernameError.classList.remove('active');
    if (passwordError) passwordError.classList.remove('active');
    if (formGeneralError) formGeneralError.classList.remove('active');
  }

  function showLoginError(msg, type = 'error') {
    if (formGeneralError) {
      formGeneralError.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="8" x2="12" y2="12"></line>
          <line x1="12" y1="16" x2="12.01" y2="16"></line>
        </svg>
        <span>${msg}</span>
      `;
      formGeneralError.className = type === 'warning' ? 'alert-general-error active alert-warning' : 'alert-general-error active';
    }
    if (type !== 'warning') {
      if (usernameInput) usernameInput.classList.add('is-invalid');
      if (passwordInput) passwordInput.classList.add('is-invalid');
    }
  }

  // Handle Form Submission via REST API
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearInlineErrors();

      const username = usernameInput ? usernameInput.value.trim().toLowerCase() : '';
      const password = passwordInput ? passwordInput.value.trim() : '';

      let hasError = false;

      if (!username) {
        if (usernameInput) usernameInput.classList.add('is-invalid');
        if (usernameError) {
          usernameError.textContent = 'Please enter your username';
          usernameError.classList.add('active');
        }
        hasError = true;
      }

      if (!password) {
        if (passwordInput) passwordInput.classList.add('is-invalid');
        if (passwordError) {
          passwordError.textContent = 'Please enter your password';
          passwordError.classList.add('active');
        }
        hasError = true;
      }

      if (hasError) return;

      // Disable submit button and show spinner
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = `
          <svg class="spinner-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="2" x2="12" y2="6"></line>
            <line x1="12" y1="18" x2="12" y2="22"></line>
            <line x1="4.93" y1="4.93" x2="7.76" y2="7.76"></line>
            <line x1="16.24" y1="16.24" x2="19.07" y2="19.07"></line>
            <line x1="2" y1="12" x2="6" y2="12"></line>
            <line x1="18" y1="12" x2="22" y2="12"></line>
            <line x1="4.93" y1="19.07" x2="7.76" y2="16.24"></line>
            <line x1="16.24" y1="7.76" x2="19.07" y2="4.93"></line>
          </svg>
          <span>Authenticating...</span>
        `;
      }

      function restoreSubmitBtn() {
        if (submitBtn) {
          submitBtn.disabled = false;
          const roleTitle = ROLE_CONFIG[activeRole]?.displayName || (activeRole === 'Doctor' ? 'Doctor' : 'Front Desk');
          submitBtn.innerHTML = `
            <span id="submit-btn-text">Sign In as ${roleTitle}</span>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="5" y1="12" x2="19" y2="12"></line>
              <polyline points="12 5 19 12 12 19"></polyline>
            </svg>
          `;
        }
      }

      try {
        const apiResp = await SmartOPDApi.post('/api/auth/login', { username, password });

        if (!apiResp || !apiResp.token) {
          throw new Error('Authentication failed. No session token returned.');
        }

        // Map backend roles: RECEPTIONIST -> Receptionist, DOCTOR -> Doctor
        const roleMap = {
          'RECEPTIONIST': 'Receptionist',
          'DOCTOR': 'Doctor'
        };
        const mappedRole = roleMap[apiResp.role] || apiResp.role;

        // Verify role matches selected portal card
        if (mappedRole !== activeRole) {
          SmartOPDApi.clearToken();
          sessionStorage.removeItem('smartopd_user');
          const portalRole = activeRole === 'Doctor' ? 'Doctor' : 'Front Desk';
          showLoginError(`This account is not a ${portalRole} account`);
          restoreSubmitBtn();
          return;
        }

        // Store Bearer token in sessionStorage
        SmartOPDApi.setToken(apiResp.token);

        const sessionUser = {
          username: apiResp.username || username,
          role: mappedRole,
          name: apiResp.displayName || username,
          doctorId: apiResp.doctorId || null,
          token: apiResp.token,
          loginTime: new Date().toISOString()
        };
        sessionStorage.setItem('smartopd_user', JSON.stringify(sessionUser));

        const redirectMap = {
          'Receptionist': 'reception-dashboard.html',
          'Doctor': 'doctor-console.html'
        };
        const targetRedirect = redirectMap[mappedRole] || 'reception-dashboard.html';

        setTimeout(() => {
          window.location.href = targetRedirect;
        }, 300);

      } catch (err) {
        restoreSubmitBtn();

        if (err.status === 401) {
          showLoginError('Wrong username or password');
        } else if (err.status === 403) {
          const portalRole = activeRole === 'Doctor' ? 'Doctor' : 'Front Desk';
          showLoginError(`This account is not a ${portalRole} account`);
        } else if (err.status === 429) {
          showLoginError(err.message || 'Account is temporarily locked. Too many failed login attempts.');
        } else if (err.status === 0 || (err.message && (err.message.includes('Cannot reach') || err.message.includes('Failed to fetch')))) {
          showLoginError('Server not reachable. Start the backend with run.bat or mvn spring-boot:run');
        } else {
          showLoginError(err.message || 'Wrong username or password');
        }
      }
    });
  }

  // =========================================================================
  // First-Run System Setup Check: redirect to setup.html if database has 0 users
  // =========================================================================
  async function checkSetupStatus() {
    try {
      const resp = await SmartOPDApi.get('/api/setup/status');
      if (resp && resp.needsSetup) {
        window.location.replace('setup.html');
      }
    } catch (err) {
      // Backend may be starting or offline; standard login remains default
    }
  }

  // Initialize with Front Desk (Receptionist) role without prefilling credentials
  setActiveRole('Receptionist');
  checkSetupStatus();
});
