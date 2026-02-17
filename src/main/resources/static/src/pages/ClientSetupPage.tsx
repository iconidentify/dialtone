import { useState } from 'react';
import MainLayout from '../components/layout/MainLayout';
import { User } from '../types';

interface ClientSetupPageProps {
  user: User;
  onLogout: () => void;
}

type ConfigTab = 'download' | 'manual';

const ClientSetupPage = ({ user, onLogout }: ClientSetupPageProps) => {
  const [configTab, setConfigTab] = useState<ConfigTab>('download');

  const handleDownload = (filename: string) => {
    const link = document.createElement('a');
    link.href = `/downloads/${filename}`;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <MainLayout
      user={user}
      onLogout={onLogout}
      headerTitle="Client Setup"
      headerSubtitle="Configure your AOL 3.0 client to connect to Dialtone."
    >
      {/* Connection Details */}
      <section className="dt-panel setup-section">
        <div className="dt-panel-header">
          <h2>Connection Details</h2>
        </div>
        <div className="setup-connection-info">
          <div className="connection-detail">
            <span className="connection-label">Server</span>
            <code className="connection-value">dialtone.live</code>
          </div>
          <div className="connection-detail">
            <span className="connection-label">Port</span>
            <code className="connection-value">5190</code>
          </div>
        </div>
      </section>

      {/* How to Sign On */}
      <section className="dt-panel setup-section">
        <div className="dt-panel-header">
          <h2>How to Sign On</h2>
        </div>
        <div className="setup-instructions">
          <ol className="setup-steps">
            <li>
              <strong>Create a Screenname</strong>
              <p>Use the <a href="/dashboard">Screenname Manager</a> to create your screenname and password (1-8 characters).</p>
            </li>
            <li>
              <strong>Download Connection File</strong>
              <p>Download and install the appropriate connection file for your platform (see below).</p>
            </li>
            <li>
              <strong>Launch AOL 3.0</strong>
              <p>Open your AOL client on Windows or Mac.</p>
            </li>
            <li>
              <strong>Select "Guest"</strong>
              <p>From the screenname dropdown, select <code>Guest</code>. This is required for logging in with your Dialtone screenname.</p>
            </li>
            <li>
              <strong>Sign On</strong>
              <p>Enter the screenname and password you created in the Screenname Manager, then click Sign On.</p>
            </li>
          </ol>
        </div>
      </section>

      {/* Browser VM - Instant Access */}
      <section className="dt-panel setup-section browser-vm-section">
        <div className="dt-panel-header">
          <h2>Instant Access: Browser VM</h2>
          <span className="section-badge">No Download</span>
        </div>
        <div className="browser-vm-content">
          <p className="setup-intro">
            Skip the setup entirely. Our browser-based Mac System 7 environment comes pre-configured
            with AOL 3.0 - just click and connect.
          </p>
          <div className="browser-vm-steps">
            <div className="vm-step">
              <span className="vm-step-number">1</span>
              <div className="vm-step-text">
                <strong>Launch the Environment</strong>
                <p>Click the button below to open Mac System 7 in your browser.</p>
              </div>
            </div>
            <div className="vm-step">
              <span className="vm-step-number">2</span>
              <div className="vm-step-text">
                <strong>Select Guest</strong>
                <p>When AOL launches, choose <code>Guest</code> from the screenname dropdown.</p>
              </div>
            </div>
            <div className="vm-step">
              <span className="vm-step-number">3</span>
              <div className="vm-step-text">
                <strong>Enter Your Credentials</strong>
                <p>Use the screenname and password you created in the Screenname Manager.</p>
              </div>
            </div>
          </div>
          <a
            href="/mac_connect"
            className="browser-vm-launch-btn"
            target="_blank"
            rel="noopener noreferrer"
          >
            <svg viewBox="0 0 24 24" fill="currentColor" className="vm-btn-icon">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
            </svg>
            Launch Mac System 7
          </a>
        </div>
      </section>

      {/* Connection Files with Tabs */}
      <section className="dt-panel setup-section">
        <div className="dt-panel-header">
          <h2>Connection Files</h2>
          <div className="config-tabs">
            <button
              type="button"
              className={`config-tab ${configTab === 'download' ? 'active' : ''}`}
              onClick={() => setConfigTab('download')}
            >
              Download
            </button>
            <button
              type="button"
              className={`config-tab ${configTab === 'manual' ? 'active' : ''}`}
              onClick={() => setConfigTab('manual')}
            >
              Manual Configuration
            </button>
          </div>
        </div>

        {configTab === 'download' && (
          <div className="config-content">
            <p className="setup-intro">
              Download the appropriate connection file for your platform. These files configure your AOL client to connect to the Dialtone research server.
            </p>
            <div className="setup-downloads">
              <div className="download-card">
                <div className="download-icon">🪟</div>
                <h3>Windows</h3>
                <p className="download-filename">TCP.CCL</p>
                <p className="download-path">
                  Place in: <code>C:\aol30\ccl\</code>
                </p>
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={() => handleDownload('TCP.CCL')}
                >
                  Download for Windows
                </button>
              </div>

              <div className="download-card">
                <div className="download-icon">🍎</div>
                <h3>Macintosh</h3>
                <p className="download-filename">TCP</p>
                <p className="download-path">
                  Place in: <code>AOL 3.0:Online Files</code>
                </p>
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={() => handleDownload('TCP')}
                >
                  Download for Mac
                </button>
              </div>
            </div>
          </div>
        )}

        {configTab === 'manual' && (
          <div className="config-content">
            <p className="setup-intro">
              If you prefer to configure manually, or if you have an existing connection file you want to modify:
            </p>
            <ol className="setup-steps">
              <li>
                <strong>Locate Your Connection File</strong>
                <p>
                  <strong>Windows:</strong> <code>C:\aol30\ccl\TCP.CCL</code><br />
                  <strong>Mac:</strong> <code>AOL 3.0:Online Files:TCP</code>
                </p>
              </li>
              <li>
                <strong>Open in Text Editor</strong>
                <p>Use Notepad (Windows) or SimpleText/TextEdit (Mac) to open the file.</p>
              </li>
              <li>
                <strong>Replace the Hostname</strong>
                <p>Find the existing AOL hostname and replace it with <code>dialtone.live</code></p>
              </li>
              <li>
                <strong>Save and Restart</strong>
                <p>Save the file and restart your AOL client.</p>
              </li>
            </ol>
          </div>
        )}
      </section>

      {/* Compatible Clients */}
      <section className="dt-panel setup-section">
        <div className="dt-panel-header">
          <h2>Compatible Client Software</h2>
        </div>
        <p className="setup-intro">
          Dialtone was developed and tested using specific versions of AOL client software. For best compatibility, use one of the following:
        </p>
        <div className="compatibility-list">
          <div className="compatibility-item">
            <span className="compatibility-platform">Windows</span>
            <span className="compatibility-version">America Online v3.0 (32-bit)</span>
            <span className="compatibility-tested">Tested on Windows 98 SE, Windows XP SP3</span>
          </div>
          <div className="compatibility-item">
            <span className="compatibility-platform">Macintosh</span>
            <span className="compatibility-version">America Online v3.0.1</span>
            <span className="compatibility-tested">Tested on Mac OS 7.6.1, 8.1, 8.6, 9</span>
          </div>
        </div>
      </section>

      {/* Community Support */}
      <section className="dt-panel setup-section discord-section">
        <div className="dt-panel-header">
          <h2>Community Support</h2>
        </div>
        <div className="discord-content">
          <div className="discord-info">
            <p className="discord-headline">Join the Dialtone Discord</p>
            <p className="discord-description">
              Connect with fellow retro computing enthusiasts. Get help with setup, 
              share your experiences, and stay updated on Dialtone development.
            </p>
            <ul className="discord-features">
              <li>
                <svg viewBox="0 0 24 24" fill="currentColor" className="feature-icon">
                  <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                </svg>
                <span>Technical support from the community</span>
              </li>
              <li>
                <svg viewBox="0 0 24 24" fill="currentColor" className="feature-icon">
                  <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                </svg>
                <span>Client configuration tips</span>
              </li>
              <li>
                <svg viewBox="0 0 24 24" fill="currentColor" className="feature-icon">
                  <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                </svg>
                <span>Development updates and announcements</span>
              </li>
            </ul>
            <a 
              href="https://discord.gg/gXzESggyQx" 
              target="_blank" 
              rel="noopener noreferrer"
              className="discord-join-btn"
            >
              <svg viewBox="0 0 24 24" fill="currentColor" className="discord-btn-icon">
                <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.947 2.418-2.157 2.418z"/>
              </svg>
              Join Discord Server
            </a>
          </div>
        </div>
      </section>
    </MainLayout>
  );
};

export default ClientSetupPage;
