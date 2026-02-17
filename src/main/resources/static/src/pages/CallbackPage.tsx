import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { User } from '../types';
import { setAuthToken, getAuthCookieToken, authAPI } from '../services/api';
import LoadingSpinner from '../components/LoadingSpinner';

interface CallbackPageProps {
  onSuccess: (user: User) => void;
  onError: (error: string) => void;
}

const CallbackPage = ({ onSuccess, onError }: CallbackPageProps) => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  useEffect(() => {
    const handleCallback = async () => {
      const token = searchParams.get('token');
      const success = searchParams.get('success');
      const error = searchParams.get('error');
      const cookieToken = getAuthCookieToken();

      // Check for authentication error
      if (success === 'false' || error) {
        onError(error || 'Authentication failed');
        return;
      }

      // Check for successful authentication with token
      const resolvedToken = token ?? cookieToken ?? null;
      const loginWasSuccessful = success === 'true' || (!success && resolvedToken);

      if (loginWasSuccessful && resolvedToken) {
        try {
          // Set the auth token
          setAuthToken(resolvedToken);

          // Fetch the full user data including screennames
          const user = await authAPI.getCurrentUser();
          
          onSuccess(user);
          navigate('/dashboard', { replace: true });

        } catch (error) {
          console.error('Token processing error:', error);
          
          // Fallback: decode JWT token to get basic user information
          try {
            const payload = JSON.parse(atob(resolvedToken.split('.')[1]));
            const user: User = {
              userId: payload.userId,
              xUsername: payload.xUsername,
              displayName: payload.displayName,
              screennames: [], // Will be loaded separately
            };
            onSuccess(user);
            navigate('/dashboard', { replace: true });
          } catch (decodeError) {
            console.error('Failed to decode token:', decodeError);
            onError('Failed to process authentication token. Please try again.');
          }
        }
        return;
      }

      // If we get here, callback parameters are invalid
      onError('Invalid authentication callback - missing required parameters');
    };

    handleCallback();
  }, [searchParams, onSuccess, onError, navigate]);

  return (
    <div className="auth-container">
      <div className="auth-card">
        <LoadingSpinner message="Completing authentication..." />
        <p style={{ textAlign: 'center', color: '#718096', marginTop: '1rem' }}>
          Please wait while we sign you in.
        </p>
      </div>
    </div>
  );
};

export default CallbackPage;