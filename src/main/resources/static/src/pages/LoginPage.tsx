import { useState, useEffect } from 'react';
import { authAPI } from '../services/api';
import { AuthProvidersResponse } from '../types';

interface LoginPageProps {
  onError: (error: string) => void;
}

interface Screenshot {
  src: string;
  alt: string;
  caption: string;
  description: string;
}

const screenshots: Screenshot[] = [
  {
    src: '/MACOS.jpg',
    alt: 'Mac OS 7.6.1 running AOL 3.0.1 connected to Dialtone',
    caption: 'Mac OS 7.6.1 - AOL 3.0.1',
    description: 'Mac OS 7.6.1 running America Online 3.0.1, connected to the Dialtone research server. Shows the Dialtone Lobby chat room and an instant message conversation.'
  },
  {
    src: '/WIN98.jpg',
    alt: 'Windows 98 running AOL 3.0 connected to Dialtone',
    caption: 'Windows 98 - AOL 3.0',
    description: 'Windows 98 running America Online 3.0, connected to the Dialtone research server. Shows the classic Windows AOL experience.'
  }
];

const LoginPage = ({ onError }: LoginPageProps) => {
  const [showTos, setShowTos] = useState(false);
  const [showLightbox, setShowLightbox] = useState(false);
  const [currentSlide, setCurrentSlide] = useState(0);
  const [tosContent, setTosContent] = useState<string>('');
  const [tosLoading, setTosLoading] = useState(false);
  const [authProviders, setAuthProviders] = useState<AuthProvidersResponse>({ xEnabled: true, discordEnabled: false, emailEnabled: false });

  // Email login state
  const [showEmailForm, setShowEmailForm] = useState(false);
  const [email, setEmail] = useState('');
  const [emailSending, setEmailSending] = useState(false);
  const [emailSent, setEmailSent] = useState(false);

  // Fetch available auth providers on mount
  useEffect(() => {
    authAPI.getAuthProviders().then(setAuthProviders).catch(() => {
      // Default to X only on error
      setAuthProviders({ xEnabled: true, discordEnabled: false, emailEnabled: false });
    });
  }, []);

  const handleXLogin = async () => {
    try {
      await authAPI.initiateXLogin();
    } catch (error) {
      onError('Failed to initiate X login. Please try again.');
    }
  };

  const handleDiscordLogin = async () => {
    try {
      await authAPI.initiateDiscordLogin();
    } catch (error) {
      onError('Failed to initiate Discord login. Please try again.');
    }
  };

  const handleEmailLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) {
      onError('Please enter your email address.');
      return;
    }

    setEmailSending(true);
    try {
      await authAPI.initiateEmailLogin(email.trim());
      setEmailSent(true);
    } catch (error: any) {
      onError(error.message || 'Failed to send magic link. Please try again.');
    } finally {
      setEmailSending(false);
    }
  };

  const resetEmailForm = () => {
    setShowEmailForm(false);
    setEmail('');
    setEmailSent(false);
    setEmailSending(false);
  };

  const handleShowTos = async () => {
    setShowTos(true);
    if (!tosContent) {
      setTosLoading(true);
      try {
        const response = await fetch('/TOS.txt');
        if (response.ok) {
          const text = await response.text();
          setTosContent(text);
        } else {
          setTosContent('Terms of Service could not be loaded.');
        }
      } catch (error) {
        setTosContent('Terms of Service could not be loaded.');
      } finally {
        setTosLoading(false);
      }
    }
  };

  // Handle escape key to close modals, arrow keys for carousel
  useEffect(() => {
    const handleKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (showTos) setShowTos(false);
        if (showLightbox) setShowLightbox(false);
      }
      if (showLightbox) {
        if (e.key === 'ArrowLeft') {
          setCurrentSlide((prev) => (prev === 0 ? screenshots.length - 1 : prev - 1));
        } else if (e.key === 'ArrowRight') {
          setCurrentSlide((prev) => (prev === screenshots.length - 1 ? 0 : prev + 1));
        }
      }
    };
    document.addEventListener('keydown', handleKeydown);
    return () => document.removeEventListener('keydown', handleKeydown);
  }, [showTos, showLightbox]);

  const nextSlide = () => {
    setCurrentSlide((prev) => (prev === screenshots.length - 1 ? 0 : prev + 1));
  };

  const prevSlide = () => {
    setCurrentSlide((prev) => (prev === 0 ? screenshots.length - 1 : prev - 1));
  };

  const goToSlide = (index: number) => {
    setCurrentSlide(index);
  };

  return (
    <div className="landing-container">
      {/* Main Two-Column Layout */}
      <div className="landing-grid">

        {/* Left Column: Hero & Info */}
        <div className="landing-hero">
          <div className="hero-header">
            <img src="/favicon.svg" alt="Dialtone" className="hero-logo" />
            <div className="hero-brand">
              <h1 className="hero-title">Dialtone</h1>
              <span className="hero-tagline">AOL 3.0 Protocol Server</span>
            </div>
          </div>

          <h2 className="hero-headline">
            Revive AOL 3.0 on Modern Hardware
          </h2>
          <p className="hero-description">
            Connect your retro America Online clients to a reverse-engineered P3 protocol server.
            Chat with Grok AI, relive the '90s, no dial-up required.
          </p>

          {/* How It Works Section */}
          <div className="how-it-works">
            <h3 className="section-title">Get Connected in 3 Steps</h3>
            <div className="steps-list">
              <div className="step-item">
                <div className="step-number">1</div>
                <div className="step-content">
                  <h4>Create Your Account</h4>
                  <p>Sign in with X, Discord, or email. Takes 30 seconds.</p>
                </div>
              </div>
              <div className="step-item">
                <div className="step-number">2</div>
                <div className="step-content">
                  <h4>Claim Your Screennames</h4>
                  <p>Create up to 3 classic AOL screennames. They work across all supported clients.</p>
                </div>
              </div>
              <div className="step-item">
                <div className="step-number">3</div>
                <div className="step-content">
                  <h4>Connect and Chat</h4>
                  <p>Launch the browser VM instantly, or configure your native AOL client.</p>
                </div>
              </div>
            </div>
          </div>

          {/* Supported Platforms */}
          <div className="platforms-section">
            <h3 className="section-title">Supported Clients</h3>
            <p className="section-subtitle">Your screenname works on all of these</p>
            <div className="platforms-grid">
              <div className="platform-card">
                <div className="platform-icon">
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M3 4h18v12H3V4zm0 14h18v2H3v-2zm4-8h2v2H7v-2zm4 0h2v2h-2v-2zm4 0h2v2h-2v-2z"/>
                  </svg>
                </div>
                <div className="platform-info">
                  <span className="platform-name">Windows 95/98</span>
                  <span className="platform-version">AOL 3.0 32-bit</span>
                </div>
              </div>
              <div className="platform-card">
                <div className="platform-icon">
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M3 4h18v12H3V4zm0 14h18v2H3v-2zm2-10h4v1H5V8zm0 2h4v1H5v-1zm6-2h4v1h-4V8zm0 2h4v1h-4v-1z"/>
                  </svg>
                </div>
                <div className="platform-info">
                  <span className="platform-name">Windows 3.11</span>
                  <span className="platform-version">AOL 3.0 16-bit</span>
                </div>
              </div>
              <div className="platform-card">
                <div className="platform-icon">
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M4 4h16v14H4V4zm2 2v10h12V6H6zm3 2h6v1H9V8zm0 2h6v1H9v-1zm-3 8h12v2H6v-2z"/>
                  </svg>
                </div>
                <div className="platform-info">
                  <span className="platform-name">Mac 68k/PPC</span>
                  <span className="platform-version">AOL 3.0.1</span>
                </div>
              </div>
              <div className="platform-card platform-featured">
                <div className="platform-icon">
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
                  </svg>
                </div>
                <div className="platform-info">
                  <span className="platform-name">Browser VM</span>
                  <span className="platform-version">No download</span>
                </div>
                <span className="platform-badge">Instant</span>
              </div>
            </div>
          </div>

          {/* Screenshots - Desktop Only */}
          <div className="screenshots-desktop">
            <div className="screenshot-grid">
              {screenshots.map((shot, index) => (
                <div
                  key={shot.src}
                  className="screenshot-item"
                  onClick={() => { setCurrentSlide(index); setShowLightbox(true); }}
                >
                  <img src={shot.src} alt={shot.alt} />
                  <span className="screenshot-label">{shot.caption}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Right Column: Auth & Browser Connect */}
        <div className="landing-auth">
          {/* Browser Connect Feature Card - Hero Position */}
          <a
            href="/mac_connect"
            className="browser-connect-card"
            target="_blank"
            rel="noopener noreferrer"
          >
            <div className="browser-connect-visual">
              <div className="mac-frame">
                <div className="mac-screen">
                  <div className="mac-menubar">
                    <span className="mac-apple"></span>
                    <span>File</span>
                    <span>Edit</span>
                    <span>Special</span>
                  </div>
                  <div className="mac-content">
                    <div className="aol-window">
                      <div className="aol-titlebar">America Online</div>
                      <div className="aol-body">
                        <div className="aol-text">Welcome!</div>
                      </div>
                    </div>
                  </div>
                </div>
                <div className="mac-badge">DIALTONE</div>
              </div>
            </div>
            <div className="browser-connect-content">
              <span className="browser-connect-badge">No Download Required</span>
              <h2 className="browser-connect-headline">
                Try AOL 3.0 Right Now
              </h2>
              <p className="browser-connect-description">
                Full Mac System 7 emulation with real TCP/IP networking.
                Log in as Guest and use your Dialtone screenname.
              </p>
              <div className="browser-connect-cta">
                <span className="cta-text">Launch Mac System 7</span>
                <svg className="cta-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M5 12h14M12 5l7 7-7 7"/>
                </svg>
              </div>
            </div>
          </a>

          {/* Auth Card */}
          <div className="auth-card">
            <h3 className="auth-card-title">Create Your Screennames</h3>
            <p className="auth-card-subtitle">
              Sign in to create up to 3 screennames. Use them on any supported client or the browser VM.
            </p>

            <div className="auth-buttons">
              <button onClick={handleXLogin} className="btn btn-x auth-cta" type="button">
                <svg viewBox="0 0 24 24" fill="currentColor" className="auth-btn-icon">
                  <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
                </svg>
                Sign in with X
              </button>

              {authProviders.discordEnabled && (
                <button onClick={handleDiscordLogin} className="btn btn-discord auth-cta" type="button">
                  <svg viewBox="0 0 24 24" fill="currentColor" className="auth-btn-icon">
                    <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.947 2.418-2.157 2.418z"/>
                  </svg>
                  Sign in with Discord
                </button>
              )}

              {/* Email Login Section */}
              {authProviders.emailEnabled && (
                <>
                  {!showEmailForm && !emailSent ? (
                    <button
                      onClick={() => setShowEmailForm(true)}
                      className="btn btn-email auth-cta"
                      type="button"
                    >
                      <svg viewBox="0 0 24 24" fill="currentColor" className="auth-btn-icon">
                        <path d="M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z"/>
                      </svg>
                      Continue with Email
                    </button>
                  ) : emailSent ? (
                    <div className="email-sent-confirmation">
                      <div className="email-sent-icon">
                        <svg viewBox="0 0 24 24" fill="currentColor">
                          <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                        </svg>
                      </div>
                      <h3>Check your email</h3>
                      <p>We sent a sign-in link to <strong>{email}</strong></p>
                      <p className="email-sent-hint">Click the link in your email to sign in. The link expires in 15 minutes.</p>
                      <button
                        type="button"
                        className="btn btn-outline btn-small"
                        onClick={resetEmailForm}
                      >
                        Use a different email
                      </button>
                    </div>
                  ) : (
                    <form onSubmit={handleEmailLogin} className="email-login-form">
                      <div className="email-input-group">
                        <input
                          type="email"
                          value={email}
                          onChange={(e) => setEmail(e.target.value)}
                          placeholder="Enter your email address"
                          className="email-input"
                          disabled={emailSending}
                          autoFocus
                          required
                        />
                        <button
                          type="submit"
                          className="btn btn-primary btn-email-submit"
                          disabled={emailSending || !email.trim()}
                        >
                          {emailSending ? 'Sending...' : 'Send Link'}
                        </button>
                      </div>
                      <button
                        type="button"
                        className="email-cancel-btn"
                        onClick={resetEmailForm}
                      >
                        Cancel
                      </button>
                    </form>
                  )}
                </>
              )}
            </div>
          </div>

          {/* Screenshot Carousel - Mobile Only */}
          <div className="screenshot-carousel-mobile">
            <div className="carousel-viewport" onClick={() => setShowLightbox(true)}>
              <div
                className="carousel-track"
                style={{ transform: `translateX(-${currentSlide * 100}%)` }}
              >
                {screenshots.map((shot) => (
                  <div key={shot.src} className="carousel-slide">
                    <img
                      src={shot.src}
                      alt={shot.alt}
                      className="screenshot-thumb"
                    />
                    <div className="screenshot-overlay">
                      <span className="screenshot-caption">{shot.caption}</span>
                      <span className="screenshot-hint">Click to enlarge</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Carousel Navigation */}
            <div className="carousel-nav">
              <button
                type="button"
                className="carousel-btn carousel-prev"
                onClick={(e) => { e.stopPropagation(); prevSlide(); }}
                aria-label="Previous screenshot"
              >
                &lsaquo;
              </button>
              <div className="carousel-dots">
                {screenshots.map((_, index) => (
                  <button
                    key={index}
                    type="button"
                    className={`carousel-dot ${index === currentSlide ? 'active' : ''}`}
                    onClick={(e) => { e.stopPropagation(); goToSlide(index); }}
                    aria-label={`Go to screenshot ${index + 1}`}
                  />
                ))}
              </div>
              <button
                type="button"
                className="carousel-btn carousel-next"
                onClick={(e) => { e.stopPropagation(); nextSlide(); }}
                aria-label="Next screenshot"
              >
                &rsaquo;
              </button>
            </div>
          </div>

          {/* What is P3 - Collapsed on Desktop */}
          <details className="p3-explainer">
            <summary>What is P3?</summary>
            <div className="p3-content">
              <p>
                <strong>AOL's Legacy Protocol</strong><br />
                P3 powered America Online throughout the 1990s, enabling millions to chat, email, and explore the early internet.
              </p>
              <p>
                <strong>Reverse-Engineered Server</strong><br />
                Dialtone is a fan-built P3 server, painstakingly recreated from AOL 3.0 clients for Windows and Mac.
              </p>
              <p>
                <strong>Powered by Grok AI</strong><br />
                Chat with an AI that speaks fluent '90s. Research and preservation meet modern technology.
              </p>
            </div>
          </details>

          {/* Footer */}
          <div className="landing-footer">
            <button
              type="button"
              className="tos-link-btn"
              onClick={handleShowTos}
            >
              Terms of Service
            </button>

            <div className="auth-social-links">
              <a
                href="https://discord.gg/gXzESggyQx"
                target="_blank"
                rel="noopener noreferrer"
                className="social-link"
                title="Join Dialtone Discord"
              >
                <svg viewBox="0 0 24 24" fill="currentColor" className="social-icon">
                  <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.947 2.418-2.157 2.418z"/>
                </svg>
                <span>Discord</span>
              </a>
              <a
                href="https://x.com/SiliconForested"
                target="_blank"
                rel="noopener noreferrer"
                className="social-link"
                title="Follow on X"
              >
                <svg viewBox="0 0 24 24" fill="currentColor" className="social-icon">
                  <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
                </svg>
                <span>@SiliconForested</span>
              </a>
            </div>
          </div>
        </div>
      </div>

      {/* Screenshot Lightbox with Carousel */}
      {showLightbox && (
        <div className="lightbox-overlay" onClick={() => setShowLightbox(false)}>
          <div className="lightbox-content lightbox-carousel" onClick={(e) => e.stopPropagation()}>
            <button
              type="button"
              className="lightbox-close"
              onClick={() => setShowLightbox(false)}
              aria-label="Close"
            >
              x
            </button>

            <button
              type="button"
              className="lightbox-nav lightbox-prev"
              onClick={prevSlide}
              aria-label="Previous screenshot"
            >
              &lsaquo;
            </button>

            <div className="lightbox-viewport">
              <div
                className="lightbox-track"
                style={{ transform: `translateX(-${currentSlide * 100}%)` }}
              >
                {screenshots.map((shot) => (
                  <div key={shot.src} className="lightbox-slide">
                    <img
                      src={shot.src}
                      alt={shot.alt}
                      className="lightbox-image"
                    />
                  </div>
                ))}
              </div>
            </div>

            <button
              type="button"
              className="lightbox-nav lightbox-next"
              onClick={nextSlide}
              aria-label="Next screenshot"
            >
              &rsaquo;
            </button>

            <div className="lightbox-footer">
              <p className="lightbox-caption">{screenshots[currentSlide].description}</p>
              <div className="lightbox-dots">
                {screenshots.map((_, index) => (
                  <button
                    key={index}
                    type="button"
                    className={`carousel-dot ${index === currentSlide ? 'active' : ''}`}
                    onClick={() => goToSlide(index)}
                    aria-label={`Go to screenshot ${index + 1}`}
                  />
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* TOS Modal */}
      {showTos && (
        <div className="tos-modal-overlay" onClick={() => setShowTos(false)}>
          <div className="tos-modal" onClick={(e) => e.stopPropagation()}>
            <div className="tos-modal-header">
              <h2>Terms of Service</h2>
            </div>
            <div className="tos-modal-content">
              {tosLoading ? (
                <p className="tos-loading">Loading terms...</p>
              ) : (
                <pre className="tos-text">{tosContent}</pre>
              )}
            </div>
            <div className="tos-modal-footer">
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setShowTos(false)}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default LoginPage;
