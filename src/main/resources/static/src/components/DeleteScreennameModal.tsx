import Modal from './Modal';
import { Screenname } from '../types';

interface DeleteScreennameModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  screenname: Screenname | null;
  isLoading: boolean;
}

const DeleteScreennameModal = ({
  isOpen,
  onClose,
  onConfirm,
  screenname,
  isLoading,
}: DeleteScreennameModalProps) => {
  if (!screenname) {
    return null;
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Delete Screenname">
      <div className="delete-confirmation">
        <div className="delete-warning">
          <div className="delete-warning-icon">⚠️</div>
          <p className="delete-warning-text">
            Are you sure you want to delete the screenname{' '}
            <strong>"{screenname.screenname}"</strong>?
          </p>
        </div>
        <p className="delete-warning-note">
          This action cannot be undone. The screenname will be permanently removed
          and you will no longer be able to log in with it.
        </p>
      </div>

      <div className="modal-actions">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={onClose}
          disabled={isLoading}
        >
          Cancel
        </button>
        <button
          type="button"
          className="btn btn-danger"
          onClick={onConfirm}
          disabled={isLoading}
        >
          {isLoading ? 'Deleting...' : 'Delete Screenname'}
        </button>
      </div>
    </Modal>
  );
};

export default DeleteScreennameModal;

