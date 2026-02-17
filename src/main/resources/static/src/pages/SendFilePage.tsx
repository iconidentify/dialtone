import { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { User, ConnectedScreennameInfo, TransferState } from '../types';
import { transferAPI } from '../services/api';
import MainLayout from '../components/layout/MainLayout';

interface SendFilePageProps {
  user: User;
  onLogout: () => void;
}

const SendFilePage = ({ user, onLogout }: SendFilePageProps) => {
  // State
  const [connectedScreennames, setConnectedScreennames] = useState<ConnectedScreennameInfo[]>([]);
  const [selectedScreenname, setSelectedScreenname] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [maxFileSizeMb, setMaxFileSizeMb] = useState<number>(100);
  const [isLoadingScreennames, setIsLoadingScreennames] = useState(true);
  const [isDragOver, setIsDragOver] = useState(false);
  const [transferState, setTransferState] = useState<TransferState>({
    status: 'idle',
    progress: 0,
    error: null,
    transferId: null,
  });

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Load connected screennames and config on mount
  useEffect(() => {
    const loadData = async () => {
      try {
        setIsLoadingScreennames(true);
        const [screennamesResponse, configResponse] = await Promise.all([
          transferAPI.getConnectedScreennames(),
          transferAPI.getConfig(),
        ]);
        setConnectedScreennames(screennamesResponse.screennames);
        setMaxFileSizeMb(configResponse.maxFileSizeMb);
      } catch (error) {
        console.error('Failed to load transfer data:', error);
      } finally {
        setIsLoadingScreennames(false);
      }
    };
    loadData();
  }, []);

  // Format file size for display
  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  // Handle file selection
  const handleFileSelect = useCallback((file: File) => {
    const maxBytes = maxFileSizeMb * 1024 * 1024;
    if (file.size > maxBytes) {
      setTransferState({
        status: 'error',
        progress: 0,
        error: `File size (${formatFileSize(file.size)}) exceeds maximum allowed size (${maxFileSizeMb} MB)`,
        transferId: null,
      });
      return;
    }
    setSelectedFile(file);
    setTransferState({ status: 'idle', progress: 0, error: null, transferId: null });
  }, [maxFileSizeMb]);

  // Drag and drop handlers
  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);

    const files = e.dataTransfer.files;
    if (files.length > 0) {
      handleFileSelect(files[0]);
    }
  }, [handleFileSelect]);

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      handleFileSelect(files[0]);
    }
  };

  const handleDropZoneClick = () => {
    fileInputRef.current?.click();
  };

  // Remove selected file
  const handleRemoveFile = () => {
    setSelectedFile(null);
    setTransferState({ status: 'idle', progress: 0, error: null, transferId: null });
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // Handle file upload
  const handleSendFile = async () => {
    if (!selectedFile || !selectedScreenname) return;

    setTransferState({ status: 'uploading', progress: 0, error: null, transferId: null });

    try {
      const response = await transferAPI.uploadFile(
        selectedFile,
        selectedScreenname,
        (percent) => {
          setTransferState((prev) => ({ ...prev, progress: percent }));
        }
      );

      setTransferState({
        status: 'success',
        progress: 100,
        error: null,
        transferId: response.transferId,
      });
    } catch (error: any) {
      setTransferState({
        status: 'error',
        progress: 0,
        error: error.message || 'Transfer failed',
        transferId: null,
      });
    }
  };

  // Reset for another transfer
  const handleSendAnother = () => {
    setSelectedFile(null);
    setSelectedScreenname(null);
    setTransferState({ status: 'idle', progress: 0, error: null, transferId: null });
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // Refresh connected screennames
  const handleRefreshScreennames = async () => {
    try {
      setIsLoadingScreennames(true);
      const response = await transferAPI.getConnectedScreennames();
      setConnectedScreennames(response.screennames);
      // Clear selection if selected screenname is no longer connected
      if (selectedScreenname && !response.screennames.find(s => s.screenname === selectedScreenname)) {
        setSelectedScreenname(null);
      }
    } catch (error) {
      console.error('Failed to refresh screennames:', error);
    } finally {
      setIsLoadingScreennames(false);
    }
  };

  const canSend = selectedFile && selectedScreenname && transferState.status === 'idle';
  const isTransferring = transferState.status === 'uploading' || transferState.status === 'transferring';

  return (
    <MainLayout user={user} onLogout={onLogout}>
      <div className="send-page">
        <div className="send-page-header">
          <h1>Send File</h1>
          <p className="send-page-subtitle">
            Transfer files to your connected AOL devices
          </p>
        </div>

        {/* Success State */}
        {transferState.status === 'success' && (
          <div className="send-success-card">
            <div className="send-success-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                <polyline points="22 4 12 14.01 9 11.01" />
              </svg>
            </div>
            <h2>File Delivered Successfully</h2>
            <p className="send-success-details">
              <strong>{selectedFile?.name}</strong> sent to <strong>{selectedScreenname}</strong>
            </p>
            <div className="send-success-actions">
              <button className="btn btn-primary" onClick={handleSendAnother}>
                Send Another File
              </button>
              <Link to="/dashboard" className="btn btn-secondary">
                Back to Dashboard
              </Link>
            </div>
          </div>
        )}

        {/* Main Transfer UI */}
        {transferState.status !== 'success' && (
          <>
            {/* Drop Zone */}
            <div
              className={`drop-zone ${isDragOver ? 'drop-zone--dragover' : ''} ${selectedFile ? 'drop-zone--has-file' : ''} ${isTransferring ? 'drop-zone--disabled' : ''}`}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={!isTransferring && !selectedFile ? handleDropZoneClick : undefined}
            >
              <input
                ref={fileInputRef}
                type="file"
                className="drop-zone-input"
                onChange={handleFileInputChange}
                disabled={isTransferring}
              />

              {!selectedFile ? (
                <div className="drop-zone-content">
                  <div className="drop-zone-icon">
                    <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                      <polyline points="17 8 12 3 7 8" />
                      <line x1="12" y1="3" x2="12" y2="15" />
                    </svg>
                  </div>
                  <p className="drop-zone-text">
                    {isDragOver ? 'Release to upload' : 'Drop files here or click to browse'}
                  </p>
                  <p className="drop-zone-hint">Maximum file size: {maxFileSizeMb} MB</p>
                </div>
              ) : (
                <div className="file-preview">
                  <div className="file-preview-icon">
                    <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                    </svg>
                  </div>
                  <div className="file-preview-info">
                    <p className="file-preview-name">{selectedFile.name}</p>
                    <p className="file-preview-size">{formatFileSize(selectedFile.size)}</p>
                  </div>
                  {!isTransferring && (
                    <button
                      className="file-preview-remove"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRemoveFile();
                      }}
                      aria-label="Remove file"
                    >
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <line x1="18" y1="6" x2="6" y2="18" />
                        <line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* Error Message */}
            {transferState.error && (
              <div className="send-error">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
                <span>{transferState.error}</span>
              </div>
            )}

            {/* Recipient Selection */}
            {selectedFile && (
              <div className="recipient-section">
                <div className="recipient-header">
                  <h2>Select Recipient</h2>
                  <button
                    className="btn btn-text"
                    onClick={handleRefreshScreennames}
                    disabled={isLoadingScreennames || isTransferring}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <polyline points="23 4 23 10 17 10" />
                      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
                    </svg>
                    Refresh
                  </button>
                </div>

                {isLoadingScreennames ? (
                  <div className="recipient-loading">
                    <div className="loading-spinner"></div>
                    <span>Loading connected screennames...</span>
                  </div>
                ) : connectedScreennames.length === 0 ? (
                  <div className="recipient-empty">
                    <p>No screennames are currently connected.</p>
                    <p className="recipient-empty-hint">
                      Connect to Dialtone from your AOL client to receive files.
                      <Link to="/setup"> View setup guide</Link>
                    </p>
                  </div>
                ) : (
                  <div className="recipient-cards">
                    {connectedScreennames.map((sn) => (
                      <button
                        key={sn.screenname}
                        className={`recipient-card ${selectedScreenname === sn.screenname ? 'recipient-card--selected' : ''}`}
                        onClick={() => !isTransferring && setSelectedScreenname(sn.screenname)}
                        disabled={isTransferring}
                      >
                        <div className="recipient-card-status">
                          <span className="status-dot status-dot--online"></span>
                          <span>Online</span>
                        </div>
                        <p className="recipient-card-name">{sn.screenname}</p>
                        <p className="recipient-card-platform">
                          {sn.platform === 'mac' ? 'Mac' : sn.platform === 'windows' ? 'Windows' : 'Unknown'}
                        </p>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Progress Bar */}
            {isTransferring && (
              <div className="transfer-progress">
                <div className="transfer-progress-info">
                  <span>Uploading to server...</span>
                  <span>{transferState.progress}%</span>
                </div>
                <div className="progress-bar">
                  <div
                    className="progress-bar-fill"
                    style={{ width: `${transferState.progress}%` }}
                  ></div>
                </div>
              </div>
            )}

            {/* Send Button */}
            <div className="send-actions">
              <button
                className="btn btn-primary btn-large send-button"
                onClick={handleSendFile}
                disabled={!canSend || isTransferring}
              >
                {isTransferring ? (
                  <>
                    <div className="loading-spinner loading-spinner--small"></div>
                    Sending...
                  </>
                ) : (
                  'Send File'
                )}
              </button>
            </div>
          </>
        )}
      </div>
    </MainLayout>
  );
};

export default SendFilePage;
