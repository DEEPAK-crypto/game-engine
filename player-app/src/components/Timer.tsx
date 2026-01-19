import { useEffect, useState } from 'react';
import { Clock } from 'lucide-react';

interface TimerProps {
  expiresAt: string | null;
  onExpire?: () => void;
  size?: 'sm' | 'md' | 'lg';
}

export function Timer({ expiresAt, onExpire, size = 'md' }: TimerProps) {
  const [timeRemaining, setTimeRemaining] = useState(0);

  useEffect(() => {
    if (!expiresAt) {
      setTimeRemaining(0);
      return;
    }

    const calculateRemaining = () => {
      const now = Date.now();
      const expiresAtTime = new Date(expiresAt).getTime();
      const remaining = Math.max(0, Math.floor((expiresAtTime - now) / 1000));
      return remaining;
    };

    setTimeRemaining(calculateRemaining());

    const interval = setInterval(() => {
      const remaining = calculateRemaining();
      setTimeRemaining(remaining);

      if (remaining === 0) {
        clearInterval(interval);
        onExpire?.();
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [expiresAt, onExpire]);

  const sizeClasses = {
    sm: 'w-16 h-16 text-lg',
    md: 'w-24 h-24 text-2xl',
    lg: 'w-32 h-32 text-4xl',
  };

  const strokeWidth = size === 'lg' ? 4 : size === 'md' ? 6 : 8;
  const radius = size === 'lg' ? 58 : size === 'md' ? 42 : 28;
  const circumference = 2 * Math.PI * radius;

  // Assume max duration of 30 seconds for the progress calculation
  const maxDuration = 30;
  const progress = Math.min(1, timeRemaining / maxDuration);
  const offset = circumference * (1 - progress);

  const getColor = () => {
    if (timeRemaining <= 5) return 'text-red-500';
    if (timeRemaining <= 10) return 'text-yellow-500';
    return 'text-primary-500';
  };

  const getStrokeColor = () => {
    if (timeRemaining <= 5) return '#ef4444';
    if (timeRemaining <= 10) return '#eab308';
    return '#0ea5e9';
  };

  return (
    <div className={`relative ${sizeClasses[size]} flex items-center justify-center`}>
      <svg
        className="absolute transform -rotate-90"
        viewBox={`0 0 ${(radius + strokeWidth) * 2} ${(radius + strokeWidth) * 2}`}
      >
        {/* Background circle */}
        <circle
          cx={radius + strokeWidth}
          cy={radius + strokeWidth}
          r={radius}
          fill="none"
          stroke="#e5e7eb"
          strokeWidth={strokeWidth}
        />
        {/* Progress circle */}
        <circle
          cx={radius + strokeWidth}
          cy={radius + strokeWidth}
          r={radius}
          fill="none"
          stroke={getStrokeColor()}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          className="timer-circle"
        />
      </svg>
      <div className={`flex flex-col items-center ${getColor()}`}>
        <Clock className={size === 'lg' ? 'w-6 h-6' : size === 'md' ? 'w-4 h-4' : 'w-3 h-3'} />
        <span className={`font-bold ${timeRemaining <= 5 ? 'animate-countdown' : ''}`}>
          {timeRemaining}
        </span>
      </div>
    </div>
  );
}
