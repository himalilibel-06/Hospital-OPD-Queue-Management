/**
 * SmartOPD – REST API Client
 * File: js/api-client.js
 * 
 * Production HTTP client communicating with Spring Boot REST backend:
 * - Same-origin relative URLs (/api/...) for localhost and LAN/mobile access
 * - Automatic Bearer token header injection from sessionStorage
 * - Automatic redirect to login.html?expired=1 on HTTP 401
 * - Standardized ApiError with status and backend error message
 * - Visibility-aware polling helper with exponential backoff on errors
 */

class ApiError extends Error {
  constructor(message, status = 0, data = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

const SmartOPDApi = {
  /**
   * Returns base URL: empty string for browser same-origin,
   * or localhost:8080 when running in Node.js test environment.
   */
  getBaseUrl() {
    if (typeof window === 'undefined') {
      return (typeof process !== 'undefined' && process.env && process.env.BASE)
        ? process.env.BASE.replace(/\/+$/, '')
        : 'http://localhost:8080';
    }
    return '';
  },

  getToken() {
    if (typeof sessionStorage === 'undefined') return null;
    return sessionStorage.getItem('smartopd_token') || null;
  },

  setToken(token) {
    if (typeof sessionStorage === 'undefined') return;
    if (token) {
      sessionStorage.setItem('smartopd_token', token);
    } else {
      sessionStorage.removeItem('smartopd_token');
    }
  },

  clearSession() {
    if (typeof sessionStorage === 'undefined') return;
    sessionStorage.removeItem('smartopd_token');
    sessionStorage.removeItem('smartopd_user');
  },

  /**
   * Core request wrapper
   */
  async request(path, options = {}) {
    const cleanPath = path.startsWith('/') ? path : `/${path}`;
    const url = `${this.getBaseUrl()}${cleanPath}`;
    const token = this.getToken();

    const headers = {
      'Accept': 'application/json',
      ...(options.headers || {})
    };

    if (token && !headers['Authorization']) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    let body = options.body;
    if (body && typeof body === 'object' && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch(url, { ...options, headers, body });
    } catch (networkErr) {
      throw new ApiError('Cannot reach the SmartOPD server', 0, null);
    }

    // 401 Unauthorized handling for protected calls
    if (response.status === 401 && !cleanPath.includes('/api/auth/login')) {
      this.clearSession();
      if (typeof window !== 'undefined' && !window.location.pathname.includes('login.html')) {
        window.location.href = 'login.html?expired=1';
      }
    }

    if (response.status === 204) {
      return null;
    }

    const contentType = response.headers.get('content-type') || '';
    const isJson = contentType.includes('application/json');
    let data;
    try {
      data = isJson ? await response.json() : await response.text();
    } catch (parseErr) {
      data = null;
    }

    if (!response.ok) {
      let message = `HTTP ${response.status}: ${response.statusText}`;
      if (data && typeof data === 'object') {
        message = data.message || data.error || message;
      } else if (typeof data === 'string' && data.trim()) {
        message = data;
      }
      throw new ApiError(message, response.status, data);
    }

    return data;
  },

  get(path, options = {}) {
    return this.request(path, { ...options, method: 'GET' });
  },

  post(path, body = null, options = {}) {
    return this.request(path, { ...options, method: 'POST', body });
  },

  put(path, body = null, options = {}) {
    return this.request(path, { ...options, method: 'PUT', body });
  },

  del(path, options = {}) {
    return this.request(path, { ...options, method: 'DELETE' });
  },

  delete(path, options = {}) {
    return this.del(path, options);
  },

  /**
   * Visibility-aware polling helper
   * - Pauses when the browser tab is hidden
   * - Backs off to 15 seconds after 3 consecutive failures
   * - Shows reconnecting state notification
   * - Resumes polling immediately on return to tab
   * 
   * @param {Function} fn - Async fetch function
   * @param {number} intervalMs - Polling interval in ms
   * @param {Function} onResult - Callback on successful poll
   * @param {Function} onError - Callback on failure
   * @param {Function} onReconnecting - Callback for reconnecting status (true/false)
   * @returns {Object} { stop: Function, triggerNow: Function }
   */
  poll(fn, intervalMs, onResult, onError, onReconnecting) {
    let consecutiveFailures = 0;
    let timer = null;
    let isRunning = true;
    let inFlight = false;

    const executePoll = async () => {
      if (!isRunning || inFlight) return;
      if (typeof document !== 'undefined' && document.hidden) return;

      inFlight = true;
      try {
        const result = await fn();
        if (consecutiveFailures >= 3 && typeof onReconnecting === 'function') {
          onReconnecting(false);
        }
        consecutiveFailures = 0;
        if (typeof onResult === 'function') onResult(result);
      } catch (err) {
        consecutiveFailures++;
        if (consecutiveFailures >= 3 && typeof onReconnecting === 'function') {
          onReconnecting(true);
        }
        if (typeof onError === 'function') onError(err);
      } finally {
        inFlight = false;
        if (isRunning) {
          const nextWait = consecutiveFailures >= 3 ? 15000 : intervalMs;
          timer = setTimeout(executePoll, nextWait);
        }
      }
    };

    // Tab visibility listener
    let onVisibilityChange = null;
    if (typeof document !== 'undefined' && document.addEventListener) {
      onVisibilityChange = () => {
        if (!document.hidden && isRunning) {
          if (timer) clearTimeout(timer);
          executePoll();
        }
      };
      document.addEventListener('visibilitychange', onVisibilityChange);
    }

    // Start initial poll
    executePoll();

    return {
      stop() {
        isRunning = false;
        if (timer) clearTimeout(timer);
        if (onVisibilityChange && typeof document !== 'undefined') {
          document.removeEventListener('visibilitychange', onVisibilityChange);
        }
      },
      triggerNow() {
        if (timer) clearTimeout(timer);
        return executePoll();
      }
    };
  }
};

// Expose globally in browser and Node.js
if (typeof window !== 'undefined') {
  window.SmartOPDApi = SmartOPDApi;
  window.ApiError = ApiError;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = { SmartOPDApi, ApiError };
}
