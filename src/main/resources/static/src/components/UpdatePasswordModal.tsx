import { useState, useEffect } from 'react';
import { Screenname, UpdatePasswordFormData, PASSWORD_CONSTRAINTS } from '../types';
import Modal from './Modal';
import LoadingSpinner from './LoadingSpinner';

interface UpdatePasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: UpdatePasswordFormData) => Promise<void>;
  screenname: Screenname | null;
  isLoading: boolean;
}

const UpdatePasswordModal = ({
  isOpen,
  onClose,
  onSubmit,
  screenname,
  isLoading,
}: UpdatePasswordModalProps) => {
  const [formData, setFormData] = useState<UpdatePasswordFormData>({
    password: '',
    confirmPassword: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Reset form when modal opens/closes
  useEffect(() => {
    if (isOpen) {
      setFormData({ password: '', confirmPassword: '' });
      setErrors({});
    }
  }, [isOpen]);

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    // Validate password
    if (!formData.password) {
      newErrors.password = 'Password is required';
    } else if (formData.password.length < PASSWORD_CONSTRAINTS.MIN_LENGTH) {
      newErrors.password = `Password must be at least ${PASSWORD_CONSTRAINTS.MIN_LENGTH} character`;
    } else if (formData.password.length > PASSWORD_CONSTRAINTS.MAX_LENGTH) {
      newErrors.password = `Password must be ${PASSWORD_CONSTRAINTS.MAX_LENGTH} characters or less`;
    }

    // Validate confirm password
    if (!formData.confirmPassword) {
      newErrors.confirmPassword = 'Please confirm your password';
    } else if (formData.password !== formData.confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    try {
      await onSubmit(formData);
      // Reset form on success
      setFormData({ password: '', confirmPassword: '' });
      setErrors({});
    } catch (error) {
      // Error handling is done in parent component
    }
  };

  const handleClose = () => {
    if (!isLoading) {
      setFormData({ password: '', confirmPassword: '' });
      setErrors({});
      onClose();
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={`Update Password: ${screenname?.screenname || ''}`}
    >
      {isLoading ? (
        <LoadingSpinner message="Updating password..." />
      ) : (
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="new-password" className="form-label">
              New Password
            </label>
            <input
              id="new-password"
              type="password"
              className={`form-input ${errors.password ? 'error' : ''}`}
              value={formData.password}
              onChange={(e) =>
                setFormData((prev) => ({ ...prev, password: e.target.value }))
              }
              placeholder="Enter new password (1-8 characters)"
              maxLength={PASSWORD_CONSTRAINTS.MAX_LENGTH}
              disabled={isLoading}
              autoComplete="new-password"
            />
            {errors.password && (
              <div className="form-error">{errors.password}</div>
            )}
            <div style={{ fontSize: '0.75rem', color: '#718096', marginTop: '0.25rem' }}>
              Protocol requires 8 characters or less
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="confirm-password" className="form-label">
              Confirm New Password
            </label>
            <input
              id="confirm-password"
              type="password"
              className={`form-input ${errors.confirmPassword ? 'error' : ''}`}
              value={formData.confirmPassword}
              onChange={(e) =>
                setFormData((prev) => ({ ...prev, confirmPassword: e.target.value }))
              }
              placeholder="Confirm new password"
              maxLength={PASSWORD_CONSTRAINTS.MAX_LENGTH}
              disabled={isLoading}
              autoComplete="new-password"
            />
            {errors.confirmPassword && (
              <div className="form-error">{errors.confirmPassword}</div>
            )}
          </div>

          <div className="modal-footer">
            <button
              type="button"
              onClick={handleClose}
              className="btn btn-secondary"
              disabled={isLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={isLoading}
            >
              Update Password
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
};

export default UpdatePasswordModal;