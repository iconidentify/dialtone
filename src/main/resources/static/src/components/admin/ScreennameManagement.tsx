import { useState, useEffect } from 'react';
import { ScreennameWithUser, ScreennamesListResponse } from '../../types';
import { adminAPI } from '../../services/api';
import LoadingSpinner from '../LoadingSpinner';
import ErrorMessage from '../ErrorMessage';
import Modal from '../Modal';

function ScreennameManagement() {
  const [screennames, setScreennames] = useState<ScreennameWithUser[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Reset password modal
  const [resetModalOpen, setResetModalOpen] = useState(false);
  const [resetTarget, setResetTarget] = useState<ScreennameWithUser | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [isResetting, setIsResetting] = useState(false);

  // Delete modal
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<ScreennameWithUser | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const ITEMS_PER_PAGE = 25;

  // Load screennames
  const loadScreennames = async (page = 0) => {
    try {
      setIsLoading(true);
      const offset = page * ITEMS_PER_PAGE;
      const response: ScreennamesListResponse = await adminAPI.listScreennames(ITEMS_PER_PAGE, offset);
      setScreennames(response.screennames);
      setTotalCount(response.totalCount);
      setCurrentPage(page);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to load screennames');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadScreennames();
  }, []);

  const handlePageChange = (page: number) => {
    loadScreennames(page);
  };

  // Copy to clipboard
  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setSuccess(`Copied ${label} to clipboard`);
      setTimeout(() => setSuccess(null), 2000);
    }).catch(console.error);
  };

  // Open reset password modal
  const openResetModal = (screenname: ScreennameWithUser) => {
    setResetTarget(screenname);
    setNewPassword('');
    setResetModalOpen(true);
  };

  // Handle password reset
  const handlePasswordReset = async () => {
    if (!resetTarget) return;
    if (!newPassword.trim()) {
      setError('Password is required');
      return;
    }

    try {
      setIsResetting(true);
      await adminAPI.resetAdminScreennamePassword(resetTarget.id, newPassword.trim());
      setSuccess(`Password reset for ${resetTarget.screenname}`);
      setResetModalOpen(false);
      setResetTarget(null);
      setNewPassword('');
    } catch (err: any) {
      setError(err.message || 'Failed to reset password');
    } finally {
      setIsResetting(false);
    }
  };

  // Open delete modal
  const openDeleteModal = (screenname: ScreennameWithUser) => {
    setDeleteTarget(screenname);
    setDeleteModalOpen(true);
  };

  // Handle delete
  const handleDelete = async () => {
    if (!deleteTarget) return;

    try {
      setIsDeleting(true);
      await adminAPI.deleteScreenname(deleteTarget.id);
      setSuccess(`Deleted screenname ${deleteTarget.screenname}`);
      setDeleteModalOpen(false);
      setDeleteTarget(null);
      await loadScreennames(currentPage);
    } catch (err: any) {
      setError(err.message || 'Failed to delete screenname');
    } finally {
      setIsDeleting(false);
    }
  };

  // Get owner display info based on auth provider
  // API returns snake_case fields, so we check both formats
  const getOwnerDisplayInfo = (sn: any) => {
    const provider = sn.userAuthProvider || sn.user_auth_provider || 'x';
    const email = sn.userEmail || sn.user_email;
    const discordDisplay = sn.userDiscordDisplayName || sn.user_discord_display_name;
    const discordUsername = sn.userDiscordUsername || sn.user_discord_username;
    const xDisplay = sn.userXDisplayName || sn.user_x_display_name;
    const xUsername = sn.userXUsername || sn.user_x_username;

    if (provider === 'email') {
      return {
        displayName: email || 'Email User',
        icon: 'email' as const
      };
    } else if (provider === 'discord') {
      return {
        displayName: discordDisplay || discordUsername || 'Discord User',
        icon: 'discord' as const
      };
    } else {
      return {
        displayName: xDisplay || xUsername || 'X User',
        icon: 'x' as const
      };
    }
  };

  // Icon components - using dt- prefix to avoid CSS conflicts
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

  const renderOwnerIcon = (icon: 'email' | 'discord' | 'x') => {
    switch (icon) {
      case 'email': return <EmailIcon />;
      case 'discord': return <DiscordIcon />;
      default: return <XIcon />;
    }
  };

  // Pagination
  const totalPages = Math.ceil(totalCount / ITEMS_PER_PAGE);
  const startIndex = currentPage * ITEMS_PER_PAGE + 1;
  const endIndex = Math.min((currentPage + 1) * ITEMS_PER_PAGE, totalCount);

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
          Page {currentPage + 1} of {totalPages} ({startIndex}-{endIndex} of {totalCount} screennames)
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

  const renderScreennameRow = (sn: any) => {
    const owner = getOwnerDisplayInfo(sn);
    // Handle both camelCase and snake_case from API
    const isActive = sn.userIsActive ?? sn.user_is_active ?? true;
    const isPrimary = sn.isPrimary ?? sn.is_primary ?? false;
    const createdAt = sn.createdAt || sn.created_at;
    
    return (
      <tr key={sn.id} className={`screenname-row ${!isActive ? 'user-inactive' : ''}`}>
        <td className="col-screenname">
          <div className="screenname-cell">
            <span className="screenname-name">{sn.screenname}</span>
            <button 
              className="btn-copy-inline" 
              onClick={() => copyToClipboard(sn.screenname, 'screenname')}
              title="Copy screenname"
            >
              <CopyIcon />
            </button>
            {isPrimary && <span className="badge-primary">Primary</span>}
          </div>
        </td>
        <td className="col-owner">
          <div className="owner-cell">
            {renderOwnerIcon(owner.icon)}
            <span className="owner-name">{owner.displayName}</span>
          </div>
        </td>
        <td className="col-status">
          <span className={`status-badge ${isActive ? 'status-active' : 'status-inactive'}`}>
            {isActive ? 'Active' : 'Inactive'}
          </span>
        </td>
        <td className="col-created">
          {createdAt ? new Date(createdAt).toLocaleDateString() : '—'}
        </td>
        <td className="col-actions">
          <div className="screenname-actions">
            <button
              onClick={() => openResetModal(sn)}
              className="btn-action"
              title="Reset password"
            >
              Reset Password
            </button>
            <button
              onClick={() => openDeleteModal(sn)}
              className="btn-action btn-delete"
              title="Delete screenname"
            >
              Delete
            </button>
          </div>
        </td>
      </tr>
    );
  };

  return (
    <div className="screenname-management">
      <div className="management-header">
        <h2>Screenname Management</h2>
        <div className="management-controls">
          <span className="total-count">{totalCount} screennames</span>
          <button onClick={() => loadScreennames(currentPage)} className="btn-refresh">
            Refresh
          </button>
        </div>
      </div>

      {error && <ErrorMessage message={error} onClose={() => setError(null)} />}
      {success && <div className="success-message">{success}</div>}

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <>
          <div className="screennames-table-container">
            <table className="screennames-table">
              <thead>
                <tr>
                  <th>Screenname</th>
                  <th>Owner</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {screennames.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="no-data">
                      No screennames found
                    </td>
                  </tr>
                ) : (
                  screennames.map(renderScreennameRow)
                )}
              </tbody>
            </table>
          </div>
          {renderPagination()}
        </>
      )}

      {/* Reset Password Modal */}
      <Modal
        isOpen={resetModalOpen}
        onClose={() => !isResetting && setResetModalOpen(false)}
        title="Reset Screenname Password"
      >
        {resetTarget && (
          <div className="reset-password-form">
            <p>
              Reset password for <strong>{resetTarget.screenname}</strong>
            </p>
            <div className="form-group">
              <label className="form-label" htmlFor="new-password">New Password (max 8 chars)</label>
              <input
                id="new-password"
                className="form-input"
                type="text"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                maxLength={8}
                placeholder="Enter new password"
                disabled={isResetting}
                autoFocus
              />
            </div>
            <p className="warning-text">
              Passwords must be 1-8 characters for AOL client compatibility.
            </p>
            <div className="modal-actions">
              <button
                onClick={() => setResetModalOpen(false)}
                disabled={isResetting}
                className="btn-cancel"
              >
                Cancel
              </button>
              <button
                onClick={handlePasswordReset}
                disabled={isResetting || !newPassword.trim()}
                className="btn-primary"
              >
                {isResetting ? 'Resetting...' : 'Reset Password'}
              </button>
            </div>
          </div>
        )}
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={deleteModalOpen}
        onClose={() => !isDeleting && setDeleteModalOpen(false)}
        title="Delete Screenname"
      >
        {deleteTarget && (
          <div className="delete-confirmation">
            <p>
              Are you sure you want to delete screenname <strong>{deleteTarget.screenname}</strong>?
            </p>
            <p className="warning-text">
              This action cannot be undone. The user will lose access to this screenname.
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
                onClick={handleDelete}
                disabled={isDeleting}
                className="btn-delete"
              >
                {isDeleting ? 'Deleting...' : 'Delete Screenname'}
              </button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

export default ScreennameManagement;

