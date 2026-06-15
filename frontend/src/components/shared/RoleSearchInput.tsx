import { useState, useRef, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { roleApi, RoleRecord } from '../../services/user/userApi';
import { cn } from '../../lib/utils';

interface Props {
  value: string;
  onChange: (id: string) => void;
  allRoles?: RoleRecord[];
  placeholder?: string;
  className?: string;
  inputCls?: string;
}

export function RoleSearchInput({ value, onChange, allRoles = [], placeholder = 'Search role...', className, inputCls }: Props) {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // Close dropdown on click outside
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Fetch roles from backend based on the search query
  const { data: searchResults, isLoading } = useQuery({
    queryKey: ['rolesSearch', query],
    queryFn: () => roleApi.getPaginated({ start: 0, limit: 10, value: query }),
    enabled: open,
  });

  const roles = searchResults?.content ?? [];

  // Find the selected role's name
  const selectedRole = roles.find(r => r.id === value) || allRoles.find(r => r.id === value);
  const displayValue = selectedRole ? selectedRole.name : '';

  useEffect(() => {
    if (!open) {
      setQuery('');
    }
  }, [open]);

  const handleSelect = (r: RoleRecord) => {
    onChange(r.id);
    setOpen(false);
  };

  return (
    <div ref={ref} className={cn('relative w-full', className)}>
      <div className="relative">
        <input
          type="text"
          value={open ? query : displayValue}
          title={displayValue}
          placeholder={open ? "Type to search..." : placeholder}
          className={cn(
            inputCls,
            "w-full pr-10 outline-none transition-all",
            open && "border-neutral-500 ring-1 ring-neutral-500"
          )}
          onChange={e => {
            setQuery(e.target.value);
            if (!e.target.value && value) {
              onChange('');
            }
            if (!open) setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onClick={() => setOpen(true)}
        />
        <div className="absolute right-3 top-1/2 -translate-y-1/2 flex items-center gap-1">
          {value && (
            <button
              type="button"
              onMouseDown={(e) => {
                e.preventDefault();
                onChange('');
                setQuery('');
                setOpen(true);
              }}
              className="p-1 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-100 transition-colors"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2.5" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          )}
          <div className="text-gray-400 pointer-events-none">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
            </svg>
          </div>
        </div>
      </div>

      {open && (
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-md flex flex-col max-h-60 overflow-hidden">
          {isLoading ? (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">Searching...</div>
          ) : roles.length > 0 ? (
            <ul className="overflow-y-auto">
              {roles.map(r => (
                <li
                  key={r.id}
                  title={r.name}
                  className={cn(
                    "px-4 py-2 hover:bg-neutral-600 hover:text-white cursor-pointer transition-colors text-gray-900 text-sm",
                    value === r.id ? "bg-neutral-600 text-white" : ""
                  )}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    handleSelect(r);
                  }}
                >
                  <span className="font-medium">{r.name}</span>
                  {r.description && (
                    <span className="block text-xs opacity-75">{r.description}</span>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">No roles found</div>
          )}
        </div>
      )}
    </div>
  );
}
