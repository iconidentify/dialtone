import { useState, useEffect } from 'react';
import {
  User,
  Screenname,
  ScreennameFormData,
  UpdateScreennameFormData,
  UpdatePasswordFormData,
  getMaxScreennamesForUser,
} from '../types';
import { screennameAPI } from '../services/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import ScreennameList from '../components/ScreennameList';
import CreateScreennameModal from '../components/CreateScreennameModal';
import EditScreennameModal from '../components/EditScreennameModal';
import UpdatePasswordModal from '../components/UpdatePasswordModal';
import DeleteScreennameModal from '../components/DeleteScreennameModal';
import MainLayout from '../components/layout/MainLayout';

interface DashboardPageProps {
  user: User;
  onUserUpdate: (user: User) => void;
  onLogout: () => void;
}

const DashboardPage = ({ user, onUserUpdate, onLogout }: DashboardPageProps) => {
  const [screennames, setScreennames] = useState<Screenname[]>(user.screennames);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  // Modal states
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [selectedScreenname, setSelectedScreenname] = useState<Screenname | null>(null);

  // Loading states for individual operations
  const [createLoading, setCreateLoading] = useState(false);
  const [editLoading, setEditLoading] = useState(false);
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  // Clear messages after timeout
  useEffect(() => {
    if (success) {
      const timer = setTimeout(() => setSuccess(null), 5000);
      return () => clearTimeout(timer);
    }
  }, [success]);

  useEffect(() => {
    if (error) {
      const timer = setTimeout(() => setError(null), 10000);
      return () => clearTimeout(timer);
    }
  }, [error]);

  const refreshScreennames = async () => {
    setIsLoading(true);
    try {
      const updatedScreennames = await screennameAPI.getScreennames();
      setScreennames(updatedScreennames);

      // Update the user object with new screennames
      const updatedUser = { ...user, screennames: updatedScreennames };
      onUserUpdate(updatedUser);
    } catch (error: any) {
      setError(error.message || 'Failed to refresh screennames');
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateScreenname = async (data: ScreennameFormData) => {
    setCreateLoading(true);
    setError(null);

    try {
      await screennameAPI.createScreenname({
        screenname: data.screenname,
        password: data.password,
      });

      setSuccess(`Screenname '${data.screenname}' created successfully!`);
      setCreateModalOpen(false);
      await refreshScreennames();
    } catch (error: any) {
      setError(error.message || 'Failed to create screenname');
    } finally {
      setCreateLoading(false);
    }
  };

  const handleEditScreenname = async (data: UpdateScreennameFormData) => {
    if (!selectedScreenname) return;

    setEditLoading(true);
    setError(null);

    try {
      await screennameAPI.updateScreenname(selectedScreenname.id, {
        screenname: data.screenname,
      });

      setSuccess(`Screenname updated to '${data.screenname}' successfully!`);
      setEditModalOpen(false);
      setSelectedScreenname(null);
      await refreshScreennames();
    } catch (error: any) {
      setError(error.message || 'Failed to update screenname');
    } finally {
      setEditLoading(false);
    }
  };

  const handleUpdatePassword = async (data: UpdatePasswordFormData) => {
    if (!selectedScreenname) return;

    setPasswordLoading(true);
    setError(null);

    try {
      await screennameAPI.updatePassword(selectedScreenname.id, {
        password: data.password,
      });

      setSuccess(`Password updated for '${selectedScreenname.screenname}' successfully!`);
      setPasswordModalOpen(false);
      setSelectedScreenname(null);
      await refreshScreennames();
    } catch (error: any) {
      setError(error.message || 'Failed to update password');
    } finally {
      setPasswordLoading(false);
    }
  };

  const handleDeleteScreenname = async () => {
    if (!selectedScreenname) return;

    setDeleteLoading(true);
    setError(null);

    try {
      await screennameAPI.deleteScreenname(selectedScreenname.id);

      setSuccess(`Screenname '${selectedScreenname.screenname}' deleted successfully!`);
      setDeleteModalOpen(false);
      setSelectedScreenname(null);
      await refreshScreennames();
    } catch (error: any) {
      setError(error.message || 'Failed to delete screenname');
    } finally {
      setDeleteLoading(false);
    }
  };

  const openEditModal = (screenname: Screenname) => {
    setSelectedScreenname(screenname);
    setEditModalOpen(true);
  };

  const openPasswordModal = (screenname: Screenname) => {
    setSelectedScreenname(screenname);
    setPasswordModalOpen(true);
  };

  const openDeleteModal = (screenname: Screenname) => {
    setSelectedScreenname(screenname);
    setDeleteModalOpen(true);
  };

  const maxScreennames = getMaxScreennamesForUser(user);
  const canCreateMore = screennames.length < maxScreennames;

  return (
    <MainLayout
      user={user}
      onLogout={onLogout}
      headerTitle="Screenname Manager"
      headerSubtitle="Manage the screennames linked to your Dialtone account."
      actions={
        canCreateMore ? (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setCreateModalOpen(true)}
            disabled={isLoading}
          >
            Create Screenname
          </button>
        ) : undefined
      }
    >
      {error && <ErrorMessage message={error} onClose={() => setError(null)} />}
      {success && <div className="success">{success}</div>}

      {/* Try It Now - Browser VM Card */}
      <a
        href="/mac_connect"
        className="try-now-card"
        target="_blank"
        rel="noopener noreferrer"
      >
        <div className="try-now-icon">
          <svg viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
          </svg>
        </div>
        <div className="try-now-content">
          <h3 className="try-now-title">Try AOL 3.0 in Your Browser</h3>
          <p className="try-now-description">
            Launch a Mac System 7 environment instantly. Select <strong>Guest</strong> at login
            and use your Dialtone screenname credentials.
          </p>
        </div>
        <div className="try-now-arrow">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M5 12h14M12 5l7 7-7 7"/>
          </svg>
        </div>
      </a>

      <section className="dt-panel">
        <div className="dt-panel-header">
          <h2 className="card-title">Account Overview</h2>
        </div>
        <div className="dt-grid-two">
          <div>
            <p className="form-label">Display name</p>
            <p>{user.displayName}</p>
          </div>
          <div>
            <p className="form-label">
              {user.authProvider === 'email' ? 'Email' 
                : user.authProvider === 'discord' ? 'Discord username' 
                : 'X username'}
            </p>
            <p className="provider-username">
              <span className={`provider-icon provider-${user.authProvider || 'x'}`}>
                {user.authProvider === 'email' ? (
                  <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                    <path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"/>
                  </svg>
                ) : user.authProvider === 'discord' ? (
                  <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                    <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.947 2.418-2.157 2.418z"/>
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" fill="currentColor" width="14" height="14">
                    <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
                  </svg>
                )}
              </span>
              {user.authProvider === 'email' 
                ? user.email || user.providerUsername
                : user.authProvider === 'discord' 
                  ? user.discordUsername || user.providerUsername
                  : `@${user.xUsername || user.providerUsername}`}
            </p>
          </div>
          <div>
            <p className="form-label">Screennames in use</p>
            <p>{screennames.length}/{maxScreennames}</p>
          </div>
          <div>
            <p className="form-label">Account type</p>
            <span className={`status-badge ${user.isAdmin ? 'status-admin' : 'status-user'}`}>
              {user.isAdmin ? 'Admin' : 'Standard'}
            </span>
          </div>
        </div>
        <div className="dt-panel-footer">
          You can create up to {maxScreennames} screennames ({screennames.length}/{maxScreennames} used).
        </div>
      </section>

      <section className="dt-panel">
        <div className="dt-panel-header">
          <h2 className="card-title">
            Your Screenname{screennames.length > 1 ? 's' : ''}
          </h2>
        </div>

        {isLoading ? (
          <LoadingSpinner message="Loading screennames..." />
        ) : screennames.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state-icon">🛰️</div>
            <h3 className="empty-state-title">No screennames yet</h3>
            <p className="empty-state-description">
              Create your first screenname to log into your AOL client.
            </p>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => setCreateModalOpen(true)}
            >
              Create Screenname
            </button>
          </div>
        ) : (
          <ScreennameList
            screennames={screennames}
            onEdit={openEditModal}
            onChangePassword={openPasswordModal}
            onDelete={openDeleteModal}
          />
        )}
      </section>

      <CreateScreennameModal
        isOpen={createModalOpen}
        onClose={() => setCreateModalOpen(false)}
        onSubmit={handleCreateScreenname}
        isLoading={createLoading}
      />
      <EditScreennameModal
        isOpen={editModalOpen}
        onClose={() => {
          setEditModalOpen(false);
          setSelectedScreenname(null);
        }}
        onSubmit={handleEditScreenname}
        screenname={selectedScreenname}
        isLoading={editLoading}
      />
      <UpdatePasswordModal
        isOpen={passwordModalOpen}
        onClose={() => {
          setPasswordModalOpen(false);
          setSelectedScreenname(null);
        }}
        onSubmit={handleUpdatePassword}
        screenname={selectedScreenname}
        isLoading={passwordLoading}
      />
      <DeleteScreennameModal
        isOpen={deleteModalOpen}
        onClose={() => {
          setDeleteModalOpen(false);
          setSelectedScreenname(null);
        }}
        onConfirm={handleDeleteScreenname}
        screenname={selectedScreenname}
        isLoading={deleteLoading}
      />
    </MainLayout>
  );
};

export default DashboardPage;