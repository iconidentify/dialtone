interface ErrorMessageProps {
  message: string;
  onClose?: () => void;
  type?: 'error' | 'warning';
}

const ErrorMessage = ({ message, onClose, type = 'error' }: ErrorMessageProps) => {
  return (
    <div className={type}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span>{message}</span>
        {onClose && (
          <button
            onClick={onClose}
            className="btn btn-small"
            style={{
              background: 'transparent',
              border: 'none',
              color: 'inherit',
              padding: '0.25rem',
              cursor: 'pointer'
            }}
          >
            ×
          </button>
        )}
      </div>
    </div>
  );
};

export default ErrorMessage;