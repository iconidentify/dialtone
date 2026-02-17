import { useState, useEffect } from 'react';
import { ScreennamePreferences } from '../types';
import { preferencesAPI } from '../services/api';

interface ScreennamePreferencesPanelProps {
  screennameId: number;
  screennameName: string;
  onClose: () => void;
}

const ScreennamePreferencesPanel = ({
  screennameId,
  screennameName,
  onClose,
}: ScreennamePreferencesPanelProps) => {
  const [preferences, setPreferences] = useState<ScreennamePreferences | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    loadPreferences();
  }, [screennameId]);

  const loadPreferences = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const prefs = await preferencesAPI.getPreferences(screennameId);
      setPreferences(prefs);
    } catch (err: any) {
      setError(err.message || 'Failed to load preferences');
    } finally {
      setIsLoading(false);
    }
  };

  const handleToggleLowColorMode = async () => {
    if (!preferences || isSaving) return;

    const newValue = !preferences.lowColorMode;
    setIsSaving(true);
    setError(null);
    setSaveSuccess(false);

    try {
      const updated = await preferencesAPI.updatePreferences(screennameId, {
        lowColorMode: newValue,
      });
      setPreferences(updated);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 2000);
    } catch (err: any) {
      setError(err.message || 'Failed to save preferences');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="preferences-panel">
        <div className="preferences-loading">Loading preferences...</div>
      </div>
    );
  }

  return (
    <div className="preferences-panel">
      <div className="preferences-header">
        <h4>Preferences for {screennameName}</h4>
        <button
          onClick={onClose}
          className="btn btn-outline btn-small"
          aria-label="Close preferences"
        >
          Close
        </button>
      </div>

      {error && (
        <div className="preferences-error">
          {error}
        </div>
      )}

      {saveSuccess && (
        <div className="preferences-success">
          Preferences saved
        </div>
      )}

      <div className="preferences-content">
        <div className="preference-row">
          <div className="preference-info">
            <span className="preference-label">Low Color Mode</span>
            <span className="preference-description">
              Use a high-contrast black and white theme optimized for accessibility
            </span>
          </div>
          <label className="toggle-switch">
            <input
              type="checkbox"
              checked={preferences?.lowColorMode ?? false}
              onChange={handleToggleLowColorMode}
              disabled={isSaving}
            />
            <span className="toggle-slider"></span>
          </label>
        </div>
      </div>
    </div>
  );
};

export default ScreennamePreferencesPanel;
