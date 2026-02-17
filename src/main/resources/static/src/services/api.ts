import axios, { AxiosResponse, AxiosError } from 'axios';
import {
  User,
  Screenname,
  AdminUser,
  CreateScreennameRequest,
  UpdateScreennameRequest,
  UpdatePasswordRequest,
  DeleteResponse,
  LogoutResponse,
  HealthResponse,
  ApiError,
  AuthProvidersResponse,
  MagicLinkSentResponse,
  // Admin types
  UsersListResponse,
  UserDetailResponse,
  ScreennamesListResponse,
  AuditLogResponse,
  AuditStatsResponse,
  SystemStatsResponse,
  SystemHealthResponse,
  UpdateStatusRequest,
  AuditFilters,
  CreateAdminUserRequest,
  // FDO Workbench types
  ConnectedScreennamesResponse,
  SendFdoRequest,
  SendFdoResponse,
  // File Transfer types
  TransferConnectedScreennamesResponse,
  TransferConfig,
  TransferResponse,
  // Screenname Preferences types
  ScreennamePreferences,
  UpdatePreferencesRequest,
} from '../types';

const AUTH_COOKIE_NAME = 'dialtone_auth_token';

// Configure axios instance
const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

const CSRF_HEADER = 'X-CSRF-Token';

// Token management
export const setAuthToken = (token: string | null) => {
  if (token) {
    api.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    localStorage.setItem('dialtone_auth_token', token);
  } else {
    delete api.defaults.headers.common['Authorization'];
    localStorage.removeItem('dialtone_auth_token');
  }
};

// Initialize token from localStorage or cookie fallback
export const initializeAuth = () => {
  const savedToken = localStorage.getItem('dialtone_auth_token');
  if (savedToken) {
    setAuthToken(savedToken);
    return;
  }

  const cookieToken = getAuthCookieToken();
  if (cookieToken) {
    setAuthToken(cookieToken);
  }
};

