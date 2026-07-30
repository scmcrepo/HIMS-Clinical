import { useState, useRef, useEffect } from 'react';
import { cn } from '../../lib/utils';
import { X, ChevronDown } from 'lucide-react';

interface DepartmentRecord {
  id: string;
  name: string;
  status?: string | number;
}

interface Props {
  value: string[];
  onChange: (ids: string[]) => void;
  allDepartments?: DepartmentRecord[];
  placeholder?: string;
  className?: string;
  inputCls?: string;
}

export function DepartmentMultiSelect({
  value,
  onChange,
  allDepartments = [],
  placeholder = 'Search department...',
  className,
  inputCls,
}: Props) {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Close dropdown on click outside
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      const target = e.target as Node;
      // If the clicked element is no longer in the DOM (e.g. it was selected and removed),
      // do not close the dropdown.
      if (target && !document.body.contains(target)) {
        return;
      }
      if (!ref.current?.contains(target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  // Filter departments locally based on the search query
  const filteredDepartments = allDepartments.filter(d => {
    // Only active departments
    const isActive = d.status === undefined || d.status === 'ACTIVE' || d.status === 1 || String(d.status) === '1';
    if (!isActive) return false;

    return d.name.toLowerCase().includes(query.toLowerCase());
  });

  // Get display objects for selected department IDs
  const selectedDepartments = value
    .map(id => allDepartments.find(d => d.id === id))
    .filter(Boolean) as DepartmentRecord[];

  // Departments available in dropdown (not already selected)
  const availableDepartments = filteredDepartments.filter(d => !value.includes(d.id));

  useEffect(() => {
    if (!open) setQuery('');
  }, [open]);

  const handleSelect = (d: DepartmentRecord) => {
    if (!value.includes(d.id)) {
      onChange([...value, d.id]);
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
        {selectedDepartments.map(d => (
          <span
            key={d.id}
            className="inline-flex items-center gap-1 bg-neutral-100 border border-neutral-200 text-neutral-700 text-xs font-medium px-2 py-0.5 rounded-md"
          >
            {d.name}
            <button
              type="button"
              onMouseDown={e => {
                e.preventDefault();
                e.stopPropagation();
                handleRemove(d.id);
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
          placeholder={selectedDepartments.length === 0 ? placeholder : ''}
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
          {availableDepartments.length > 0 ? (
            <ul className="overflow-y-auto">
              {availableDepartments.map(d => (
                <li
                  key={d.id}
                  title={d.name}
                  className="px-4 py-2 hover:bg-neutral-600 hover:text-white cursor-pointer transition-colors text-gray-900 text-sm"
                  onMouseDown={e => {
                    e.preventDefault();
                    handleSelect(d);
                  }}
                >
                  <span className="font-medium">{d.name}</span>
                </li>
              ))}
            </ul>
          ) : (
            <div className="px-4 py-3 text-xs text-gray-500 text-center">
              {value.length > 0 && filteredDepartments.length === 0 ? 'All departments selected' : 'No departments found'}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
