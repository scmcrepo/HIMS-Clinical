import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

interface BackButtonProps {
  label?: string;
  className?: string;
  to?: string;
  onClick?: () => void;
}

export default function BackButton({ label = 'Back', className = '', to, onClick }: BackButtonProps) {
  const navigate = useNavigate();

  const handleClick = () => {
    if (onClick) {
      onClick();
    } else if (to) {
      navigate(to);
    } else {
      navigate(-1);
    }
  };

  return (
    <button
      onClick={handleClick}
      className={`group flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-gray-500 hover:text-gray-900 bg-white hover:bg-gray-50 border border-gray-200 hover:border-gray-300 rounded-lg transition-all duration-200 shadow-sm hover:shadow-md active:scale-95 ${className}`}
      aria-label="Go back"
    >
      <ArrowLeft className="w-4 h-4 transition-transform group-hover:-translate-x-0.5" />
      <span>{label}</span>
    </button>
  );
}