export const getAuthCookieToken = (): string | null => {
  if (typeof document === 'undefined') {
    return null;
  }

  const cookiePair = document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${AUTH_COOKIE_NAME}=`));

  if (!cookiePair) {
    return null;
  }

  const value = cookiePair.split('=')[1];
  return value ? decodeURIComponent(value) : null;
};

// Error handling utility
const handleApiError = (error: AxiosError): ApiError => {
  if (error.response?.data) {
    // Backend returned an error response
    return error.response.data as ApiError;
  } else if (error.request) {
    // Network error
    return {
      error: 'Network Error',
      message: 'Unable to connect to the server. Please check your internet connection.',
    };
  } else {
    // Request setup error
    return {
      error: 'Request Error',
      message: error.message || 'An unexpected error occurred',
    };
  }
};

// Helper to map API screenname response to frontend format
const mapScreenname = (apiScreenname: any): Screenname => ({
  id: apiScreenname.id,
  screenname: apiScreenname.screenname,
  isPrimary: apiScreenname.is_primary || apiScreenname.isPrimary || false,
  createdAt: apiScreenname.created_at || apiScreenname.createdAt || '',
});

// Helper to map user response with screennames
const mapUserResponse = (apiUser: any): User => {
  const rawScreennames =
    apiUser.screennames ??
    apiUser.screen_names ??
    apiUser.screennames_list ??
    [];

  const normalizedScreennames = Array.isArray(rawScreennames)
    ? rawScreennames.map(mapScreenname)
    : [];

  // Determine auth provider
  const authProvider = apiUser.authProvider ?? apiUser.auth_provider ?? 'x';
  
  // Get provider-specific username
  const providerUsername = 
    apiUser.providerUsername ??
    apiUser.provider_username ??
    (authProvider === 'discord' 
      ? (apiUser.discordUsername ?? apiUser.discord_username)
      : (apiUser.xUsername ?? apiUser.x_username ?? apiUser.username)) ??
    '';

  return {
    userId: apiUser.userId ?? apiUser.id ?? apiUser.user_id ?? 0,
    authProvider,
    providerUsername,
    xUsername:
      apiUser.xUsername ??
      apiUser.x_username ??
      (authProvider === 'x' ? apiUser.username : undefined),
    discordUsername:
      apiUser.discordUsername ??
      apiUser.discord_username ??
      (authProvider === 'discord' ? apiUser.username : undefined),
    displayName:
      apiUser.displayName ??
      apiUser.xDisplayName ??
      apiUser.x_display_name ??
      apiUser.discordDisplayName ??
      apiUser.discord_display_name ??
      apiUser.display_name ??
      apiUser.name ??
      '',
    screennames: normalizedScreennames,
    isAdmin: Boolean(apiUser.isAdmin ?? apiUser.is_admin),
    isActive: apiUser.isActive ?? apiUser.is_active ?? true,
  };
};

const mapAdminUserResponse = (entry: any): AdminUser => ({
  // Keep user data as-is (ApiUser format with snake_case) for admin components
  user: entry.user ?? entry,
  screennameCount: entry.screennameCount ?? entry.screenname_count ?? 0,
  isAdmin: Boolean(entry.isAdmin ?? entry.is_admin),
});

// Authentication API
export const authAPI = {
  /**
   * Initiate X OAuth login by redirecting to authorization URL
   */
  async initiateXLogin(): Promise<void> {
    window.location.href = '/api/auth/x/login';
  },

  /**
   * Initiate Discord OAuth login by redirecting to authorization URL
   */
  async initiateDiscordLogin(): Promise<void> {
    window.location.href = '/api/auth/discord/login';
  },

  /**
   * Get available auth providers
   */
  async getAuthProviders(): Promise<AuthProvidersResponse> {
    try {
      const response: AxiosResponse<AuthProvidersResponse> = await api.get('/auth/providers');
      return response.data;
    } catch (error) {
      // Default to X only if endpoint fails (backward compatibility)
      return { xEnabled: true, discordEnabled: false, emailEnabled: false };
    }
  },

  /**
   * Initiate email magic link login
   */
  async initiateEmailLogin(email: string): Promise<MagicLinkSentResponse> {
    try {
      const response: AxiosResponse<MagicLinkSentResponse> = await api.post('/auth/email/login', { email });
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Get current authenticated user information
   */
  async getCurrentUser(): Promise<User> {
    try {
      const response: AxiosResponse<any> = await api.get('/auth/me');
      return mapUserResponse(response.data);
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Logout current user
   */
  async logout(): Promise<LogoutResponse> {
    try {
      const response: AxiosResponse<LogoutResponse> = await api.post('/auth/logout');
      setAuthToken(null);
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },
};

// CSRF helpers
const fetchCsrfToken = async (): Promise<string> => {
  try {
    const response: AxiosResponse<{ token: string }> = await api.get('/csrf-token', {
      headers: { 'Cache-Control': 'no-cache' },
    });
    return response.data.token;
  } catch (error) {
    throw handleApiError(error as AxiosError);
  }
};

const withCsrf = async <T>(request: (token: string) => Promise<T>): Promise<T> => {
  const token = await fetchCsrfToken();
  return request(token);
};

// Screenname management API
export const screennameAPI = {
  /**
   * Get all screennames for the authenticated user
   */
  async getScreennames(): Promise<Screenname[]> {
    try {
      const response: AxiosResponse<any> = await api.get('/screennames');
      const screennames = response.data.screennames || [];
      return screennames.map(mapScreenname);
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Create a new screenname
   */
  async createScreenname(data: CreateScreennameRequest): Promise<Screenname> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.post('/screennames', data, {
          headers: { [CSRF_HEADER]: token },
        });
        return mapScreenname(response.data);
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Update an existing screenname (change the screenname text)
   */
  async updateScreenname(id: number, data: UpdateScreennameRequest): Promise<Screenname> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.put(`/screennames/${id}`, data, {
          headers: { [CSRF_HEADER]: token },
        });
        return mapScreenname(response.data);
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Update password for a screenname
   */
  async updatePassword(id: number, data: UpdatePasswordRequest): Promise<Screenname> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.put(`/screennames/${id}/password`, data, {
          headers: { [CSRF_HEADER]: token },
        });
        return mapScreenname(response.data);
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Set a screenname as primary
   */
  async setPrimary(id: number): Promise<Screenname> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.put(`/screennames/${id}/primary`, null, {
          headers: { [CSRF_HEADER]: token },
        });
        return mapScreenname(response.data);
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Delete a screenname
   */
  async deleteScreenname(id: number): Promise<DeleteResponse> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<DeleteResponse> = await api.delete(`/screennames/${id}`, {
          headers: { [CSRF_HEADER]: token },
        });
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },
};

// Helper to map preferences API response (snake_case) to frontend format (camelCase)
const mapPreferencesResponse = (apiPrefs: any): ScreennamePreferences => ({
  screennameId: apiPrefs.screennameId ?? apiPrefs.screenname_id ?? 0,
  lowColorMode: apiPrefs.lowColorMode ?? apiPrefs.low_color_mode ?? false,
});

// Screenname Preferences API
export const preferencesAPI = {
  /**
   * Get preferences for a screenname
   * Returns default preferences if none exist
   */
  async getPreferences(screennameId: number): Promise<ScreennamePreferences> {
    try {
      const response: AxiosResponse<any> = await api.get(
        `/screennames/${screennameId}/preferences`
      );
      return mapPreferencesResponse(response.data);
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Update preferences for a screenname
   */
  async updatePreferences(
    screennameId: number,
    data: UpdatePreferencesRequest
  ): Promise<ScreennamePreferences> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.put(
          `/screennames/${screennameId}/preferences`,
          data,
          {
            headers: { [CSRF_HEADER]: token },
          }
        );
        return mapPreferencesResponse(response.data);
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },
};

// Admin API
export const adminAPI = {
  /**
   * List all users with pagination and admin status
   */
  async listUsers(limit = 20, offset = 0, activeOnly = false): Promise<UsersListResponse> {
    try {
      const params = new URLSearchParams({
        limit: limit.toString(),
        offset: offset.toString(),
        active_only: activeOnly.toString(),
      });
      const response: AxiosResponse<any> = await api.get(`/admin/users?${params}`);
      const data = response.data;

      return {
        users: Array.isArray(data.users) ? data.users.map(mapAdminUserResponse) : [],
        totalCount: data.totalCount ?? 0,
        limit: data.limit ?? limit,
        offset: data.offset ?? offset,
      };
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Get detailed information about a specific user
   */
  async getUserDetails(userId: number): Promise<UserDetailResponse> {
    try {
      const response: AxiosResponse<any> = await api.get(`/admin/users/${userId}`);
      const data = response.data;

      return {
        user: mapUserResponse(data.user),
        screennames: Array.isArray(data.screennames)
          ? data.screennames.map(mapScreenname)
          : [],
        isAdmin: Boolean(data.isAdmin ?? data.is_admin),
      };
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Update user status (enable/disable)
   */
  async updateUserStatus(userId: number, active: boolean): Promise<any> {
    return withCsrf(async (token) => {
      try {
        const data: UpdateStatusRequest = { active };
        const response: AxiosResponse<any> = await api.put(`/admin/users/${userId}/status`, data, {
          headers: { [CSRF_HEADER]: token },
        });
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Delete a user and all associated data
   */
  async deleteUser(userId: number): Promise<DeleteResponse> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<DeleteResponse> = await api.delete(`/admin/users/${userId}`, {
          headers: { [CSRF_HEADER]: token },
        });
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Get all screennames for a specific user
   */
  async getUserScreennames(userId: number): Promise<any> {
    try {
      const response: AxiosResponse<any> = await api.get(`/admin/users/${userId}/screennames`);
      const data = response.data;

      return {
        user: mapUserResponse(data.user),
        screennames: Array.isArray(data.screennames)
          ? data.screennames.map(mapScreenname)
          : [],
      };
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * List all screennames with user information
   */
  async listScreennames(limit = 50, offset = 0, userId?: number): Promise<ScreennamesListResponse> {
    try {
      const params = new URLSearchParams({
        limit: limit.toString(),
        offset: offset.toString(),
      });
      if (userId) {
        params.append('user_id', userId.toString());
      }
      const response: AxiosResponse<any> = await api.get(`/admin/screennames?${params}`);
      const data = response.data;

      return {
        screennames: Array.isArray(data.screennames)
          ? data.screennames.map((sn: any) => ({
              id: sn.id,
              screenname: sn.screenname,
              isPrimary: sn.isPrimary ?? sn.is_primary ?? false,
              createdAt: sn.createdAt ?? sn.created_at ?? '',
              userId: sn.userId ?? sn.user_id ?? 0,
              userXUsername: sn.userXUsername ?? sn.user_x_username ?? sn.x_username ?? '',
              userXDisplayName:
                sn.userXDisplayName ?? sn.user_x_display_name ?? sn.x_display_name ?? '',
              userIsActive: sn.userIsActive ?? sn.user_is_active ?? true,
            }))
          : [],
        totalCount: data.totalCount ?? 0,
        limit: data.limit ?? limit,
        offset: data.offset ?? offset,
      };
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Create a new user (admin action)
   */
  async createUser(data: CreateAdminUserRequest): Promise<AdminUser> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.post('/admin/users', data, {
          headers: { [CSRF_HEADER]: token },
        });
        const payload = response.data?.user ?? response.data;
        return mapAdminUserResponse(payload);
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Reset password for a user's screenname
   */
  async resetUserScreennamePassword(userId: number, screennameId: number, password: string): Promise<any> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.put(
          `/admin/users/${userId}/screennames/${screennameId}/password`,
          { password },
          {
            headers: { [CSRF_HEADER]: token },
          }
        );
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Create a screenname for a specific user
   */
  async createUserScreenname(userId: number, data: CreateScreennameRequest): Promise<Screenname> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.post(
          `/admin/users/${userId}/screennames`,
          data,
          {
            headers: { [CSRF_HEADER]: token },
          }
        );
        const payload = response.data?.screenname ?? response.data;
        return mapScreenname(payload);
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Delete a specific screenname
   */
  async deleteScreenname(screennameId: number): Promise<DeleteResponse> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<DeleteResponse> = await api.delete(`/admin/screennames/${screennameId}`, {
          headers: { [CSRF_HEADER]: token },
        });
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Reset password for a screenname (admin endpoint)
   */
  async resetAdminScreennamePassword(screennameId: number, password: string): Promise<any> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.put(
          `/admin/screennames/${screennameId}/password`,
          { password },
          {
            headers: { [CSRF_HEADER]: token },
          }
        );
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Get audit log entries with filtering
   */
  async getAuditLog(
    limit = 50,
    offset = 0,
    filters?: AuditFilters
  ): Promise<AuditLogResponse> {
    try {
      const params = new URLSearchParams({
        limit: limit.toString(),
        offset: offset.toString(),
      });
      if (filters?.adminUserId) {
        params.append('admin_user_id', filters.adminUserId.toString());
      }
      if (filters?.action) {
        params.append('action', filters.action);
      }
      const response: AxiosResponse<AuditLogResponse> = await api.get(`/admin/audit?${params}`);
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Get audit log statistics
   */
  async getAuditStats(): Promise<AuditStatsResponse> {
    try {
      const response: AxiosResponse<AuditStatsResponse> = await api.get('/admin/audit/stats');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Trigger manual audit cleanup
   */
  async triggerAuditCleanup(): Promise<any> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.post('/admin/audit/cleanup', null, {
          headers: { [CSRF_HEADER]: token },
        });
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Get recent admin activity
   */
  async getRecentActivity(): Promise<AuditLogResponse> {
    try {
      const response: AxiosResponse<AuditLogResponse> = await api.get('/admin/audit/recent');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Get system statistics
   */
  async getSystemStats(): Promise<SystemStatsResponse> {
    try {
      const response: AxiosResponse<SystemStatsResponse> = await api.get('/admin/system/stats');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Get system health information
   */
  async getSystemHealth(): Promise<SystemHealthResponse> {
    try {
      const response: AxiosResponse<SystemHealthResponse> = await api.get('/admin/system/health');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Get AOL server operational metrics
   */
  async getAolMetrics(): Promise<any> {
    try {
      const response: AxiosResponse<any> = await api.get('/admin/aol/metrics');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Grant admin role to a user
   */
  async grantAdminRole(userId: number): Promise<any> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.post(`/admin/roles/${userId}/grant`, null, {
          headers: { [CSRF_HEADER]: token },
        });
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Revoke admin role from a user
   */
  async revokeAdminRole(userId: number): Promise<any> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<any> = await api.delete(`/admin/roles/${userId}`, {
          headers: { [CSRF_HEADER]: token },
        });
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },

  /**
   * Get list of currently connected screennames
   */
  async getConnectedScreennames(): Promise<ConnectedScreennamesResponse> {
    try {
      const response: AxiosResponse<ConnectedScreennamesResponse> = await api.get('/admin/fdo/connected-screennames');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Send FDO script to a connected client
   */
  async sendFdo(request: SendFdoRequest): Promise<SendFdoResponse> {
    return withCsrf(async (token) => {
      try {
        const response: AxiosResponse<SendFdoResponse> = await api.post('/admin/fdo/send', request, {
          headers: { [CSRF_HEADER]: token },
        });
        return response.data;
      } catch (error) {
        throw handleApiError(error as AxiosError);
      }
    });
  },
};

// Health check API
export const healthAPI = {
  /**
   * Check application health status
   */
  async getHealth(): Promise<HealthResponse> {
    try {
      const response: AxiosResponse<HealthResponse> = await api.get('/health');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },
};

// File Transfer API
export const transferAPI = {
  /**
   * Get transfer configuration (max file size, etc.)
   */
  async getConfig(): Promise<TransferConfig> {
    try {
      const response: AxiosResponse<TransferConfig> = await api.get('/transfer/config');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Get list of user's screennames that are currently connected
   */
  async getConnectedScreennames(): Promise<TransferConnectedScreennamesResponse> {
    try {
      const response: AxiosResponse<TransferConnectedScreennamesResponse> = await api.get('/transfer/connected-screennames');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  /**
   * Upload a file and initiate transfer to a connected screenname
   * Uses XMLHttpRequest for progress tracking
   */
  async uploadFile(
    file: File,
    screenname: string,
    onProgress?: (percent: number) => void
  ): Promise<TransferResponse> {
    // First get CSRF token
    const csrfToken = await fetchCsrfToken();

    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      const formData = new FormData();
      formData.append('file', file);
      formData.append('screenname', screenname);

      // Track upload progress
      xhr.upload.addEventListener('progress', (event) => {
        if (event.lengthComputable && onProgress) {
          const percent = Math.round((event.loaded / event.total) * 100);
          onProgress(percent);
        }
      });

      xhr.addEventListener('load', () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            const response = JSON.parse(xhr.responseText) as TransferResponse;
            resolve(response);
          } catch {
            reject({ error: 'Parse Error', message: 'Failed to parse server response' });
          }
        } else {
          try {
            const errorResponse = JSON.parse(xhr.responseText);
            reject(errorResponse);
          } catch {
            reject({ error: 'Upload Error', message: `Upload failed with status ${xhr.status}` });
          }
        }
      });

      xhr.addEventListener('error', () => {
        reject({ error: 'Network Error', message: 'Failed to connect to server' });
      });

      xhr.addEventListener('abort', () => {
        reject({ error: 'Aborted', message: 'Upload was cancelled' });
      });

      xhr.open('POST', '/api/transfer/upload');

      // Set authorization header
      const token = localStorage.getItem('dialtone_auth_token');
      if (token) {
        xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      }

      // Set CSRF token
      xhr.setRequestHeader('X-CSRF-Token', csrfToken);

      xhr.send(formData);
    });
  },
};

// Response interceptor for global error handling
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    // Handle 401 errors globally by clearing auth and redirecting to login
    if (error.response?.status === 401) {
      setAuthToken(null);
      // Don't redirect if we're already on a login page or handling auth callback
      if (!window.location.pathname.includes('/auth/') &&
          !window.location.pathname.includes('/callback') &&
          window.location.pathname !== '/') {
        window.location.href = '/';
      }
    }
    return Promise.reject(error);
  }
);

export default api;