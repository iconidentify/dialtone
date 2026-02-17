interface LoadingSpinnerProps {
  message?: string;
}

const LoadingSpinner = ({ message = 'Loading...' }: LoadingSpinnerProps) => {
  return (
    <div className="loading">
      <div className="loading-text">{message}</div>
    </div>
  );
};

export default LoadingSpinner;