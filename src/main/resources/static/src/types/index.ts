// Auth provider type
export type AuthProvider = 'x' | 'discord' | 'email';

// User and Authentication Types
export interface User {
  // Frontend uses camelCase, but API returns snake_case
  // These are the camelCase versions used internally
  userId: number;
  authProvider?: AuthProvider;
  providerUsername?: string;
  displayName: string;
  xUsername?: string;
  xDisplayName?: string;
  discordUsername?: string;
  discordDisplayName?: string;
  email?: string;
  screennames: Screenname[];
  isAdmin?: boolean;
  isActive?: boolean;
}

// API response format (snake_case from backend)
export interface ApiUser {
  id: number;
  auth_provider?: AuthProvider;
  x_user_id?: string;
  x_username?: string;
  x_display_name?: string;
  discord_user_id?: string;
  discord_username?: string;
  discord_display_name?: string;
  email?: string;
  created_at?: string;
  is_active?: boolean;
}

export interface LoginResponse {
  userId: number;
  authProvider?: AuthProvider;
  providerUsername?: string;
  displayName: string;
  token: string;
  screennames: Screenname[];
}

export interface AuthProvidersResponse {
  xEnabled: boolean;
  discordEnabled: boolean;
  emailEnabled: boolean;
}

export interface EmailLoginRequest {
  email: string;
}

export interface MagicLinkSentResponse {
  message: string;
  success: boolean;
}

// Screenname Types
export interface Screenname {
  id: number;
  screenname: string;
  isPrimary: boolean;
  createdAt: string;
}

// Screenname Preferences Types
export interface ScreennamePreferences {
  screennameId: number;
  lowColorMode: boolean;
}

export interface UpdatePreferencesRequest {
  lowColorMode: boolean;
}

export interface CreateScreennameRequest {
  screenname: string;
  password: string;
}

export interface UpdateScreennameRequest {
  screenname: string;
}

export interface UpdatePasswordRequest {
  password: string;
}

// API Response Types
export interface ApiError {
  error: string;
  message: string;
}

export interface HealthResponse {
  status: string;
  timestamp: number;
  dbStats: string;
}

export interface ScreennamesResponse {
  screennames: Screenname[];
}

export interface DeleteResponse {
  message: string;
}

export interface LogoutResponse {
  message: string;
}

// Form Types
export interface ScreennameFormData {
  screenname: string;
  password: string;
}

export interface UpdateScreennameFormData {
  screenname: string;
}

export interface UpdatePasswordFormData {
  password: string;
  confirmPassword: string;
}

// UI State Types
export interface AppState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

export interface ScreennameState {
  screennames: Screenname[];
  isLoading: boolean;
  error: string | null;
}

// Component Props Types
export interface ScreennameItemProps {
  screenname: Screenname;
  onEdit: (screenname: Screenname) => void;
  onChangePassword: (screenname: Screenname) => void;
  onSetPrimary: (screenname: Screenname) => void;
  onDelete: (screenname: Screenname) => void;
}

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}

export interface CreateScreennameModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: ScreennameFormData) => void;
  isLoading: boolean;
}

export interface EditScreennameModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: UpdateScreennameFormData) => void;
  screenname: Screenname | null;
  isLoading: boolean;
}

export interface UpdatePasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: UpdatePasswordFormData) => void;
  screenname: Screenname | null;
  isLoading: boolean;
}

// Validation Types
export interface ValidationResult {
  isValid: boolean;
  errors: Record<string, string>;
}

// Constants
export const SCREENNAME_CONSTRAINTS = {
  MIN_LENGTH: 1,
  MAX_LENGTH: 10,
  PATTERN: /^[a-zA-Z0-9]+$/,
  /** Reserved prefix for ephemeral guest accounts - normal users cannot use this */
  RESERVED_PREFIX: '~',
} as const;

export const PASSWORD_CONSTRAINTS = {
  MIN_LENGTH: 1,
  MAX_LENGTH: 8,
} as const;

export const MAX_SCREENNAMES_PER_USER = 2;
export const MAX_SCREENNAMES_PER_ADMIN = 20;

// Helper function to get max screennames based on user role
export const getMaxScreennamesForUser = (user: User | null): number => {
  if (!user) return MAX_SCREENNAMES_PER_USER;
  return user.isAdmin ? MAX_SCREENNAMES_PER_ADMIN : MAX_SCREENNAMES_PER_USER;
};

// Admin-related Types
export interface AdminUser {
  user: ApiUser;
  screennameCount: number;
  isAdmin: boolean;
}

