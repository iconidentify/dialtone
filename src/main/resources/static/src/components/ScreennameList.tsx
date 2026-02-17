import { useState } from 'react';
import { Screenname } from '../types';
import ScreennamePreferencesPanel from './ScreennamePreferencesPanel';

interface ScreennameListProps {
  screennames: Screenname[];
  onEdit: (screenname: Screenname) => void;
  onChangePassword: (screenname: Screenname) => void;
  onDelete?: (screenname: Screenname) => void;
}

const ScreennameList = ({
  screennames,
  onEdit,
  onChangePassword,
  onDelete,
}: ScreennameListProps) => {
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const formatDate = (dateString: string) => {
    // Handle ISO timestamps with microseconds (e.g., 2025-11-20T21:21:25.093584)
    // JavaScript Date only supports milliseconds, so we need to truncate microseconds
    const normalizedDate = dateString.replace(/(\.\d{3})\d+/, '$1');
    const date = new Date(normalizedDate);

    // Check if date is valid
    if (isNaN(date.getTime())) {
      return 'Unknown date';
    }

    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  };

  const toggleExpanded = (id: number) => {
    setExpandedId(expandedId === id ? null : id);
  };

  return (
    <div className="screenname-list">
      {screennames.map((screenname) => (
        <div key={screenname.id} className="screenname-item-container">
          <div className="screenname-item">
            <div className="screenname-info">
              <div className="screenname-name">
                <span className="screenname-handle">{screenname.screenname}</span>
                <span className={`status-badge ${screenname.isPrimary ? 'status-admin' : 'status-user'}`}>
                  {screenname.isPrimary ? 'Primary' : 'Secondary'}
                </span>
              </div>
              <div className="screenname-meta">
                <div className="screenname-meta-block">
                  <small>Created</small>
                  <strong>{formatDate(screenname.createdAt)}</strong>
                </div>
              </div>
            </div>

            <div className="screenname-actions">
              <button
                onClick={() => toggleExpanded(screenname.id)}
                className={`btn btn-outline btn-small ${expandedId === screenname.id ? 'active' : ''}`}
                title="View preferences"
              >
                Preferences
              </button>

              <button
                onClick={() => onEdit(screenname)}
                className="btn btn-outline btn-small"
              >
                Edit
              </button>

              <button
                onClick={() => onChangePassword(screenname)}
                className="btn btn-secondary btn-small"
              >
                Password
              </button>

              {onDelete && (
                <button
                  onClick={() => onDelete(screenname)}
                  className="btn btn-danger-outline btn-small"
                  title="Delete screenname"
                >
                  Delete
                </button>
              )}
            </div>
          </div>

          {expandedId === screenname.id && (
            <ScreennamePreferencesPanel
              screennameId={screenname.id}
              screennameName={screenname.screenname}
              onClose={() => setExpandedId(null)}
            />
          )}
        </div>
      ))}
    </div>
  );
};

export default ScreennameList;