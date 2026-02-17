import { useState } from 'react';
import { ScreennameFormData, SCREENNAME_CONSTRAINTS, PASSWORD_CONSTRAINTS } from '../types';
import Modal from './Modal';
import LoadingSpinner from './LoadingSpinner';

interface CreateScreennameModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: ScreennameFormData) => Promise<void>;
  isLoading: boolean;
}

const CreateScreennameModal = ({
  isOpen,
  onClose,
  onSubmit,
  isLoading,
}: CreateScreennameModalProps) => {
  const [formData, setFormData] = useState<ScreennameFormData>({
    screenname: '',
    password: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validateForm = (): boolean => {
    const newErrors: Record<string, string> = {};

    // Validate screenname
    if (!formData.screenname.trim()) {
      newErrors.screenname = 'Screenname is required';
    } else if (formData.screenname.length < SCREENNAME_CONSTRAINTS.MIN_LENGTH) {
      newErrors.screenname = `Screenname must be at least ${SCREENNAME_CONSTRAINTS.MIN_LENGTH} character`;
    } else if (formData.screenname.length > SCREENNAME_CONSTRAINTS.MAX_LENGTH) {
      newErrors.screenname = `Screenname must be ${SCREENNAME_CONSTRAINTS.MAX_LENGTH} characters or less`;
    } else if (!SCREENNAME_CONSTRAINTS.PATTERN.test(formData.screenname)) {
      newErrors.screenname = 'Screenname can only contain letters and numbers';
    }

    // Validate password
    if (!formData.password) {
      newErrors.password = 'Password is required';
    } else if (formData.password.length < PASSWORD_CONSTRAINTS.MIN_LENGTH) {
      newErrors.password = `Password must be at least ${PASSWORD_CONSTRAINTS.MIN_LENGTH} character`;
    } else if (formData.password.length > PASSWORD_CONSTRAINTS.MAX_LENGTH) {
      newErrors.password = `Password must be ${PASSWORD_CONSTRAINTS.MAX_LENGTH} characters or less`;
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
      setFormData({ screenname: '', password: '' });
      setErrors({});
    } catch (error) {
      // Error handling is done in parent component
    }
  };

  const handleClose = () => {
    if (!isLoading) {
      setFormData({ screenname: '', password: '' });
      setErrors({});
      onClose();
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title="Create New Screenname">
      {isLoading ? (
        <LoadingSpinner message="Creating screenname..." />
      ) : (
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="screenname" className="form-label">
              Screenname
              <span style={{ float: 'right', fontSize: '0.875rem', color: formData.screenname.length > SCREENNAME_CONSTRAINTS.MAX_LENGTH ? '#e53e3e' : '#718096' }}>
                {formData.screenname.length}/{SCREENNAME_CONSTRAINTS.MAX_LENGTH}
              </span>
            </label>
            <input
              id="screenname"
              type="text"
              className={`form-input ${errors.screenname ? 'error' : ''}`}
              value={formData.screenname}
              onChange={(e) => {
                const value = e.target.value;
                // Only allow alphanumeric characters
                if (value === '' || /^[a-zA-Z0-9]+$/.test(value)) {
                  setFormData((prev) => ({ ...prev, screenname: value }));
                }
              }}
              placeholder="Enter screenname"
              maxLength={SCREENNAME_CONSTRAINTS.MAX_LENGTH}
              disabled={isLoading}
              autoComplete="username"
            />
            {errors.screenname && (
              <div className="form-error">{errors.screenname}</div>
            )}
            <div style={{ fontSize: '0.75rem', color: '#718096', marginTop: '0.25rem' }}>
              <strong>Maximum 10 characters</strong> • Letters and numbers only
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="password" className="form-label">
              Password
              <span style={{ float: 'right', fontSize: '0.875rem', color: formData.password.length > PASSWORD_CONSTRAINTS.MAX_LENGTH ? '#e53e3e' : '#718096' }}>
                {formData.password.length}/{PASSWORD_CONSTRAINTS.MAX_LENGTH}
              </span>
            </label>
            <input
              id="password"
              type="password"
              className={`form-input ${errors.password ? 'error' : ''}`}
              value={formData.password}
              onChange={(e) =>
                setFormData((prev) => ({ ...prev, password: e.target.value }))
              }
              placeholder="Enter password"
              maxLength={PASSWORD_CONSTRAINTS.MAX_LENGTH}
              disabled={isLoading}
              autoComplete="new-password"
            />
            {errors.password && (
              <div className="form-error">{errors.password}</div>
            )}
            <div style={{ fontSize: '0.75rem', color: '#718096', marginTop: '0.25rem' }}>
              Protocol requires 1-8 characters
            </div>
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
              Create Screenname
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
};

export default CreateScreennameModal;