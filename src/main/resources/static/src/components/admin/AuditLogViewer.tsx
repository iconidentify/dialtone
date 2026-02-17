import { useState, useEffect } from 'react';
import { AuditLogEntry, AuditLogResponse, AuditFilters } from '../../types';
import { adminAPI } from '../../services/api';
import LoadingSpinner from '../LoadingSpinner';
import ErrorMessage from '../ErrorMessage';

function AuditLogViewer() {
  const [entries, setEntries] = useState<AuditLogEntry[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [filters, setFilters] = useState<AuditFilters>({});
  const [showFilters, setShowFilters] = useState(false);

  const ENTRIES_PER_PAGE = 50;

  // Load audit log entries
  const loadEntries = async (page = 0, currentFilters = filters) => {
    try {
      setIsLoading(true);
      const offset = page * ENTRIES_PER_PAGE;
      const response: AuditLogResponse = await adminAPI.getAuditLog(
        ENTRIES_PER_PAGE,
        offset,
        currentFilters
      );

      setEntries(response.entries);
      setTotalCount(response.totalCount);
      setCurrentPage(page);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to load audit log');
    } finally {
      setIsLoading(false);
    }
  };

  // Initial load
  useEffect(() => {
    loadEntries();
  }, []);

  // Handle filter changes
  const handleFilterChange = (newFilters: Partial<AuditFilters>) => {
    const updatedFilters = { ...filters, ...newFilters };
    setFilters(updatedFilters);
    loadEntries(0, updatedFilters);
  };

  // Clear filters
  const clearFilters = () => {
    setFilters({});
    loadEntries(0, {});
  };

  // Handle page change
  const handlePageChange = (page: number) => {
    loadEntries(page);
  };

  // Format date
  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  // Format details JSON
  const formatDetails = (details?: string) => {
    if (!details) return '-';
    try {
      const parsed = JSON.parse(details);
      return (
        <details className="audit-details">
          <summary>View Details</summary>
          <pre>{JSON.stringify(parsed, null, 2)}</pre>
        </details>
      );
    } catch {
      return <span className="details-text">{details}</span>;
    }
  };

  // Get action display style
  const getActionStyle = (action: string) => {
    if (action.includes('DELETE') || action.includes('REVOKE')) {
      return 'action-destructive';
    }
    if (action.includes('CREATE') || action.includes('GRANT')) {
      return 'action-create';
    }
    if (action.includes('UPDATE') || action.includes('RESET')) {
      return 'action-modify';
    }
    return 'action-view';
  };

  // Calculate pagination
  const totalPages = Math.ceil(totalCount / ENTRIES_PER_PAGE);
  const startIndex = currentPage * ENTRIES_PER_PAGE + 1;
  const endIndex = Math.min((currentPage + 1) * ENTRIES_PER_PAGE, totalCount);

  const renderFilters = () => (
    <div className={`audit-filters ${showFilters ? 'filters-open' : ''}`}>
      <div className="filter-row">
        <div className="filter-group">
          <label htmlFor="action-filter">Action:</label>
          <select
            id="action-filter"
            value={filters.action || ''}
            onChange={(e) => handleFilterChange({ action: e.target.value || undefined })}
          >
            <option value="">All Actions</option>
            <option value="LIST_USERS">List Users</option>
            <option value="VIEW_USER_DETAILS">View User Details</option>
            <option value="ENABLE_USER">Enable User</option>
            <option value="DISABLE_USER">Disable User</option>
            <option value="DELETE_USER">Delete User</option>
            <option value="DELETE_SCREENNAME">Delete Screenname</option>
            <option value="RESET_SCREENNAME_PASSWORD">Reset Password</option>
            <option value="GRANT_ADMIN_ROLE">Grant Admin Role</option>
            <option value="REVOKE_ADMIN_ROLE">Revoke Admin Role</option>
            <option value="VIEW_AUDIT_LOG">View Audit Log</option>
            <option value="VIEW_SYSTEM_STATS">View System Stats</option>
          </select>
        </div>

        <div className="filter-group">
          <label htmlFor="admin-filter">Admin User ID:</label>
          <input
            type="number"
            id="admin-filter"
            value={filters.adminUserId || ''}
            onChange={(e) => handleFilterChange({
              adminUserId: e.target.value ? parseInt(e.target.value) : undefined
            })}
            placeholder="Filter by admin ID"
          />
        </div>

        <div className="filter-actions">
          <button onClick={clearFilters} className="btn-clear-filters">
            Clear Filters
          </button>
          <button onClick={() => loadEntries(0)} className="btn-refresh">
            Refresh
          </button>
        </div>
      </div>
    </div>
  );

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
          Page {currentPage + 1} of {totalPages} ({startIndex}-{endIndex} of {totalCount} entries)
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

  const renderAuditEntry = (entry: AuditLogEntry) => (
    <tr key={entry.id} className="audit-entry">
      <td className="timestamp">{formatDate(entry.createdAt)}</td>
      <td className="admin-info">
        <div>
          <strong>{entry.adminUsername}</strong>
          <br />
          <span className="admin-id">ID: {entry.adminUserId}</span>
        </div>
      </td>
      <td className={`action ${getActionStyle(entry.action)}`}>
        {entry.action}
      </td>
      <td className="target">
        {entry.targetUsername ? (
          <div>
            <strong>{entry.targetUsername}</strong>
            {entry.targetUserId && <br />}
            {entry.targetUserId && <span className="target-id">ID: {entry.targetUserId}</span>}
          </div>
        ) : entry.targetUserId ? (
          <span className="target-id">User ID: {entry.targetUserId}</span>
        ) : (
          '-'
        )}
        {entry.targetScreenname && (
          <div className="target-screenname">
            Screenname: <strong>{entry.targetScreenname}</strong>
          </div>
        )}
      </td>
      <td className="details">{formatDetails(entry.details)}</td>
      <td className="ip-address">{entry.ipAddress || '-'}</td>
    </tr>
  );

  return (
    <div className="audit-log-viewer">
      <div className="viewer-header">
        <h2>Admin Audit Log</h2>
        <div className="viewer-controls">
          <button
            onClick={() => setShowFilters(!showFilters)}
            className={`btn-toggle-filters ${showFilters ? 'active' : ''}`}
          >
            Filters {showFilters ? '▲' : '▼'}
          </button>
        </div>
      </div>

      {renderFilters()}

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
          <div className="audit-table-container">
            <table className="audit-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Admin User</th>
                  <th>Action</th>
                  <th>Target</th>
                  <th>Details</th>
                  <th>IP Address</th>
                </tr>
              </thead>
              <tbody>
                {entries.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="no-entries">
                      No audit log entries found
                    </td>
                  </tr>
                ) : (
                  entries.map(renderAuditEntry)
                )}
              </tbody>
            </table>
          </div>

          {renderPagination()}
        </>
      )}
    </div>
  );
}

export default AuditLogViewer;