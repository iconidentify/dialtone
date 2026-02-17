import { useState, useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { authAPI, initializeAuth, getAuthCookieToken } from './services/api';
import { User, AppState } from './types';

// Pages
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import AdminDashboard from './pages/AdminDashboard';
import CallbackPage from './pages/CallbackPage';
import ClientSetupPage from './pages/ClientSetupPage';
import SendFilePage from './pages/SendFilePage';

// Components
import LoadingSpinner from './components/LoadingSpinner';
import ErrorMessage from './components/ErrorMessage';
import TosModal from './components/TosModal';

const TOS_SESSION_KEY = 'dialtone_tos_shown';

function App() {
  const [appState, setAppState] = useState<AppState>({
    user: null,
    isAuthenticated: false,
    isLoading: true,
    error: null,
  });
  const [showTosModal, setShowTosModal] = useState(false);

  // Initialize authentication on app start
  useEffect(() => {
    const initAuth = async () => {
      // Don't try to authenticate if we're on the callback page
      // The callback page will handle setting the token
      if (window.location.pathname === '/auth/callback') {
        setAppState(prev => ({ ...prev, isLoading: false }));
        return;
      }

      try {
        initializeAuth();

        // Only try to get current user if we have a token
        const token = localStorage.getItem('dialtone_auth_token') || getAuthCookieToken();
        if (token) {
          const user = await authAPI.getCurrentUser();
          setAppState({
            user,
            isAuthenticated: true,
            isLoading: false,
            error: null,
          });

          // Don't show TOS on page refresh - only on fresh login
          // sessionStorage tracks if TOS was shown this browser session
        } else {
          // No token available
          setAppState({
            user: null,
            isAuthenticated: false,
            isLoading: false,
            error: null,
          });
        }
      } catch (error) {
        // Token exists but is invalid or expired
        setAppState({
          user: null,
          isAuthenticated: false,
          isLoading: false,
          error: null,
        });
      }
    };

    initAuth();
  }, []);

  // Handle login success (from callback) - this is a FRESH login
  const handleLoginSuccess = (user: User) => {
    setAppState({
      user,
      isAuthenticated: true,
      isLoading: false,
      error: null,
    });

    // Show TOS modal on fresh login only
    // Check sessionStorage to see if already shown this session
    if (!sessionStorage.getItem(TOS_SESSION_KEY)) {
      setShowTosModal(true);
    }
  };

  // Handle TOS acceptance
  const handleTosAccept = () => {
    setShowTosModal(false);
    // Mark TOS as shown for this browser session
    sessionStorage.setItem(TOS_SESSION_KEY, 'true');
  };

  // Handle logout
  const handleLogout = async () => {
    try {
      await authAPI.logout();
    } catch (error) {
      console.error('Logout error:', error);
    } finally {
      setAppState({
        user: null,
        isAuthenticated: false,
        isLoading: false,
        error: null,
      });
      // Clear TOS session flag so it shows again on next login
      sessionStorage.removeItem(TOS_SESSION_KEY);
    }
  };

  // Handle authentication errors
  const handleAuthError = (error: string) => {
    setAppState(prev => ({
      ...prev,
      error,
      isLoading: false,
    }));
  };

  // Update user data (for screenname changes)
  const updateUser = (updatedUser: User) => {
    setAppState(prev => ({
      ...prev,
      user: updatedUser,
    }));
  };

  // Show loading spinner while initializing
  if (appState.isLoading) {
    return (
      <div className="auth-container">
        <div className="auth-card">
          <LoadingSpinner />
          <p>Loading Dialtone...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="App">
      {appState.error && (
        <ErrorMessage
          message={appState.error}
          onClose={() => setAppState(prev => ({ ...prev, error: null }))}
        />
      )}

      {/* TOS Modal - shown on fresh login only */}
      {showTosModal && appState.isAuthenticated && (
        <TosModal onAccept={handleTosAccept} onDecline={handleLogout} />
      )}

      <Routes>
        {/* Public routes */}
        <Route
          path="/auth/callback"
          element={
            <CallbackPage
              onSuccess={handleLoginSuccess}
              onError={handleAuthError}
            />
          }
        />

        {/* Protected routes */}
        {appState.isAuthenticated ? (
          <>
            <Route
              path="/dashboard"
              element={
                <DashboardPage
                  user={appState.user!}
                  onUserUpdate={updateUser}
                  onLogout={handleLogout}
                />
              }
            />
            <Route
              path="/setup"
              element={
                <ClientSetupPage
                  user={appState.user!}
                  onLogout={handleLogout}
                />
              }
            />
            <Route
              path="/send"
              element={
                <SendFilePage
                  user={appState.user!}
                  onLogout={handleLogout}
                />
              }
            />
            <Route
              path="/admin"
              element={
                appState.user?.isAdmin ? (
                  <AdminDashboard
                    user={appState.user!}
                    onLogout={handleLogout}
                  />
                ) : (
                  <Navigate to="/dashboard" replace />
                )
              }
            />
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </>
        ) : (
          <>
            <Route
              path="/"
              element={
                <LoginPage
                  onError={handleAuthError}
                />
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </>
        )}
      </Routes>
    </div>
  );
}

export default App;
