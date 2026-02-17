import { useState, useEffect } from 'react';
import { User, SystemStats, SystemHealth } from '../types';
import { adminAPI } from '../services/api';
import LoadingSpinner from '../components/LoadingSpinner';
import ErrorMessage from '../components/ErrorMessage';
import UserManagement from '../components/admin/UserManagement';
import ScreennameManagement from '../components/admin/ScreennameManagement';
import AuditLogViewer from '../components/admin/AuditLogViewer';
import FdoWorkbench from '../components/admin/FdoWorkbench';
import MainLayout from '../components/layout/MainLayout';

interface AdminDashboardProps {
  user: User;
  onLogout: () => void;
}

type AdminSection = 'overview' | 'users' | 'screennames' | 'audit' | 'fdo';

function AdminDashboard({ user, onLogout }: AdminDashboardProps) {
  const [currentSection, setCurrentSection] = useState<AdminSection>('overview');
  const [systemStats, setSystemStats] = useState<SystemStats | null>(null);
  const [systemHealth, setSystemHealth] = useState<SystemHealth | null>(null);
  const [aolMetrics, setAolMetrics] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load initial dashboard data
  useEffect(() => {
    const loadDashboardData = async () => {
      try {
        setIsLoading(true);
        const [statsResponse, healthResponse, aolMetricsResponse] = await Promise.all([
          adminAPI.getSystemStats(),
          adminAPI.getSystemHealth(),
          adminAPI.getAolMetrics(),
        ]);

        setSystemStats(statsResponse.statistics);
        setSystemHealth(healthResponse.health);
        setAolMetrics(aolMetricsResponse.metrics);
        setError(null);
      } catch (err: any) {
        setError(err.message || 'Failed to load dashboard data');
      } finally {
        setIsLoading(false);
      }
    };

    loadDashboardData();
  }, []);

  const refreshDashboard = async () => {
    try {
      const [statsResponse, healthResponse, aolMetricsResponse] = await Promise.all([
        adminAPI.getSystemStats(),
        adminAPI.getSystemHealth(),
        adminAPI.getAolMetrics(),
      ]);

      setSystemStats(statsResponse.statistics);
      setSystemHealth(healthResponse.health);
      setAolMetrics(aolMetricsResponse.metrics);
    } catch (err: any) {
      setError(err.message || 'Failed to refresh dashboard data');
    }
  };

  const renderSectionTabs = () => (
    <div className="dt-subnav">
      {(['overview', 'users', 'screennames', 'audit', 'fdo'] as AdminSection[]).map(section => (
        <button
          key={section}
          type="button"
          className={`dt-nav-item ${currentSection === section ? 'active' : ''}`}
          onClick={() => setCurrentSection(section)}
        >
          {section === 'overview' && 'Overview'}
          {section === 'users' && 'Users'}
          {section === 'screennames' && 'Screennames'}
          {section === 'audit' && 'Audit'}
          {section === 'fdo' && 'FDO Workbench'}
        </button>
      ))}
    </div>
  );

  const renderOverview = () => (
    <div className="admin-overview">
      <div className="overview-header">
        <h2>AOL Server Operations</h2>
        <button onClick={refreshDashboard} className="btn-refresh">
          Refresh Data
        </button>
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : (
        <div className="overview-grid">
          {/* Server Status */}
          <div className="overview-card">
            <h3>Server Status</h3>
            {aolMetrics && aolMetrics.server && (
              <div className="stats-grid">
                <div className="stat-item">
                  <span className="stat-label">Status:</span>
                  <span className={`stat-value status-${aolMetrics.server.status}`}>
                    {aolMetrics.server.status.toUpperCase()}
                  </span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Uptime:</span>
                  <span className="stat-value">{aolMetrics.server.uptimeHours}h</span>
                </div>
              </div>
            )}
          </div>

          {/* AOL Protocol Configuration */}
          <div className="overview-card">
            <h3>AOL Protocol</h3>
            {aolMetrics && aolMetrics.protocol && (
              <div className="stats-grid">
                <div className="stat-item">
                  <span className="stat-label">AOL Port:</span>
                  <span className="stat-value">{aolMetrics.protocol.aolPort}</span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Verbose Logging:</span>
                  <span className={`stat-value ${aolMetrics.protocol.verboseLogging ? 'status-enabled' : 'status-disabled'}`}>
                    {aolMetrics.protocol.verboseLogging ? 'Enabled' : 'Disabled'}
                  </span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Atomforge URL:</span>
                  <span className="stat-value">{aolMetrics.protocol.atomforgeUrl}</span>
                </div>
              </div>
            )}
          </div>

          {/* AI Services */}
          <div className="overview-card">
            <h3>AI Services</h3>
            {aolMetrics && aolMetrics.ai && (
              <div className="stats-grid">
                <div className="stat-item">
                  <span className="stat-label">Grok AI:</span>
                  <span className={`stat-value ${aolMetrics.ai.grokEnabled ? 'status-enabled' : 'status-disabled'}`}>
                    {aolMetrics.ai.grokEnabled ? 'Enabled' : 'Disabled'}
                  </span>
                </div>
                {aolMetrics.ai.grokEnabled && (
                  <>
                    <div className="stat-item">
                      <span className="stat-label">Model:</span>
                      <span className="stat-value">{aolMetrics.ai.grokModel}</span>
                    </div>
                    <div className="stat-item">
                      <span className="stat-label">News Service:</span>
                      <span className={`stat-value ${aolMetrics.ai.newsServiceEnabled ? 'status-enabled' : 'status-disabled'}`}>
                        {aolMetrics.ai.newsServiceEnabled ? 'Enabled' : 'Disabled'}
                      </span>
                    </div>
                  </>
                )}
              </div>
            )}
          </div>

          {/* Database Health */}
          <div className="overview-card">
            <h3>Database</h3>
            {aolMetrics && aolMetrics.database && (
              <div className="stats-grid">
                <div className="stat-item">
                  <span className="stat-label">Connection Pool:</span>
                  <span className={`stat-value ${aolMetrics.database.connectionPoolActive ? 'status-healthy' : 'status-error'}`}>
                    {aolMetrics.database.connectionPoolActive ? 'Active' : 'Error'}
                  </span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Pool Size:</span>
                  <span className="stat-value">{aolMetrics.database.connectionPoolSize}</span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Database Path:</span>
                  <span className="stat-value">{aolMetrics.database.databasePath}</span>
                </div>
              </div>
            )}
          </div>

          {/* User Management Summary */}
          <div className="overview-card">
            <h3>User Management</h3>
            {systemStats && (
              <div className="stats-grid">
                <div className="stat-item">
                  <span className="stat-label">Total Users:</span>
                  <span className="stat-value">{systemStats.totalUsers}</span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Active Users:</span>
                  <span className="stat-value">{systemStats.activeUsers}</span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Screennames:</span>
                  <span className="stat-value">{systemStats.totalScreennames}</span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Audit Log:</span>
                  <span className="stat-value">{systemStats.auditLogSize} entries</span>
                </div>
              </div>
            )}
          </div>

          {/* System Resources */}
          <div className="overview-card">
            <h3>System Resources</h3>
            {systemHealth && (
              <div className="stats-grid">
                <div className="stat-item">
                  <span className="stat-label">Memory Usage:</span>
                  <span className="stat-value">
                    {systemHealth.systemResources.memoryUsagePercent}%
                  </span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Free Memory:</span>
                  <span className="stat-value">
                    {Math.round(systemHealth.systemResources.freeMemoryMB)}MB
                  </span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Overall Status:</span>
                  <span className={`stat-value status-${systemHealth.overallStatus}`}>
                    {systemHealth.overallStatus.toUpperCase()}
                  </span>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );

  if (!user.isAdmin) {
    return (
      <div className="admin-error">
        <h1>Access Denied</h1>
        <p>You do not have admin privileges to access this dashboard.</p>
        <button onClick={onLogout}>Return to Login</button>
      </div>
    );
  }

  return (
    <MainLayout
      user={user}
      onLogout={onLogout}
      headerTitle="Admin Console"
      headerSubtitle="Monitor Dialtone health, users, and activity in one console."
      actions={
        <button type="button" className="btn btn-outline" onClick={refreshDashboard}>
          Refresh Data
        </button>
      }
    >
      {renderSectionTabs()}

      {error && (
        <ErrorMessage
          message={error}
          onClose={() => setError(null)}
        />
      )}

      {currentSection === 'overview' && renderOverview()}
      {currentSection === 'users' && <UserManagement />}
      {currentSection === 'screennames' && <ScreennameManagement />}
      {currentSection === 'audit' && <AuditLogViewer />}
      {currentSection === 'fdo' && <FdoWorkbench onError={(msg) => setError(msg)} />}
    </MainLayout>
  );
}

export default AdminDashboard;