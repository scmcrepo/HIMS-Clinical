import { useState, useRef, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { roleApi, RoleRecord } from '../../services/user/userApi';
import { cn } from '../../lib/utils';
import { X, ChevronDown } from 'lucide-react';

interface Props {
  value: string[];
  onChange: (ids: string[]) => void;
  allRoles?: RoleRecord[];
  placeholder?: string;
  className?: string;
  inputCls?: string;
}

export function RoleMultiSelect({ value, onChange, allRoles = [], placeholder = 'Search role...', className, inputCls }: Props) {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

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
    queryFn: () => roleApi.getPaginated({ start: 0, limit: 20, value: query }),
    enabled: open,
  });

  const roles = (searchResults?.content ?? []).filter(r => r.status !== 0);

  // Merge allRoles with search results for display name resolution
  const allKnown = [...roles];
  allRoles.forEach(r => {
    if (!allKnown.some(k => k.id === r.id)) allKnown.push(r);
  });

  // Get display names for selected role IDs
  const selectedRoles = value
    .map(id => allKnown.find(r => r.id === id))
    .filter(Boolean) as RoleRecord[];

  // Roles available in dropdown (not already selected)
  const availableRoles = roles.filter(r => !value.includes(r.id));

  useEffect(() => {
    if (!open) setQuery('');
  }, [open]);

  const handleSelect = (r: RoleRecord) => {
    if (!value.includes(r.id)) {
      onChange([...value, r.id]);
    }
    setQuery('');
    inputRef.current?.focus();
  };

  const handleRemove = (id: string) => {
    onChange(value.filter(v => v !== id));
  };

  return (
    <div ref={ref} className={cn('relative w-full', className)}>
      {/* Selected chips + input */}
      <div
        className={cn(
          inputCls,
          'flex flex-wrap items-center gap-1.5 min-h-[38px] pr-10 cursor-text',
          open && 'border-neutral-500 ring-1 ring-neutral-500'
        )}
        onClick={() => {
          setOpen(true);
          inputRef.current?.focus();
        }}
      >
        {selectedRoles.map(r => (
          <span
            key={r.id}
            className="inline-flex items-center gap-1 bg-neutral-100 border border-neutral-200 text-neutral-700 text-xs font-medium px-2 py-0.5 rounded-md"
          >
            {r.name}
            <button
              type="button"
              onMouseDown={e => {
                e.preventDefault();
                e.stopPropagation();
                handleRemove(r.id);
              }}
              className="text-neutral-400 hover:text-neutral-700 transition-colors cursor-pointer"
            >
              <X size={12} />
            </button>
          </span>
        ))}
        <input
          ref={inputRef}
          type="text"
          value={open ? query : ''}
          placeholder={selectedRoles.length === 0 ? placeholder : ''}
          className="flex-1 min-w-[80px] outline-none bg-transparent text-sm placeholder:text-gray-400"
          onChange={e => {
            setQuery(e.target.value);
            if (!open) setOpen(true);
          }}
          onFocus={() => setOpen(true)}
        />
      </div>

      {/* Chevron icon */}
      <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none text-gray-400">
        <ChevronDown size={18} className={cn('transition-transform', open && 'rotate-180')} />
      </div>

      {/* Dropdown */}
      {open && (
        <div className="absolute z-50 w-full mt-1 bg-white border border-gray-300 rounded-lg shadow-md flex flex-col max-h-60 overflow-hidden">
          {isLoading ? (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">Searching...</div>
          ) : availableRoles.length > 0 ? (
            <ul className="overflow-y-auto">
              {availableRoles.map(r => (
                <li
                  key={r.id}
                  title={r.name}
                  className="px-4 py-2 hover:bg-neutral-600 hover:text-white cursor-pointer transition-colors text-gray-900 text-sm"
                  onMouseDown={e => {
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
            <div className="px-4 py-3 text-xs text-gray-500 text-center">
              {value.length > 0 && roles.length > 0 ? 'All roles selected' : 'No roles found'}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
