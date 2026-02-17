import { useState, useEffect } from 'react';

interface TosModalProps {
  onAccept: () => void;
  onDecline: () => void;
}

const TosModal = ({ onAccept, onDecline }: TosModalProps) => {
  const [tosContent, setTosContent] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const loadTos = async () => {
      try {
        const response = await fetch('/TOS.txt');
        if (response.ok) {
          const text = await response.text();
          setTosContent(text);
        } else {
          setTosContent('Terms of Service could not be loaded. Please try again later.');
        }
      } catch (error) {
        setTosContent('Terms of Service could not be loaded. Please try again later.');
      } finally {
        setIsLoading(false);
      }
    };

    loadTos();
  }, []);

  return (
    <div className="tos-modal-overlay">
      <div className="tos-modal">
        <div className="tos-modal-header">
          <h2>Terms of Service</h2>
          <p>Please read and accept the terms below to continue.</p>
        </div>
        <div className="tos-modal-content">
          {isLoading ? (
            <p className="tos-loading">Loading terms...</p>
          ) : (
            <pre className="tos-text">{tosContent}</pre>
          )}
        </div>
        <div className="tos-modal-footer">
          <button
            type="button"
            className="btn btn-tos-decline"
            onClick={onDecline}
            disabled={isLoading}
          >
            DISAGREE
          </button>
          <button
            type="button"
            className="btn btn-tos-agree"
            onClick={onAccept}
            disabled={isLoading}
          >
            AGREE
          </button>
        </div>
      </div>
    </div>
  );
};

export default TosModal;