export interface UsersListResponse {
  users: AdminUser[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface UserDetailResponse {
  user: User;
  screennames: Screenname[];
  isAdmin: boolean;
}

export interface ScreennameWithUser {
  id: number;
  screenname: string;
  isPrimary: boolean;
  createdAt: string;
  userId: number;
  userAuthProvider: AuthProvider;
  userXUsername?: string;
  userXDisplayName?: string;
  userDiscordUsername?: string;
  userDiscordDisplayName?: string;
  userEmail?: string;
  userIsActive: boolean;
}

export interface ScreennamesListResponse {
  screennames: ScreennameWithUser[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface AuditLogEntry {
  id: number;
  adminUserId: number;
  adminUsername: string;
  action: string;
  targetUserId?: number;
  targetUsername?: string;
  targetScreennameId?: number;
  targetScreenname?: string;
  details?: string;
  ipAddress?: string;
  userAgent?: string;
  createdAt: string;
}

export interface AuditLogResponse {
  entries: AuditLogEntry[];
  totalCount: number;
  limit: number;
  offset: number;
}

export interface AuditStatsResponse {
  statistics: Record<string, any>;
}

export interface SystemStats {
  totalUsers: number;
  activeUsers: number;
  inactiveUsers: number;
  totalScreennames: number;
  configuredAdminCount: number;
  configuredAdmins: string[];
  adminEnabled: boolean;
  adminSessionTimeout: number;
  adminMaxScreennames: number;
  auditLogStats: Record<string, any>;
  auditLogSize: number;
  systemHealth: string;
  timestamp: number;
}

export interface SystemStatsResponse {
  statistics: SystemStats;
}

export interface SystemHealth {
  database: {
    status: string;
    lastChecked: number;
    error?: string;
  };
  adminServices: {
    adminEnabled: boolean;
    auditLogSize: number;
  };
  systemResources: {
    maxMemoryMB: number;
    totalMemoryMB: number;
    usedMemoryMB: number;
    freeMemoryMB: number;
    memoryUsagePercent: number;
  };
  overallStatus: string;
  timestamp: number;
}

export interface SystemHealthResponse {
  health: SystemHealth;
}

export interface UpdateStatusRequest {
  active: boolean;
}

export interface ResetPasswordRequest {
  password: string;
}

export interface CreateAdminUserRequest {
  xUsername?: string;
  displayName?: string;
  xUserId?: string;
  screenname?: string;
  screennamePassword?: string;
  isActive?: boolean;
  grantAdminRole?: boolean;
}

// Admin UI State Types
export interface AdminState {
  isAdmin: boolean;
  currentSection: 'dashboard' | 'users' | 'audit' | 'system';
  usersData: {
    users: AdminUser[];
    totalCount: number;
    currentPage: number;
    isLoading: boolean;
  };
  auditData: {
    entries: AuditLogEntry[];
    totalCount: number;
    currentPage: number;
    isLoading: boolean;
  };
  systemStats: SystemStats | null;
  systemHealth: SystemHealth | null;
}

// Admin Component Props
export interface AdminDashboardProps {
  user: User;
  onLogout: () => void;
  onSectionChange: (section: string) => void;
}

export interface UserManagementProps {
  users: AdminUser[];
  totalCount: number;
  currentPage: number;
  onPageChange: (page: number) => void;
  onUserStatusChange: (userId: number, active: boolean) => void;
  onUserDelete: (userId: number) => void;
  isLoading: boolean;
}

export interface AuditLogViewerProps {
  entries: AuditLogEntry[];
  totalCount: number;
  currentPage: number;
  onPageChange: (page: number) => void;
  onFilterChange: (filters: AuditFilters) => void;
  isLoading: boolean;
}

export interface AuditFilters {
  adminUserId?: number;
  action?: string;
  dateFrom?: string;
  dateTo?: string;
}

export interface SystemHealthPanelProps {
  stats: SystemStats | null;
  health: SystemHealth | null;
  onRefresh: () => void;
  isLoading: boolean;
}

// FDO Workbench Types
export interface ConnectedScreennamesResponse {
  screennames: string[];
  count: number;
}

export interface SendFdoRequest {
  screenname: string;
  fdoScript: string;
  token?: string;
  streamId?: number;
}

export interface FdoCompilationStats {
  chunkCount: number;
  totalBytes: number;
  compilationTimeMs: number;
}

export interface SendFdoResponse {
  success: boolean;
  screenname: string;
  compilationStats: FdoCompilationStats;
  message: string;
}

// File Transfer Types
export interface ConnectedScreennameInfo {
  screenname: string;
  platform: 'mac' | 'windows' | 'unknown';
  isOnline: boolean;
}

export interface TransferConnectedScreennamesResponse {
  screennames: ConnectedScreennameInfo[];
  count: number;
}

export interface TransferConfig {
  maxFileSizeMb: number;
}

export interface TransferResponse {
  success: boolean;
  transferId: string;
  message: string;
  filename: string;
  fileSize: number;
  screenname: string;
}

export interface TransferState {
  status: 'idle' | 'uploading' | 'transferring' | 'success' | 'error';
  progress: number;
  error: string | null;
  transferId: string | null;
}