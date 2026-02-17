import { useState, useEffect } from 'react';
import { Screenname, UpdateScreennameFormData, SCREENNAME_CONSTRAINTS } from '../types';
import Modal from './Modal';
import LoadingSpinner from './LoadingSpinner';

interface EditScreennameModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSubmit: (data: UpdateScreennameFormData) => Promise<void>;
  screenname: Screenname | null;
  isLoading: boolean;
}

const EditScreennameModal = ({
  isOpen,
  onClose,
  onSubmit,
  screenname,
  isLoading,
}: EditScreennameModalProps) => {
  const [formData, setFormData] = useState<UpdateScreennameFormData>({
    screenname: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Initialize form data when screenname changes
  useEffect(() => {
    if (screenname) {
      setFormData({ screenname: screenname.screenname });
    } else {
      setFormData({ screenname: '' });
    }
    setErrors({});
  }, [screenname]);

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
    } catch (error) {
      // Error handling is done in parent component
    }
  };

  const handleClose = () => {
    if (!isLoading) {
      setErrors({});
      onClose();
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={`Edit Screenname: ${screenname?.screenname || ''}`}
    >
      {isLoading ? (
        <LoadingSpinner message="Updating screenname..." />
      ) : (
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="edit-screenname" className="form-label">
              New Screenname
            </label>
            <input
              id="edit-screenname"
              type="text"
              className={`form-input ${errors.screenname ? 'error' : ''}`}
              value={formData.screenname}
              onChange={(e) => {
                const value = e.target.value;
                // Only allow alphanumeric characters (blocks ~ and all special chars)
                if (value === '' || SCREENNAME_CONSTRAINTS.PATTERN.test(value)) {
                  setFormData((prev) => ({ ...prev, screenname: value }));
                }
              }}
              placeholder="Enter new screenname (1-10 characters)"
              maxLength={SCREENNAME_CONSTRAINTS.MAX_LENGTH}
              disabled={isLoading}
              autoComplete="username"
            />
            {errors.screenname && (
              <div className="form-error">{errors.screenname}</div>
            )}
            <div style={{ fontSize: '0.75rem', color: '#718096', marginTop: '0.25rem' }}>
              Only letters and numbers allowed
            </div>
          </div>

          <div style={{
            padding: '1rem',
            backgroundColor: '#fff3cd',
            borderRadius: '6px',
            border: '1px solid #ffeeba',
            marginBottom: '1rem'
          }}>
            <p style={{ color: '#856404', margin: 0, fontSize: '0.9rem' }}>
              <strong>Note:</strong> Make sure your new screenname is available before submitting.
              If it's taken, you'll get an error message.
            </p>
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
              Update Screenname
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
};

export default EditScreennameModal;