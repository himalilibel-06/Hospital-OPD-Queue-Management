/**
 * SmartOPD – Shared Application Helpers
 * File: js/app.js
 * 
 * Provides:
 * - Role guard & authentication checks
 * - Dynamic app shell initialization (user chip, mobile sidebar toggle, logout)
 * - Accessible toast notification system
 * - Real-time clock updater
 * - OPD triage & status formatting utilities
 */

// Global App Namespace
const SmartOPD = {
  /**
   * Role Guard: Verifies user authentication and role permissions.
   * Redirects unauthorized users to login.html.
   * 
   * @param {Array<string>} allowedRoles - e.g. ['Doctor'], ['Receptionist'], ['Patient']
   * @param {boolean} allowGuest - Whether unauthenticated guest access is permitted (e.g. for token tracker)
   * @returns {Object|null} The current session user object, or null if guest
   */
  requireAuth(allowedRoles = [], allowGuest = false) {
    const rawUser = sessionStorage.getItem('smartopd_user');
    const token = sessionStorage.getItem('smartopd_token');

    if (!rawUser || !token) {
      if (allowGuest) {
        // Patient tracking can be accessed without login
        this.renderGuestChip();
        return null;
      }
      if (typeof SmartOPDApi !== 'undefined') {
        SmartOPDApi.clearSession();
      } else {
        sessionStorage.removeItem('smartopd_token');
        sessionStorage.removeItem('smartopd_user');
      }
      window.location.href = 'login.html';
      return null;
    }

    let user = null;
    try {
      user = JSON.parse(rawUser);
    } catch (e) {
      console.error('Invalid user session data. Redirecting to login.');
      if (typeof SmartOPDApi !== 'undefined') {
        SmartOPDApi.clearSession();
      } else {
        sessionStorage.removeItem('smartopd_token');
        sessionStorage.removeItem('smartopd_user');
      }
      window.location.href = 'login.html';
      return null;
    }

    // Verify role authorization
    if (allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
      console.warn(`Access Denied: Role "${user.role}" is not authorized for this view.`);
      this.redirectUserToRoleDashboard(user.role);
      return null;
    }

    // Validate with GET /api/auth/me on page load
    if (typeof SmartOPDApi !== 'undefined') {
      SmartOPDApi.get('/api/auth/me')
        .then(me => {
          if (!me || !me.username) {
            SmartOPDApi.clearSession();
            window.location.href = 'login.html?expired=1';
            return;
          }
          const roleMap = { 'RECEPTIONIST': 'Receptionist', 'DOCTOR': 'Doctor', 'PATIENT': 'Patient' };
          const mappedRole = roleMap[me.role] || me.role;
          if (allowedRoles.length > 0 && !allowedRoles.includes(mappedRole)) {
            SmartOPD.redirectUserToRoleDashboard(mappedRole);
            return;
          }
          user.username = me.username;
          user.role = mappedRole;
          user.name = me.displayName || me.username;
          if (me.doctorId) user.doctorId = me.doctorId;
          sessionStorage.setItem('smartopd_user', JSON.stringify(user));
        })
        .catch(err => {
          if (err && err.status === 401) {
            SmartOPDApi.clearSession();
            window.location.href = 'login.html?expired=1';
          }
        });
    }

    return user;
  },

  /**
   * Redirects a user to their designated portal based on role.
   */
  redirectUserToRoleDashboard(role) {
    const roleRoutes = {
      'Receptionist': 'reception-dashboard.html',
      'Doctor': 'doctor-console.html',
      'Patient': 'patient-tracker.html'
    };
    window.location.href = roleRoutes[role] || 'login.html';
  },

  /**
   * Signs the user out, calls POST /api/auth/logout, clears session, and redirects to login.html.
   */
  async logout() {
    try {
      if (typeof SmartOPDApi !== 'undefined') {
        await SmartOPDApi.post('/api/auth/logout', {});
      }
    } catch (e) {
      // Ignore network failures on sign out
    } finally {
      if (typeof SmartOPDApi !== 'undefined') {
        SmartOPDApi.clearSession();
      } else {
        sessionStorage.removeItem('smartopd_token');
        sessionStorage.removeItem('smartopd_user');
      }
      window.location.href = 'login.html';
    }
  },

  /**
   * Initializes common layout elements:
   * - Mobile navigation drawer
   * - Live digital clock
   * - Topbar user chip
   * - Sign out button trigger
   */
  initLayout(activeNavKey = '') {
    // 1. Live Clock
    this.initLiveClock('live-clock');

    // 2. Mobile drawer hamburger toggle
    const menuToggle = document.getElementById('menu-toggle-btn');
    const sidebar = document.getElementById('app-sidebar');
    const backdrop = document.getElementById('sidebar-backdrop');

    if (menuToggle && sidebar && backdrop) {
      menuToggle.addEventListener('click', () => {
        sidebar.classList.toggle('open');
        backdrop.classList.toggle('active');
      });

      backdrop.addEventListener('click', () => {
        sidebar.classList.remove('open');
        backdrop.classList.remove('active');
      });
    }

    // 3. User chip in topbar
    const rawUser = sessionStorage.getItem('smartopd_user');
    if (rawUser) {
      try {
        const user = JSON.parse(rawUser);
        this.renderUserChip(user);
        this.renderSidebarNav(user.role, activeNavKey);
      } catch (e) {
        this.renderGuestChip();
      }
    } else {
      this.renderGuestChip();
      this.renderSidebarNav('Guest', activeNavKey);
    }

    // 4. Logout button
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
      logoutBtn.addEventListener('click', (e) => {
        e.preventDefault();
        this.logout();
      });
    }
  },

  /**
   * HTML escape helper to prevent XSS in dynamic templates
   */
  escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  },

  /**
   * Populates topbar user chip for authenticated user with dropdown menu
   */
  renderUserChip(user) {
    const userChip = document.getElementById('user-chip');
    if (!userChip) return;

    const initials = user.name
      ? user.name.split(' ').map(n => n[0]).slice(0, 2).join('').toUpperCase()
      : user.role.slice(0, 2).toUpperCase();

    const roleDisplay = user.role === 'Receptionist' ? 'Front Desk' : user.role;

    userChip.innerHTML = `
      <div class="user-avatar" title="${this.escapeHtml(user.name)} (${this.escapeHtml(roleDisplay)})">${this.escapeHtml(initials)}</div>
      <div class="user-info">
        <span class="user-name">${this.escapeHtml(user.name)}</span>
        <span class="user-role-badge">${this.escapeHtml(roleDisplay)}</span>
      </div>
      <svg class="user-chip-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="6 9 12 15 18 9"></polyline>
      </svg>
      <div class="user-chip-dropdown" id="user-chip-menu" role="menu">
        <button type="button" class="user-chip-dropdown-item" id="menu-change-password-btn" role="menuitem">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
          </svg>
          <span>Change password</span>
        </button>
        <div class="user-chip-dropdown-divider"></div>
        <button type="button" class="user-chip-dropdown-item text-danger" id="menu-sign-out-btn" role="menuitem">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
            <polyline points="16 17 21 12 16 7"></polyline>
            <line x1="21" y1="12" x2="9" y2="12"></line>
          </svg>
          <span>Sign out</span>
        </button>
      </div>
    `;

    // Dropdown toggle logic
    const menu = document.getElementById('user-chip-menu');
    userChip.onclick = (e) => {
      // Don't toggle if clicking inside the menu items themselves
      if (e.target.closest('#user-chip-menu')) return;
      e.stopPropagation();
      const isOpen = menu.classList.contains('show');
      menu.classList.toggle('show', !isOpen);
      userChip.classList.toggle('active', !isOpen);
    };

    // Close when clicking outside
    document.addEventListener('click', (e) => {
      if (!userChip.contains(e.target)) {
        menu.classList.remove('show');
        userChip.classList.remove('active');
      }
    });

    // Wire actions
    const changePwBtn = document.getElementById('menu-change-password-btn');
    if (changePwBtn) {
      changePwBtn.onclick = (e) => {
        e.stopPropagation();
        menu.classList.remove('show');
        userChip.classList.remove('active');
        this.openChangePasswordModal();
      };
    }

    const signOutBtn = document.getElementById('menu-sign-out-btn');
    if (signOutBtn) {
      signOutBtn.onclick = (e) => {
        e.stopPropagation();
        this.logout();
      };
    }

    // Ensure modal is prepared
    this.ensureChangePasswordModal();
  },

  /**
   * Injects the global Change Password modal into DOM
   */
  ensureChangePasswordModal() {
    if (document.getElementById('change-password-modal-backdrop')) return;

    const modalHtml = `
      <div class="modal-backdrop" id="change-password-modal-backdrop" style="display:none; position:fixed; inset:0; background-color:rgba(11,37,69,0.65); backdrop-filter:blur(3px); z-index:2000; align-items:center; justify-content:center; padding:20px;">
        <div class="modal-box" style="max-width:440px; width:100%; background:var(--bg-card,#FFFFFF); border-radius:16px; box-shadow:var(--shadow-xl); border:1px solid var(--border-light,#E2E8F0); overflow:hidden;" role="dialog" aria-modal="true" aria-labelledby="cp-modal-title">
          <div class="modal-header" style="padding:16px 20px; border-bottom:1px solid var(--border-light,#E2E8F0); display:flex; align-items:center; justify-content:space-between;">
            <h3 class="modal-title" id="cp-modal-title" style="margin:0; font-size:1.125rem; font-weight:700; color:var(--navy-dark,#0B2545);">Change Password</h3>
            <button type="button" class="btn btn-ghost btn-icon" id="cp-modal-close-btn" aria-label="Close dialog" style="background:none; border:none; cursor:pointer; color:var(--text-muted); padding:4px;">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
          </div>
          <form id="change-password-form" onsubmit="SmartOPD.submitChangePassword(event)" style="margin:0;">
            <div class="modal-body" style="padding:20px;">
              <div class="form-group mb-3">
                <label class="form-label" for="cp-current-password" style="display:block; margin-bottom:6px; font-size:0.875rem; font-weight:600; color:var(--navy-dark,#0B2545);">Current Password <span class="text-danger" style="color:var(--sev-5-emergency,#DC2626);">*</span></label>
                <div class="password-field-wrapper">
                  <input type="password" id="cp-current-password" class="form-control" placeholder="••••••••" required autocomplete="current-password" style="width:100%;">
                  <button type="button" class="password-toggle-btn" onclick="SmartOPD.togglePasswordVisibility('cp-current-password', this)" title="Show/Hide Password">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>
                  </button>
                </div>
                <div id="cp-current-error" class="text-xs text-danger" style="display:none; color:var(--sev-5-emergency,#DC2626); font-size:0.75rem; margin-top:4px;"></div>
              </div>

              <div class="form-group mb-3">
                <label class="form-label" for="cp-new-password" style="display:block; margin-bottom:6px; font-size:0.875rem; font-weight:600; color:var(--navy-dark,#0B2545);">New Password <span class="text-danger" style="color:var(--sev-5-emergency,#DC2626);">*</span></label>
                <div class="password-field-wrapper">
                  <input type="password" id="cp-new-password" class="form-control" placeholder="••••••••" required autocomplete="new-password" style="width:100%;">
                  <button type="button" class="password-toggle-btn" onclick="SmartOPD.togglePasswordVisibility('cp-new-password', this)" title="Show/Hide Password">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>
                  </button>
                </div>
                <div id="cp-new-error" class="text-xs text-danger" style="display:none; color:var(--sev-5-emergency,#DC2626); font-size:0.75rem; margin-top:4px;"></div>
                <span class="text-xs text-muted" style="display:block; font-size:0.75rem; color:var(--text-muted); margin-top:4px;">Min 8 characters, at least one letter and one digit.</span>
              </div>

              <div class="form-group mb-3">
                <label class="form-label" for="cp-confirm-password" style="display:block; margin-bottom:6px; font-size:0.875rem; font-weight:600; color:var(--navy-dark,#0B2545);">Confirm New Password <span class="text-danger" style="color:var(--sev-5-emergency,#DC2626);">*</span></label>
                <div class="password-field-wrapper">
                  <input type="password" id="cp-confirm-password" class="form-control" placeholder="••••••••" required autocomplete="new-password" style="width:100%;">
                  <button type="button" class="password-toggle-btn" onclick="SmartOPD.togglePasswordVisibility('cp-confirm-password', this)" title="Show/Hide Password">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>
                  </button>
                </div>
                <div id="cp-confirm-error" class="text-xs text-danger" style="display:none; color:var(--sev-5-emergency,#DC2626); font-size:0.75rem; margin-top:4px;"></div>
              </div>
            </div>
            <div class="modal-footer" style="padding:14px 20px; border-top:1px solid var(--border-light,#E2E8F0); display:flex; justify-content:flex-end; gap:10px;">
              <button type="button" class="btn btn-secondary" onclick="SmartOPD.closeChangePasswordModal()">Cancel</button>
              <button type="submit" class="btn btn-primary" id="cp-submit-btn">Update Password</button>
            </div>
          </form>
        </div>
      </div>
    `;

    document.body.insertAdjacentHTML('beforeend', modalHtml);

    const closeBtn = document.getElementById('cp-modal-close-btn');
    if (closeBtn) closeBtn.onclick = () => this.closeChangePasswordModal();
  },

  openChangePasswordModal() {
    this.ensureChangePasswordModal();
    const backdrop = document.getElementById('change-password-modal-backdrop');
    if (backdrop) {
      document.getElementById('cp-current-password').value = '';
      document.getElementById('cp-new-password').value = '';
      document.getElementById('cp-confirm-password').value = '';
      document.getElementById('cp-current-error').style.display = 'none';
      document.getElementById('cp-new-error').style.display = 'none';
      document.getElementById('cp-confirm-error').style.display = 'none';
      backdrop.style.display = 'flex';
      setTimeout(() => document.getElementById('cp-current-password')?.focus(), 50);
    }
  },

  closeChangePasswordModal() {
    const backdrop = document.getElementById('change-password-modal-backdrop');
    if (backdrop) backdrop.style.display = 'none';
  },

  togglePasswordVisibility(fieldId, btn) {
    const input = document.getElementById(fieldId);
    if (!input) return;
    const isPw = input.type === 'password';
    input.type = isPw ? 'text' : 'password';
    if (btn) {
      btn.innerHTML = isPw
        ? `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>`
        : `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
    }
  },

  async submitChangePassword(event) {
    event.preventDefault();
    const curPw = document.getElementById('cp-current-password').value;
    const newPw = document.getElementById('cp-new-password').value;
    const confirmPw = document.getElementById('cp-confirm-password').value;

    const curErr = document.getElementById('cp-current-error');
    const newErr = document.getElementById('cp-new-error');
    const confErr = document.getElementById('cp-confirm-error');

    curErr.style.display = 'none';
    newErr.style.display = 'none';
    confErr.style.display = 'none';

    // Validation matching server rules
    if (!curPw) {
      curErr.textContent = 'Current password is required.';
      curErr.style.display = 'block';
      return;
    }

    if (newPw.length < 8 || !/[a-zA-Z]/.test(newPw) || !/[0-9]/.test(newPw)) {
      newErr.textContent = 'Password must be at least 8 characters long and contain both a letter and a digit.';
      newErr.style.display = 'block';
      return;
    }

    if (newPw !== confirmPw) {
      confErr.textContent = 'New passwords do not match.';
      confErr.style.display = 'block';
      return;
    }

    const submitBtn = document.getElementById('cp-submit-btn');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Updating...';

    try {
      if (typeof SmartOPDApi !== 'undefined') {
        await SmartOPDApi.post('/api/auth/change-password', {
          currentPassword: curPw,
          newPassword: newPw
        });
        this.closeChangePasswordModal();
        this.showToast('Password Updated', 'Your password has been changed successfully.', 'success');
      } else {
        throw new Error('API client is not available.');
      }
    } catch (err) {
      if (err.message && err.message.toLowerCase().includes('current password')) {
        curErr.textContent = err.message;
        curErr.style.display = 'block';
      } else {
        this.showToast('Update Failed', err.message || 'Could not change password.', 'error');
      }
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Update Password';
    }
  },

  /**
   * Populates topbar user chip for guest tracker mode
   */
  renderGuestChip() {
    const userChip = document.getElementById('user-chip');
    if (!userChip) return;

    userChip.innerHTML = `
      <div class="user-avatar" style="background-color: var(--primary-teal);" title="Guest Patient">GT</div>
      <div class="user-info">
        <span class="user-name">Guest Patient</span>
        <span class="user-role-badge">Token Tracking</span>
      </div>
    `;

    // Modify logout button text to "Sign In"
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
      logoutBtn.innerHTML = `
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path>
          <polyline points="10 17 15 12 10 7"></polyline>
          <line x1="15" y1="12" x2="3" y2="12"></line>
        </svg>
        <span>Portal Sign In</span>
      `;
      logoutBtn.onclick = () => window.location.href = 'login.html';
    }
  },

  /**
   * Dynamically renders role-specific sidebar navigation items
   */
  renderSidebarNav(role, activeKey) {
    const navContainer = document.getElementById('sidebar-nav-links');
    if (!navContainer) return;

    const navMenus = {
      'Receptionist': [
        { key: 'dashboard', label: 'Dashboard', icon: '<rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line>', href: 'reception-dashboard.html' },
        { key: 'register', label: 'Register Patient', icon: '<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="16"></line><line x1="8" y1="12" x2="16" y2="12"></line>', href: 'register-patient.html' },
        { key: 'live-queue', label: 'Live Queue', icon: '<line x1="8" y1="6" x2="21" y2="6"></line><line x1="8" y1="12" x2="21" y2="12"></line><line x1="8" y1="18" x2="21" y2="18"></line><line x1="3" y1="6" x2="3.01" y2="6"></line><line x1="3" y1="12" x2="3.01" y2="12"></line><line x1="3" y1="18" x2="3.01" y2="18"></line>', href: 'queue-manager.html' },
        { key: 'token-slips', label: 'Token Slips', icon: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline>', href: 'token-slip.html' },
        { key: 'doctors', label: 'Doctors', icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle><path d="M23 21v-2a4 4 0 0 0-3-3.87"></path><path d="M16 3.13a4 4 0 0 1 0 7.75"></path>', href: 'manage-doctors.html' },
        { key: 'settings', label: 'Settings', icon: '<circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>', href: 'settings.html' },
        { key: 'analytics', label: 'Analytics', icon: '<line x1="18" y1="20" x2="18" y2="10"></line><line x1="12" y1="20" x2="12" y2="4"></line><line x1="6" y1="20" x2="6" y2="14"></line>', href: 'analytics.html' },
        { key: 'display-board', label: 'Display Board', icon: '<rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line>', href: 'display-board.html', target: '_blank' }
      ],
      'Doctor': [
        { key: 'console', label: 'Console', icon: '<path d="M22 12h-4l-3 9L9 3l-3 9H2"></path>', href: 'doctor-console.html' },
        { key: 'history', label: 'My Queue History', icon: '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 14 14"></polyline>', href: 'javascript:void(0)', badge: 'Soon', disabled: true }
      ],
      'Patient': [
        { key: 'tracker', label: 'Live Token Tracker', icon: '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>', href: 'patient-tracker.html' },
        { key: 'appointments', label: 'My Appointments', icon: '<rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line>', href: 'patient-tracker.html' }
      ],
      'Guest': [
        { key: 'tracker', label: 'Live Token Tracker', icon: '<circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline>', href: 'patient-tracker.html' },
        { key: 'login', label: 'Staff & Patient Sign In', icon: '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path><polyline points="10 17 15 12 10 7"></polyline><line x1="15" y1="12" x2="3" y2="12"></line>', href: 'login.html' }
      ]
    };

    const items = navMenus[role] || navMenus['Guest'];

    navContainer.innerHTML = items.map(item => `
      <li class="nav-item">
        <a href="${item.href}" ${item.target ? `target="${item.target}" rel="noopener noreferrer"` : ''} class="nav-link ${item.key === activeKey ? 'active' : ''} ${item.disabled ? 'disabled-nav' : ''}" ${item.disabled ? 'tabindex="-1" aria-disabled="true"' : ''}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            ${item.icon}
          </svg>
          <span>${item.label}</span>
          ${item.badge ? `<span class="nav-counter" style="background-color: rgba(255,255,255,0.18); font-size: 0.65rem; padding: 2px 6px;">${item.badge}</span>` : ''}
        </a>
      </li>
    `).join('');
  },

  /**
   * Initializes a real-time live clock in the topbar
   */
  initLiveClock(elementId) {
    const el = document.getElementById(elementId);
    if (!el) return;

    const updateTime = () => {
      const now = new Date();
      const options = {
        weekday: 'short',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: true
      };
      el.textContent = now.toLocaleDateString('en-US', options).replace(',', ' •');
    };

    updateTime();
    setInterval(updateTime, 1000);
  },

  /**
   * Accessible Toast Notifications
   * 
   * @param {string} title - Short heading
   * @param {string} message - Descriptive feedback
   * @param {'success'|'error'|'warning'|'info'} type - Toast color variant
   * @param {number} duration - Milliseconds before auto dismissal
   */
  showToast(title, message, type = 'info', duration = 3500) {
    let container = document.querySelector('.toast-container');
    if (!container) {
      container = document.createElement('div');
      container.className = 'toast-container';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.setAttribute('role', 'alert');

    // SVG icon mapping
    const icons = {
      success: `<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>`,
      error: `<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>`,
      warning: `<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>`,
      info: `<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>`
    };

    toast.innerHTML = `
      ${icons[type] || icons.info}
      <div class="toast-message">
        <div class="toast-title">${title}</div>
        <div class="toast-body text-xs text-muted">${message}</div>
      </div>
      <button class="toast-close" aria-label="Close notification">&times;</button>
    `;

    container.appendChild(toast);

    const closeBtn = toast.querySelector('.toast-close');
    const dismiss = () => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateX(100%)';
      setTimeout(() => toast.remove(), 250);
    };

    closeBtn.addEventListener('click', dismiss);
    if (duration > 0) {
      setTimeout(dismiss, duration);
    }
  },

  /**
   * Helper: Severity Badge Markup
   */
  getSeverityBadge(level) {
    const map = {
      5: { class: 'sev-pill-5', label: 'Sev 5 (Emergency)' },
      4: { class: 'sev-pill-4', label: 'Sev 4 (Urgent)' },
      3: { class: 'sev-pill-3', label: 'Sev 3 (Medium)' },
      2: { class: 'sev-pill-2', label: 'Sev 2 (Low)' },
      1: { class: 'sev-pill-1', label: 'Sev 1 (Routine)' }
    };
    const item = map[level] || map[1];
    return `<span class="sev-pill ${item.class}">${item.label}</span>`;
  },

  /**
   * Helper: Status Badge Markup
   */
  getStatusBadge(status) {
    const map = {
      'Emergency': 'badge-emergency',
      'In Consultation': 'badge-consulting',
      'Waiting': 'badge-waiting',
      'Scheduled': 'badge-scheduled',
      'Walk-in': 'badge-walkin',
      'Completed': 'badge-completed'
    };
    const cls = map[status] || 'badge-scheduled';
    return `<span class="badge ${cls}">${status}</span>`;
  }
};

// Make available on window
window.SmartOPD = SmartOPD;
