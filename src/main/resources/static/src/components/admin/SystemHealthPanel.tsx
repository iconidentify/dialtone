import { useState } from 'react';
import { SystemStats, SystemHealth } from '../../types';
import { adminAPI } from '../../services/api';
import LoadingSpinner from '../LoadingSpinner';
import ErrorMessage from '../ErrorMessage';
import Modal from '../Modal';

interface SystemHealthPanelProps {
  stats: SystemStats | null;
  health: SystemHealth | null;
  onRefresh: () => void;
  isLoading: boolean;
}

function SystemHealthPanel({ stats, health, onRefresh, isLoading }: SystemHealthPanelProps) {
  const [error, setError] = useState<string | null>(null);
  const [cleanupModalOpen, setCleanupModalOpen] = useState(false);
  const [isCleaningUp, setIsCleaningUp] = useState(false);
  const [cleanupResult, setCleanupResult] = useState<any>(null);

  // Trigger audit log cleanup
  const handleAuditCleanup = async () => {
    try {
      setIsCleaningUp(true);
      const result = await adminAPI.triggerAuditCleanup();
      setCleanupResult(result);
      setError(null);
      // Refresh data after cleanup
      onRefresh();
    } catch (err: any) {
      setError(err.message || 'Failed to cleanup audit log');
    } finally {
      setIsCleaningUp(false);
    }
  };

  // Format memory in MB
  const formatMemory = (mb: number) => {
    if (mb >= 1024) {
      return `${(mb / 1024).toFixed(1)} GB`;
    }
    return `${Math.round(mb)} MB`;
  };

  // Format timestamp
  const formatTime = (timestamp: number) => {
    return new Date(timestamp).toLocaleString();
  };

  // Get status color class
  const getStatusClass = (status: string) => {
    switch (status.toLowerCase()) {
      case 'healthy':
      case 'operational':
        return 'status-healthy';
      case 'warning':
        return 'status-warning';
      case 'error':
      case 'critical':
        return 'status-error';
      default:
        return 'status-unknown';
    }
  };

  // Get memory usage color
  const getMemoryUsageClass = (percentage: number) => {
    if (percentage >= 90) return 'memory-critical';
    if (percentage >= 75) return 'memory-warning';
    return 'memory-normal';
  };

  const renderSystemStats = () => {
    if (!stats) return null;

    return (
      <div className="health-card">
        <h3>System Statistics</h3>
        <div className="stats-grid">
          <div className="stat-group">
            <h4>Users</h4>
            <div className="stat-items">
              <div className="stat-item">
                <span className="stat-label">Total:</span>
                <span className="stat-value">{stats.totalUsers}</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">Active:</span>
                <span className="stat-value text-success">{stats.activeUsers}</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">Inactive:</span>
                <span className="stat-value text-muted">{stats.inactiveUsers}</span>
              </div>
            </div>
          </div>

          <div className="stat-group">
            <h4>Admin Configuration</h4>
            <div className="stat-items">
              <div className="stat-item">
                <span className="stat-label">Admin Status:</span>
                <span className={`stat-value ${stats.adminEnabled ? 'text-success' : 'text-danger'}`}>
                  {stats.adminEnabled ? 'Enabled' : 'Disabled'}
                </span>
              </div>
              <div className="stat-item">
                <span className="stat-label">Configured Admins:</span>
                <span className="stat-value">{stats.configuredAdminCount}</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">Session Timeout:</span>
                <span className="stat-value">{stats.adminSessionTimeout}m</span>
              </div>
            </div>
          </div>

          <div className="stat-group">
            <h4>Audit Log</h4>
            <div className="stat-items">
              <div className="stat-item">
                <span className="stat-label">Total Entries:</span>
                <span className="stat-value">{stats.auditLogSize.toLocaleString()}</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">Recent Activity:</span>
                <span className="stat-value">
                  {stats.auditLogStats?.recentActivity || 'N/A'}
                </span>
              </div>
              <div className="stat-item cleanup-action">
                <button
                  onClick={() => setCleanupModalOpen(true)}
                  className="btn-cleanup"
                  disabled={isCleaningUp}
                >
                  Cleanup Old Entries
                </button>
              </div>
            </div>
          </div>
        </div>

        <div className="stats-footer">
          <small>Last updated: {formatTime(stats.timestamp)}</small>
        </div>
      </div>
    );
  };

  const renderSystemHealth = () => {
    if (!health) return null;

    return (
      <div className="health-card">
        <h3>System Health</h3>
        <div className="health-overview">
          <div className="overall-status">
            <span className="status-label">Overall Status:</span>
            <span className={`status-indicator ${getStatusClass(health.overallStatus)}`}>
              {health.overallStatus.toUpperCase()}
            </span>
          </div>
        </div>

        <div className="health-details">
          <div className="health-section">
            <h4>Database</h4>
            <div className="health-items">
              <div className="health-item">
                <span className="health-label">Status:</span>
                <span className={`health-value ${getStatusClass(health.database.status)}`}>
                  {health.database.status.toUpperCase()}
                </span>
              </div>
              <div className="health-item">
                <span className="health-label">Last Check:</span>
                <span className="health-value">
                  {formatTime(health.database.lastChecked)}
                </span>
              </div>
              {health.database.error && (
                <div className="health-item error">
                  <span className="health-label">Error:</span>
                  <span className="health-value text-danger">{health.database.error}</span>
                </div>
              )}
            </div>
          </div>

          <div className="health-section">
            <h4>System Resources</h4>
            <div className="health-items">
              <div className="health-item">
                <span className="health-label">Memory Usage:</span>
                <span className={`health-value ${getMemoryUsageClass(health.systemResources.memoryUsagePercent)}`}>
                  {health.systemResources.memoryUsagePercent}%
                </span>
              </div>
              <div className="health-item">
                <span className="health-label">Used Memory:</span>
                <span className="health-value">
                  {formatMemory(health.systemResources.usedMemoryMB)}
                </span>
              </div>
              <div className="health-item">
                <span className="health-label">Free Memory:</span>
                <span className="health-value text-success">
                  {formatMemory(health.systemResources.freeMemoryMB)}
                </span>
              </div>
              <div className="health-item">
                <span className="health-label">Total Memory:</span>
                <span className="health-value">
                  {formatMemory(health.systemResources.totalMemoryMB)}
                </span>
              </div>
            </div>
          </div>

          <div className="health-section">
            <h4>Admin Services</h4>
            <div className="health-items">
              <div className="health-item">
                <span className="health-label">Admin Enabled:</span>
                <span className={`health-value ${health.adminServices.adminEnabled ? 'text-success' : 'text-danger'}`}>
                  {health.adminServices.adminEnabled ? 'Yes' : 'No'}
                </span>
              </div>
              <div className="health-item">
                <span className="health-label">Audit Log Size:</span>
                <span className="health-value">
                  {health.adminServices.auditLogSize.toLocaleString()} entries
                </span>
              </div>
            </div>
          </div>
        </div>

        <div className="health-footer">
          <small>Last updated: {formatTime(health.timestamp)}</small>
        </div>
      </div>
    );
  };

  return (
    <div className="system-health-panel">
      <div className="panel-header">
        <h2>System Health & Statistics</h2>
        <div className="panel-controls">
          <button onClick={onRefresh} className="btn-refresh" disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
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
        <div className="health-grid">
          {renderSystemStats()}
          {renderSystemHealth()}
        </div>
      )}

      {/* Audit Cleanup Modal */}
      <Modal
        isOpen={cleanupModalOpen}
        onClose={() => !isCleaningUp && setCleanupModalOpen(false)}
        title="Audit Log Cleanup"
      >
        <div className="cleanup-modal">
          {!cleanupResult ? (
            <div className="cleanup-confirmation">
              <p>This will remove old audit log entries based on the configured retention policy.</p>
              <p className="warning-text">
                <strong>Warning:</strong> This action cannot be undone.
              </p>
              <div className="modal-actions">
                <button
                  onClick={() => setCleanupModalOpen(false)}
                  disabled={isCleaningUp}
                  className="btn-cancel"
                >
                  Cancel
                </button>
                <button
                  onClick={handleAuditCleanup}
                  disabled={isCleaningUp}
                  className="btn-cleanup"
                >
                  {isCleaningUp ? 'Cleaning up...' : 'Start Cleanup'}
                </button>
              </div>
            </div>
          ) : (
            <div className="cleanup-results">
              <h4>Cleanup Complete</h4>
              <div className="cleanup-stats">
                <div className="cleanup-stat">
                  <span>Old entries deleted:</span>
                  <span>{cleanupResult.deletedOldEntries || 0}</span>
                </div>
                <div className="cleanup-stat">
                  <span>Excess entries deleted:</span>
                  <span>{cleanupResult.deletedExcessEntries || 0}</span>
                </div>
                <div className="cleanup-stat total">
                  <span>Total deleted:</span>
                  <span>{cleanupResult.totalDeleted || 0}</span>
                </div>
              </div>
              <div className="modal-actions">
                <button
                  onClick={() => {
                    setCleanupModalOpen(false);
                    setCleanupResult(null);
                  }}
                  className="btn-close"
                >
                  Close
                </button>
              </div>
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}

export default SystemHealthPanel;