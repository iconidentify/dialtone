import { useState, useEffect } from 'react';
import { AdminUser, UsersListResponse, Screenname, CreateAdminUserRequest } from '../../types';
import { adminAPI } from '../../services/api';
import LoadingSpinner from '../LoadingSpinner';
import ErrorMessage from '../ErrorMessage';
import Modal from '../Modal';

function UserManagement() {
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeOnly, setActiveOnly] = useState(false);

  // Modal states
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [userToDelete, setUserToDelete] = useState<AdminUser | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [statusTarget, setStatusTarget] = useState<{
    adminUser: AdminUser;
    nextStatus: boolean;
  } | null>(null);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateAdminUserRequest>({
    xUsername: '',
    displayName: '',
    xUserId: '',
    isActive: true,
    grantAdminRole: false,
  });
  const [isCreatingUser, setIsCreatingUser] = useState(false);
  const [resetModalOpen, setResetModalOpen] = useState(false);
  const [resetUser, setResetUser] = useState<AdminUser | null>(null);
  const [userScreennames, setUserScreennames] = useState<Screenname[]>([]);
  const [selectedScreennameId, setSelectedScreennameId] = useState<number | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [isLoadingScreennames, setIsLoadingScreennames] = useState(false);
  const [isResettingPassword, setIsResettingPassword] = useState(false);

  const USERS_PER_PAGE = 20;

  // Load users
  const loadUsers = async (page = 0, activeFilter = activeOnly) => {
    try {
      setIsLoading(true);
      const offset = page * USERS_PER_PAGE;
      const response: UsersListResponse = await adminAPI.listUsers(USERS_PER_PAGE, offset, activeFilter);

      setUsers(response.users);
      setTotalCount(response.totalCount);
      setCurrentPage(page);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to load users');
    } finally {
      setIsLoading(false);
    }
  };

  // Initial load
  useEffect(() => {
    loadUsers();
  }, []);

  // Handle active filter change
  const handleActiveFilterChange = (active: boolean) => {
    setActiveOnly(active);
    loadUsers(0, active);
  };

  // Handle page change
  const handlePageChange = (page: number) => {
    loadUsers(page);
  };

  // Handle user status change
  const handleStatusChange = async (userId: number, active: boolean) => {
    try {
      setIsUpdatingStatus(true);
      await adminAPI.updateUserStatus(userId, active);
      await loadUsers(currentPage);
      setStatusModalOpen(false);
      setStatusTarget(null);
    } catch (err: any) {
      setError(err.message || 'Failed to update user status');
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  const promptStatusChange = (adminUser: AdminUser) => {
    const isActive = adminUser.user.is_active !== false;
    setStatusTarget({
      adminUser,
      nextStatus: !isActive,
    });
    setStatusModalOpen(true);
  };

  const openCreateModal = () => {
    setCreateForm({
      xUsername: '',
      displayName: '',
      xUserId: '',
      isActive: true,
      grantAdminRole: false,
    });
    setCreateModalOpen(true);
  };

  const handleCreateUser = async () => {
    try {
      setIsCreatingUser(true);
      const payload: CreateAdminUserRequest = {
        xUsername: createForm.xUsername?.trim() ? createForm.xUsername.trim() : undefined,
        xUserId: createForm.xUserId?.trim() ? createForm.xUserId.trim() : undefined,
        displayName: createForm.displayName?.trim(),
        isActive: createForm.isActive,
        grantAdminRole: createForm.grantAdminRole,
      };
      await adminAPI.createUser(payload);
      setCreateModalOpen(false);
      await loadUsers(currentPage);
    } catch (err: any) {
      setError(err.message || 'Failed to create user');
    } finally {
      setIsCreatingUser(false);
    }
  };

  const openResetModal = async (adminUser: AdminUser) => {
    setResetUser(adminUser);
    setResetModalOpen(true);
    setUserScreennames([]);
    setSelectedScreennameId(null);
    setNewPassword('');

    try {
      setIsLoadingScreennames(true);
      const response = await adminAPI.getUserScreennames(adminUser.user.id);
      const screennames: Screenname[] = response.screennames || [];
      setUserScreennames(screennames);
      setSelectedScreennameId(screennames[0]?.id ?? null);
    } catch (err: any) {
      setError(err.message || 'Failed to load screennames');
    } finally {
      setIsLoadingScreennames(false);
    }
  };

  const handlePasswordReset = async () => {
    if (!resetUser || !selectedScreennameId) {
      setError('Select a screenname to reset password.');
      return;
    }
    if (!newPassword.trim()) {
      setError('Password is required.');
      return;
    }
    try {
      setIsResettingPassword(true);
      await adminAPI.resetUserScreennamePassword(
        resetUser.user.id,
        selectedScreennameId,
        newPassword.trim()
      );
      setResetModalOpen(false);
      setResetUser(null);
      setNewPassword('');
    } catch (err: any) {
      setError(err.message || 'Failed to reset password');
    } finally {
      setIsResettingPassword(false);
    }
  };

  // Handle user deletion
  const handleDeleteUser = (user: AdminUser) => {
    setUserToDelete(user);
    setDeleteModalOpen(true);
  };

  const confirmDeleteUser = async () => {
    if (!userToDelete) return;

    try {
      setIsDeleting(true);
      await adminAPI.deleteUser(userToDelete.user.id);
      setDeleteModalOpen(false);
      setUserToDelete(null);
      // Reload users
      await loadUsers(currentPage);
    } catch (err: any) {
      setError(err.message || 'Failed to delete user');
    } finally {
      setIsDeleting(false);
    }
  };

  // Calculate pagination
  const totalPages = Math.ceil(totalCount / USERS_PER_PAGE);
  const startIndex = currentPage * USERS_PER_PAGE + 1;
  const endIndex = Math.min((currentPage + 1) * USERS_PER_PAGE, totalCount);

  const renderPagination = () => {
    if (totalPages <= 1) return null;

    return (
      <div className="pagination">
        <button
          disabled={currentPage === 0}
          onClick={() => handlePageChange(currentPage - 1)}
          className="btn-pagination"
        >
          Previous
        </button>

        <span className="pagination-info">
          Page {currentPage + 1} of {totalPages} ({startIndex}-{endIndex} of {totalCount} users)
        </span>

        <button
          disabled={currentPage >= totalPages - 1}
          onClick={() => handlePageChange(currentPage + 1)}
          className="btn-pagination"
        >
          Next
        </button>
      </div>
    );
  };

  // Copy to clipboard helper
  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).then(() => {
      // Could add a toast notification here
    }).catch(console.error);
  };

  // Provider icon components - using dt- prefix to avoid CSS conflicts
  const EmailIcon = () => (
    <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" className="dt-admin-icon dt-admin-icon-email">
      <path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"/>
    </svg>
  );

  const DiscordIcon = () => (
    <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" className="dt-admin-icon dt-admin-icon-discord">
      <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.947 2.418-2.157 2.418z"/>
    </svg>
  );

  const XIcon = () => (
    <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" className="dt-admin-icon dt-admin-icon-x">
      <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
    </svg>
  );

  const CopyIcon = () => (
    <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14" className="dt-copy-icon">
      <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/>
    </svg>
  );

  // Helper to get user display info based on auth provider (using snake_case API fields)
  const getUserDisplayInfo = (user: AdminUser['user']) => {
    const provider = user.auth_provider || 'x';
    if (provider === 'email') {
      return {
        displayName: user.email || 'Email User',
        username: user.email || null,
        provider: 'email' as const,
        copyable: user.email || null
      };
    } else if (provider === 'discord') {
      return {
        displayName: user.discord_display_name || user.discord_username || 'Discord User',
        username: user.discord_username || null,
        provider: 'discord' as const,
        copyable: user.discord_username || null
      };
    } else {
      return {
        displayName: user.x_display_name || user.x_username || 'X User',
        username: user.x_username ? `@${user.x_username}` : null,
        provider: 'x' as const,
        copyable: user.x_username || null
      };
    }
  };

  const renderProviderIcon = (provider: 'email' | 'discord' | 'x') => {
    switch (provider) {
      case 'email': return <EmailIcon />;
      case 'discord': return <DiscordIcon />;
      default: return <XIcon />;
    }
  };

  const renderUserRow = (adminUser: AdminUser) => {
    const user = adminUser.user;
    const isActive = user.is_active !== false;
    const { displayName, username, provider, copyable } = getUserDisplayInfo(user);
    return (
      <tr key={user.id} className={`user-row ${!isActive ? 'user-inactive' : ''}`}>
        <td>{user.id ?? '—'}</td>
        <td>
          <div className="user-info">
            <strong>{displayName}</strong>
            {username && (
              <div className="username-row">
                {renderProviderIcon(provider)}
                <span className="username">{username}</span>
                {copyable && (
                  <button 
                    className="btn-copy" 
                    onClick={() => copyToClipboard(copyable)}
                    title={`Copy ${provider === 'email' ? 'email' : 'username'}`}
                  >
                    <CopyIcon />
                  </button>
                )}
              </div>
            )}
          </div>
        </td>
        <td>{adminUser.screennameCount}</td>
        <td>
          <span className={`status-badge ${adminUser.isAdmin ? 'status-admin' : 'status-user'}`}>
            {adminUser.isAdmin ? 'Admin' : 'User'}
          </span>
        </td>
        <td>
          <span className={`status-badge ${isActive ? 'status-active' : 'status-inactive'}`}>
            {isActive ? 'Active' : 'Inactive'}
          </span>
        </td>
        <td>
          <div className="user-actions">
            <button
              onClick={() => promptStatusChange(adminUser)}
              className={`btn-action ${isActive ? 'btn-disable' : 'btn-enable'}`}
              title={isActive ? 'Disable user' : 'Enable user'}
            >
              {isActive ? 'Disable' : 'Enable'}
            </button>
            <button
              onClick={() => openResetModal(adminUser)}
              className="btn-action"
              title="Reset user screenname password"
            >
              Reset Pwd
            </button>
            <button
              onClick={() => handleDeleteUser(adminUser)}
              className="btn-action btn-delete"
              title="Delete user and all data"
            >
              Delete
            </button>
          </div>
        </td>
      </tr>
    );
  };

  return (
    <div className="user-management">
      <div className="management-header">
        <h2>User Management</h2>
        <div className="management-controls">
          <button onClick={openCreateModal} className="btn-refresh">
            Create User
          </button>
          <label className="filter-control">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(e) => handleActiveFilterChange(e.target.checked)}
            />
            Show active users only
          </label>
          <button onClick={() => loadUsers(currentPage)} className="btn-refresh">
            Refresh
          </button>
        </div>
      </div>

      {error && (
        <ErrorMessage
          message={error}
          onClose={() => setError(null)}
        />
      )}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <div className="users-table-container">
            <table className="users-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>User</th>
                  <th>Screennames</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="no-users">
                      No users found
                    </td>
                  </tr>
                ) : (
                  users.map(renderUserRow)
                )}
              </tbody>
            </table>
          </div>

          {renderPagination()}
        </>
      )}

      {/* Create User Modal */}
      <Modal
        isOpen={createModalOpen}
        onClose={() => !isCreatingUser && setCreateModalOpen(false)}
        title="Create New User"
      >
        <div className="create-user-form">
          <div className="form-group">
            <label className="form-label" htmlFor="new-x-username">X Username</label>
            <input
              id="new-x-username"
              className="form-input"
              value={createForm.xUsername}
              onChange={(e) => setCreateForm(prev => ({ ...prev, xUsername: e.target.value }))}
              placeholder="Optional (auto-generated if blank)"
              disabled={isCreatingUser}
            />
            <p className="form-help">Leave blank to auto-generate a technical username.</p>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="new-display-name">Display Name</label>
            <input
              id="new-display-name"
              className="form-input"
              value={createForm.displayName}
              onChange={(e) => setCreateForm(prev => ({ ...prev, displayName: e.target.value }))}
              placeholder="Optional"
              disabled={isCreatingUser}
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="new-x-user-id">X User ID (optional)</label>
            <input
              id="new-x-user-id"
              className="form-input"
              value={createForm.xUserId}
              onChange={(e) => setCreateForm(prev => ({ ...prev, xUserId: e.target.value }))}
              placeholder="If empty, one will be generated"
              disabled={isCreatingUser}
            />
          </div>
          <div className="form-group checkbox-group">
            <label>
              <input
                type="checkbox"
                checked={createForm.isActive ?? true}
                onChange={(e) => setCreateForm(prev => ({ ...prev, isActive: e.target.checked }))}
                disabled={isCreatingUser}
              />
              Active
            </label>
          </div>
          <div className="form-group checkbox-group">
            <label>
              <input
                type="checkbox"
                checked={createForm.grantAdminRole ?? false}
                onChange={(e) => setCreateForm(prev => ({ ...prev, grantAdminRole: e.target.checked }))}
                disabled={isCreatingUser}
              />
              Grant admin role
            </label>
          </div>
          <div className="modal-actions">
            <button
              onClick={() => setCreateModalOpen(false)}
              disabled={isCreatingUser}
              className="btn-cancel"
            >
              Cancel
            </button>
            <button
              onClick={handleCreateUser}
              disabled={isCreatingUser}
              className="btn-cleanup"
            >
              {isCreatingUser ? 'Creating...' : 'Create User'}
            </button>
          </div>
        </div>
      </Modal>

      {/* Reset Password Modal */}
      <Modal
        isOpen={resetModalOpen}
        onClose={() => !isResettingPassword && setResetModalOpen(false)}
        title="Reset Screenname Password"
      >
        {isLoadingScreennames ? (
          <LoadingSpinner />
        ) : userScreennames.length === 0 ? (
          <p>No screennames available for this user.</p>
        ) : (
          <div className="reset-password-form">
            <div className="form-group">
              <label className="form-label" htmlFor="screenname-select">Screenname</label>
              <select
                id="screenname-select"
                className="form-input"
                value={selectedScreennameId ?? ''}
                onChange={(e) => setSelectedScreennameId(Number(e.target.value))}
                disabled={isResettingPassword}
              >
                {userScreennames.map((sn) => (
                  <option key={sn.id} value={sn.id}>
                    {sn.screenname}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="new-password">New Password (max 8 chars)</label>
              <input
                id="new-password"
                className="form-input"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                maxLength={8}
                disabled={isResettingPassword}
              />
            </div>
            <p className="warning-text">
              Passwords must be 1-8 characters to remain compatible with the messaging protocol.
            </p>
            <div className="modal-actions">
              <button
                onClick={() => setResetModalOpen(false)}
                disabled={isResettingPassword}
                className="btn-cancel"
              >
                Cancel
              </button>
              <button
                onClick={handlePasswordReset}
                disabled={isResettingPassword}
                className="btn-cleanup"
              >
                {isResettingPassword ? 'Resetting...' : 'Reset Password'}
              </button>
            </div>
          </div>
        )}
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={deleteModalOpen}
        onClose={() => !isDeleting && setDeleteModalOpen(false)}
        title="Confirm User Deletion"
      >
        {userToDelete && (
          <div className="delete-confirmation">
            <p>
              Are you sure you want to delete user{' '}
              <strong>{getUserDisplayInfo(userToDelete.user).displayName}</strong>?
            </p>
            <p className="warning-text">
              This will permanently delete:
            </p>
            <ul>
              <li>The user account</li>
              <li>All {userToDelete.screennameCount} associated screennames</li>
              <li>All user data and history</li>
            </ul>
            <p className="warning-text">
              <strong>This action cannot be undone!</strong>
            </p>

            <div className="modal-actions">
              <button
                onClick={() => setDeleteModalOpen(false)}
                disabled={isDeleting}
                className="btn-cancel"
              >
                Cancel
              </button>
              <button
                onClick={confirmDeleteUser}
                disabled={isDeleting}
                className="btn-delete"
              >
                {isDeleting ? 'Deleting...' : 'Delete User'}
              </button>
            </div>
          </div>
        )}
      </Modal>

      {/* Status Confirmation Modal */}
      <Modal
        isOpen={statusModalOpen}
        onClose={() => !isUpdatingStatus && setStatusModalOpen(false)}
        title="Confirm Status Change"
      >
        {statusTarget && (
          <div className="status-confirmation">
            <p>
              Are you sure you want to{' '}
              <strong>{statusTarget.nextStatus ? 'enable' : 'disable'}</strong> user{' '}
              <strong>{getUserDisplayInfo(statusTarget.adminUser.user).displayName}</strong>?
            </p>
            <p className="warning-text">
              {statusTarget.nextStatus
                ? 'The user will regain access to Dialtone.'
                : 'The user will immediately lose access to Dialtone.'}
            </p>
            <div className="modal-actions">
              <button
                onClick={() => setStatusModalOpen(false)}
                disabled={isUpdatingStatus}
                className="btn-cancel"
              >
                Cancel
              </button>
              <button
                onClick={() =>
                  handleStatusChange(statusTarget.adminUser.user.id, statusTarget.nextStatus)
                }
                disabled={isUpdatingStatus}
                className={statusTarget.nextStatus ? 'btn-enable' : 'btn-disable'}
              >
                {isUpdatingStatus ? 'Saving...' : statusTarget.nextStatus ? 'Enable User' : 'Disable User'}
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

export default UserManagement;