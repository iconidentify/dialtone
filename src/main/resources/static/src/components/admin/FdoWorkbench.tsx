import { useState, useEffect, useCallback } from 'react';
import { adminAPI } from '../../services/api';
import { FdoCompilationStats } from '../../types';

interface FdoWorkbenchProps {
  onError?: (message: string) => void;
}

const FdoWorkbench = ({ onError }: FdoWorkbenchProps) => {
  // Connected screennames state
  const [connectedScreennames, setConnectedScreennames] = useState<string[]>([]);
  const [selectedScreenname, setSelectedScreenname] = useState<string>('');
  const [loadingScreennames, setLoadingScreennames] = useState(false);

  // FDO input state
  const [fdoScript, setFdoScript] = useState('');
  const [token, setToken] = useState('AT');
  const [streamId, setStreamId] = useState<string>('');

  // Editor mode state
  const [isEditorMode, setIsEditorMode] = useState(false);

  // Submission state
  const [sending, setSending] = useState(false);
  const [lastResult, setLastResult] = useState<{
    success: boolean;
    message: string;
    stats?: FdoCompilationStats;
  } | null>(null);

  // Load connected screennames
  const loadConnectedScreennames = useCallback(async () => {
    setLoadingScreennames(true);
    try {
      const response = await adminAPI.getConnectedScreennames();
      setConnectedScreennames(response.screennames);
      // Auto-select first screenname if none selected
      if (!selectedScreenname && response.screennames.length > 0) {
        setSelectedScreenname(response.screennames[0]);
      }
    } catch (error: any) {
      onError?.(error.message || 'Failed to load connected screennames');
    } finally {
      setLoadingScreennames(false);
    }
  }, [selectedScreenname, onError]);

  // Load screennames on mount and periodically
  useEffect(() => {
    loadConnectedScreennames();
    const interval = setInterval(loadConnectedScreennames, 10000); // Refresh every 10 seconds
    return () => clearInterval(interval);
  }, [loadConnectedScreennames]);

  // Handle Escape key to exit editor mode
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isEditorMode) {
        setIsEditorMode(false);
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [isEditorMode]);

  // Handle send FDO
  const handleSendFdo = async () => {
    if (!selectedScreenname) {
      setLastResult({
        success: false,
        message: 'Please select a screenname',
      });
      return;
    }

    if (!fdoScript.trim()) {
      setLastResult({
        success: false,
        message: 'Please enter an FDO script',
      });
      return;
    }

    setSending(true);
    setLastResult(null);

    try {
      const response = await adminAPI.sendFdo({
        screenname: selectedScreenname,
        fdoScript: fdoScript.trim(),
        token: token || 'AT',
        streamId: streamId ? parseInt(streamId, 10) : undefined,
      });

      setLastResult({
        success: response.success,
        message: response.message,
        stats: response.compilationStats,
      });
    } catch (error: any) {
      setLastResult({
        success: false,
        message: error.message || 'Failed to send FDO',
      });
    } finally {
      setSending(false);
    }
  };

  // Clear the form
  const handleClear = () => {
    setFdoScript('');
    setLastResult(null);
  };

  // Render the screenname selector
  const renderScreennameSelector = (idSuffix: string = '') => (
    <div className="fdo-field fdo-field-screenname">
      <label htmlFor={`screenname${idSuffix}`}>Target Screenname</label>
      <div className="fdo-select-wrapper">
        <select
          id={`screenname${idSuffix}`}
          value={selectedScreenname}
          onChange={(e) => setSelectedScreenname(e.target.value)}
          disabled={loadingScreennames}
        >
          {connectedScreennames.length === 0 ? (
            <option value="">No clients connected</option>
          ) : (
            <>
              <option value="">Select a screenname...</option>
              {connectedScreennames.map((sn) => (
                <option key={sn} value={sn}>
                  {sn}
                </option>
              ))}
            </>
          )}
        </select>
        <button
          type="button"
          className="btn btn-outline btn-small fdo-refresh-btn"
          onClick={loadConnectedScreennames}
          disabled={loadingScreennames}
          title="Refresh connected screennames"
        >
          {loadingScreennames ? '...' : '↻'}
        </button>
      </div>
      <span className="fdo-field-hint">
        {connectedScreennames.length} client{connectedScreennames.length !== 1 ? 's' : ''} online
      </span>
    </div>
  );

  // Render the result panel
  const renderResult = () => {
    if (!lastResult) return null;
    return (
      <div className={`fdo-result ${lastResult.success ? 'fdo-result-success' : 'fdo-result-error'}`}>
        <div className="fdo-result-header">
          <span className="fdo-result-icon">
            {lastResult.success ? '✓' : '✕'}
          </span>
          <span className="fdo-result-message">{lastResult.message}</span>
        </div>
        {lastResult.stats && (
          <div className="fdo-result-stats">
            <div className="fdo-stat">
              <span className="fdo-stat-label">Chunks</span>
              <span className="fdo-stat-value">{lastResult.stats.chunkCount}</span>
            </div>
            <div className="fdo-stat">
              <span className="fdo-stat-label">Total Size</span>
              <span className="fdo-stat-value">
                {lastResult.stats.totalBytes.toLocaleString()} bytes
              </span>
            </div>
            <div className="fdo-stat">
              <span className="fdo-stat-label">Compilation Time</span>
              <span className="fdo-stat-value">{lastResult.stats.compilationTimeMs}ms</span>
            </div>
          </div>
        )}
      </div>
    );
  };

  // Editor Mode - Fullscreen overlay
  if (isEditorMode) {
    return (
      <div className="fdo-editor-mode">
        {/* Compact Toolbar */}
        <div className="fdo-editor-toolbar">
          <div className="fdo-editor-toolbar-left">
            <span className="fdo-editor-title">FDO Editor</span>
          </div>
          <div className="fdo-editor-toolbar-center">
            <div className="fdo-editor-field">
              <label htmlFor="screenname-editor">Target</label>
              <select
                id="screenname-editor"
                value={selectedScreenname}
                onChange={(e) => setSelectedScreenname(e.target.value)}
                disabled={loadingScreennames}
              >
                {connectedScreennames.length === 0 ? (
                  <option value="">No clients</option>
                ) : (
                  <>
                    <option value="">Select...</option>
                    {connectedScreennames.map((sn) => (
                      <option key={sn} value={sn}>
                        {sn}
                      </option>
                    ))}
                  </>
                )}
              </select>
            </div>
            <div className="fdo-editor-field fdo-editor-field-small">
              <label htmlFor="token-editor">Token</label>
              <input
                type="text"
                id="token-editor"
                value={token}
                onChange={(e) => setToken(e.target.value)}
                placeholder="AT"
                maxLength={4}
              />
            </div>
            <div className="fdo-editor-field fdo-editor-field-small">
              <label htmlFor="streamId-editor">Stream</label>
              <input
                type="text"
                id="streamId-editor"
                value={streamId}
                onChange={(e) => setStreamId(e.target.value.replace(/\D/g, ''))}
                placeholder="Auto"
              />
            </div>
          </div>
          <div className="fdo-editor-toolbar-right">
            <button
              type="button"
              className="btn btn-outline btn-small"
              onClick={handleClear}
              disabled={sending}
            >
              Clear
            </button>
            <button
              type="button"
              className="btn btn-primary btn-small"
              onClick={handleSendFdo}
              disabled={sending || !selectedScreenname || !fdoScript.trim()}
            >
              {sending ? 'Sending...' : 'Send'}
            </button>
            <button
              type="button"
              className="btn btn-outline btn-small fdo-editor-exit-btn"
              onClick={() => setIsEditorMode(false)}
              title="Exit Editor Mode (Esc)"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Fullscreen Textarea */}
        <div className="fdo-editor-body">
          <textarea
            id="fdoScript-editor"
            value={fdoScript}
            onChange={(e) => setFdoScript(e.target.value)}
            placeholder="Enter FDO script here..."
            className="fdo-editor-textarea"
            spellCheck={false}
            autoFocus
          />
        </div>

        {/* Status Bar */}
        <div className="fdo-editor-statusbar">
          <div className="fdo-editor-statusbar-left">
            {fdoScript.length > 0 && (
              <span>{fdoScript.length.toLocaleString()} characters</span>
            )}
          </div>
          <div className="fdo-editor-statusbar-center">
            {lastResult && (
              <span className={lastResult.success ? 'fdo-status-success' : 'fdo-status-error'}>
                {lastResult.success ? '✓' : '✕'} {lastResult.message}
              </span>
            )}
          </div>
          <div className="fdo-editor-statusbar-right">
            <span className="fdo-editor-hint">Press Esc to exit</span>
          </div>
        </div>
      </div>
    );
  }

  // Normal Mode
  return (
    <div className="fdo-workbench">
      <div className="fdo-workbench-header">
        <h2>FDO Workbench</h2>
        <p className="fdo-workbench-description">
          Compile and send FDO scripts to connected AOL clients in real-time for testing.
        </p>
      </div>

      <div className="fdo-workbench-content">
        {/* Target Selection */}
        <div className="fdo-target-section">
          <div className="fdo-target-row">
            {renderScreennameSelector()}

            <div className="fdo-field fdo-field-token">
              <label htmlFor="token">Token</label>
              <input
                type="text"
                id="token"
                value={token}
                onChange={(e) => setToken(e.target.value)}
                placeholder="AT"
                maxLength={4}
              />
              <span className="fdo-field-hint">P3 token (default: AT)</span>
            </div>

            <div className="fdo-field fdo-field-stream">
              <label htmlFor="streamId">Stream ID</label>
              <input
                type="text"
                id="streamId"
                value={streamId}
                onChange={(e) => setStreamId(e.target.value.replace(/\D/g, ''))}
                placeholder="Auto"
              />
              <span className="fdo-field-hint">Leave empty for auto</span>
            </div>
          </div>
        </div>

        {/* FDO Script Input */}
        <div className="fdo-script-section">
          <div className="fdo-script-header">
            <label htmlFor="fdoScript">FDO Script</label>
            <button
              type="button"
              className="btn btn-outline btn-small fdo-expand-btn"
              onClick={() => setIsEditorMode(true)}
              title="Open Editor Mode"
            >
              <span className="fdo-expand-icon">⛶</span>
              Editor Mode
            </button>
          </div>
          <textarea
            id="fdoScript"
            value={fdoScript}
            onChange={(e) => setFdoScript(e.target.value)}
            placeholder={`Enter FDO script here...

Example:
man_create_window(
  "Test Window",
  100, 100, 400, 300,
  WINDOW_STYLE_NORMAL
)
man_add_text(
  "Hello from FDO Workbench!",
  10, 10
)`}
            className="fdo-script-textarea"
            spellCheck={false}
          />
          <div className="fdo-script-stats">
            {fdoScript.length > 0 && (
              <span>{fdoScript.length.toLocaleString()} characters</span>
            )}
          </div>
        </div>

        {/* Actions */}
        <div className="fdo-actions">
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleSendFdo}
            disabled={sending || !selectedScreenname || !fdoScript.trim()}
          >
            {sending ? 'Sending...' : 'Send to Client'}
          </button>
          <button
            type="button"
            className="btn btn-outline"
            onClick={handleClear}
            disabled={sending}
          >
            Clear
          </button>
        </div>

        {/* Result */}
        {renderResult()}
      </div>
    </div>
  );
};

export default FdoWorkbench;

