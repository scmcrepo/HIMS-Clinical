import React, { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { ChevronLeft, ChevronRight, Calendar } from 'lucide-react';

const MONTHS_FULL = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];
const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const DAYS_SU = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
const DAYS_MO = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

function daysInMonth(year: number, month: number) { return new Date(year, month + 1, 0).getDate(); }
function firstDayOfMonth(year: number, month: number) { return new Date(year, month, 1).getDay(); }
function decadeStart(year: number) { return Math.floor(year / 10) * 10; }

const LEVEL_ORDER = ['month', 'year', 'decade'];

const SIZE_MAP = {
  xs: { cell: 'w-6 h-6 text-[10px]', header: 'text-[10px]', input: 'px-2 py-1 text-xs', monthCell: 'py-1.5 text-[10px]', gap: 'p-2' },
  sm: { cell: 'w-7 h-7 text-[11px]', header: 'text-xs', input: 'px-3 py-1.5 text-sm', monthCell: 'py-2 text-[11px]', gap: 'p-2.5' },
  md: { cell: 'w-8 h-8 text-xs', header: 'text-sm', input: 'px-3 py-2 text-sm', monthCell: 'py-2.5 text-xs', gap: 'p-3' },
  lg: { cell: 'w-9 h-9 text-sm', header: 'text-sm', input: 'px-4 py-2.5 text-base', monthCell: 'py-3 text-sm', gap: 'p-3.5' },
};

interface DatePickerProps {
  value?: string | null;
  onChange: (val: string) => void;
  minDate?: string;
  maxDate?: string;
  placeholder?: string;
  hasError?: boolean;
  defaultLevel?: 'month' | 'year' | 'decade';
  maxLevel?: 'month' | 'year' | 'decade';
  clearable?: boolean;
  disabled?: boolean;
  label?: React.ReactNode;
  description?: React.ReactNode;
  error?: string | React.ReactNode;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  firstDayOfWeek?: 0 | 1;
  allowDeselect?: boolean;
  hideOutsideDates?: boolean;
  getDayProps?: (date: Date) => any;
  getMonthControlProps?: (date: Date) => any;
  getYearControlProps?: (date: Date) => any;
}

const DatePicker = ({
  value,
  onChange,
  minDate,
  maxDate,
  placeholder = 'Pick a date',
  hasError = false,
  defaultLevel = 'month',
  maxLevel = 'decade',
  clearable = true,
  disabled = false,
  label,
  description,
  error,
  size = 'sm',
  firstDayOfWeek = 0,
  allowDeselect = false,
  hideOutsideDates = false,
  getDayProps,
  getMonthControlProps,
  getYearControlProps,
}: DatePickerProps) => {
  const today = new Date();
  const safeValue = typeof value === 'string' ? value : '';
  const parsed = safeValue ? new Date(safeValue + 'T00:00:00') : null;

  const [open, setOpen] = useState(false);
  const [level, setLevel] = useState(defaultLevel);
  const [viewYear, setViewYear] = useState(parsed ? parsed.getFullYear() : today.getFullYear());
  const [viewMonth, setViewMonth] = useState(parsed ? parsed.getMonth() : today.getMonth());
  const [coords, setCoords] = useState<{ left: number; top: number; width: number } | null>(null);

  const inputRef = useRef<HTMLInputElement>(null);
  const calRef = useRef<HTMLDivElement>(null);

  const minD = minDate ? new Date(minDate + 'T00:00:00') : null;
  const maxD = maxDate ? new Date(maxDate + 'T23:59:59') : null;

  const isDateDisabled = (y: number, m: number, d: number) => {
    const dt = new Date(y, m, d);
    if (minD && dt < minD) return true;
    if (maxD && dt > maxD) return true;
    return false;
  };
  const isMonthDisabled = (y: number, m: number) => {
    if (maxD && new Date(y, m, 1) > maxD) return true;
    if (minD && new Date(y, m + 1, 0) < minD) return true;
    return false;
  };
  const isYearDisabled = (y: number) => {
    if (maxD && new Date(y, 0, 1) > maxD) return true;
    if (minD && new Date(y, 11, 31) < minD) return true;
    return false;
  };

  const s = SIZE_MAP[size] || SIZE_MAP.sm;
  const DAYS = firstDayOfWeek === 1 ? DAYS_MO : DAYS_SU;

  const calcCoords = useCallback(() => {
    if (!inputRef.current) return;
    const rect = inputRef.current.getBoundingClientRect();
    const GAP = 4;
    const spaceBelow = window.innerHeight - rect.bottom - GAP;
    const spaceAbove = rect.top - GAP;

    let top;
    if (spaceBelow >= 280 || spaceBelow >= spaceAbove) {
      top = rect.bottom + GAP;
    } else {
      top = Math.max(8, rect.top - 340 - GAP);
    }

    setCoords({
      left: Math.min(rect.left, window.innerWidth - 270),
      top,
      width: rect.width,
    });
  }, []);

  const toggle = () => {
    if (disabled) return;
    if (!open) {
      const anchor = parsed || today;
      setViewYear(anchor.getFullYear());
      setViewMonth(anchor.getMonth());
      setLevel(defaultLevel);
      calcCoords();
      setOpen(true);
    } else {
      setOpen(false);
    }
  };

  useEffect(() => {
    if (!open || !inputRef.current) return;
    let scrollParent = inputRef.current.parentElement;
    while (scrollParent && scrollParent !== document.body) {
      const { overflowY } = window.getComputedStyle(scrollParent);
      if (overflowY === 'auto' || overflowY === 'scroll') break;
      scrollParent = scrollParent.parentElement;
    }
    if (scrollParent && scrollParent !== document.body) {
      const prev = scrollParent.style.overflowY;
      scrollParent.style.overflowY = 'hidden';
      return () => { if (scrollParent) scrollParent.style.overflowY = prev; };
    }
    return undefined;
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handle = (e: MouseEvent) => {
      if (
        inputRef.current && !inputRef.current.contains(e.target as Node) &&
        calRef.current && !calRef.current.contains(e.target as Node)
      ) setOpen(false);
    };
    const reposition = () => { if (open) calcCoords(); };
    document.addEventListener('mousedown', handle);
    window.addEventListener('resize', reposition);
    return () => {
      document.removeEventListener('mousedown', handle);
      window.removeEventListener('resize', reposition);
    };
  }, [open, calcCoords]);

  const canZoomOut = (currentLevel: string) => {
    const ci = LEVEL_ORDER.indexOf(currentLevel);
    const mi = LEVEL_ORDER.indexOf(maxLevel);
    return ci < mi;
  };
  const zoomOut = () => {
    if (level === 'month' && canZoomOut('month')) setLevel('year');
    else if (level === 'year' && canZoomOut('year')) setLevel('decade');
  };

  const prevMonth = () => {
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear(prev => prev - 1);
    } else {
      setViewMonth(prev => prev - 1);
    }
  };
  const nextMonth = () => {
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear(prev => prev + 1);
    } else {
      setViewMonth(prev => prev + 1);
    }
  };

  const selectDate = (d: number) => {
    const mm = String(viewMonth + 1).padStart(2, '0');
    const dd = String(d).padStart(2, '0');
    const newVal = `${viewYear}-${mm}-${dd}`;
    if (allowDeselect && value === newVal) { onChange(''); }
    else { onChange(newVal); }
    setOpen(false);
  };
  const selectMonth = (m: number) => { setViewMonth(m); setLevel('month'); };
  const selectYear = (y: number) => { setViewYear(y); setLevel('year'); };

  const headerLabel = () => {
    if (level === 'month') return `${MONTHS_FULL[viewMonth]} ${viewYear}`;
    if (level === 'year') return `${viewYear}`;
    const start = decadeStart(viewYear);
    return `${start} - ${start + 9}`;
  };
  const headerPrev = () => {
    if (level === 'month') prevMonth();
    else if (level === 'year') setViewYear(y => y - 1);
    else setViewYear(y => y - 10);
  };
  const headerNext = () => {
    if (level === 'month') nextMonth();
    else if (level === 'year') setViewYear(y => y + 1);
    else setViewYear(y => y + 10);
  };

  const buildDayGrid = () => {
    const fd = firstDayOfMonth(viewYear, viewMonth);
    const shift = firstDayOfWeek === 1 ? (fd === 0 ? 6 : fd - 1) : fd;
    const numDays = daysInMonth(viewYear, viewMonth);
    const prevM = viewMonth === 0 ? 11 : viewMonth - 1;
    const prevY = viewMonth === 0 ? viewYear - 1 : viewYear;
    const prevDays = daysInMonth(prevY, prevM);

    const cells = [];
    for (let i = shift - 1; i >= 0; i--) {
      cells.push({ day: prevDays - i, outside: true, year: prevY, month: prevM });
    }
    for (let d = 1; d <= numDays; d++) {
      cells.push({ day: d, outside: false, year: viewYear, month: viewMonth });
    }
    while (cells.length % 7 !== 0) {
      const nm = viewMonth === 11 ? 0 : viewMonth + 1;
      const ny = viewMonth === 11 ? viewYear + 1 : viewYear;
      cells.push({ day: cells.length - shift - numDays + 1, outside: true, year: ny, month: nm });
    }
    return cells;
  };

  const selectedDay = parsed && parsed.getFullYear() === viewYear && parsed.getMonth() === viewMonth ? parsed.getDate() : null;
  const todayDay = today.getFullYear() === viewYear && today.getMonth() === viewMonth ? today.getDate() : null;

  const renderDayView = () => {
    const cells = buildDayGrid();
    return (
      <>
        <div className="grid grid-cols-7 mb-0.5">
          {DAYS.map(d => (
            <div key={d} className={`text-center font-semibold text-muted-foreground py-0.5 uppercase tracking-wide ${s.cell.split(' ').find(c => c.startsWith('text-'))}`}>{d}</div>
          ))}
        </div>
        <div className="grid grid-cols-7">
          {cells.map((c, i) => {
            if (c.outside && hideOutsideDates) return <div key={i} className={s.cell.replace(/text-\S+/, '')} />;
            const dis = c.outside || isDateDisabled(c.year, c.month, c.day);
            const isSelected = !c.outside && c.day === selectedDay;
            const isToday = !c.outside && c.day === todayDay;
            const extraProps = getDayProps ? getDayProps(new Date(c.year, c.month, c.day)) : {};
            const finalDisabled = dis || extraProps.disabled;

            return (
              <button
                key={i}
                type="button"
                disabled={finalDisabled}
                onClick={() => !finalDisabled && !c.outside && selectDate(c.day)}
                className={`${s.cell} mx-auto flex items-center justify-center rounded-md font-medium transition-all duration-150
                  ${c.outside
                    ? 'text-muted-foreground/25 cursor-default'
                    : isSelected
                      ? 'bg-blue-600 text-white shadow-sm shadow-blue-600/30 scale-105'
                      : isToday
                        ? 'border border-blue-500 text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30'
                        : finalDisabled
                          ? 'text-muted-foreground/30 cursor-not-allowed'
                          : 'text-foreground hover:bg-muted'
                  }`}
              >
                {c.day}
              </button>
            );
          })}
        </div>
      </>
    );
  };

  const renderMonthView = () => (
    <div className="grid grid-cols-3 gap-1.5 px-1">
      {MONTHS_SHORT.map((m, i) => {
        const dis = isMonthDisabled(viewYear, i);
        const isCurrent = parsed && parsed.getFullYear() === viewYear && parsed.getMonth() === i;
        const isThisMonth = today.getFullYear() === viewYear && today.getMonth() === i;
        const extraProps = getMonthControlProps ? getMonthControlProps(new Date(viewYear, i, 1)) : {};
        const finalDisabled = dis || extraProps.disabled;

        return (
          <button
            key={m}
            type="button"
            disabled={finalDisabled}
            onClick={() => !finalDisabled && selectMonth(i)}
            className={`${s.monthCell} rounded-lg font-medium transition-all duration-150
              ${isCurrent
                ? 'bg-blue-600 text-white shadow-sm shadow-blue-600/30'
                : isThisMonth
                  ? 'border border-blue-500 text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30'
                  : finalDisabled
                    ? 'text-muted-foreground/30 cursor-not-allowed'
                    : 'text-foreground hover:bg-muted'
              }`}
          >
            {m}
          </button>
        );
      })}
    </div>
  );

  const renderDecadeView = () => {
    const ds = decadeStart(viewYear);
    const years = [];
    for (let y = ds - 1; y <= ds + 10; y++) years.push(y);

    return (
      <div className="grid grid-cols-3 gap-1.5 px-1">
        {years.map(y => {
          const outside = y < ds || y > ds + 9;
          const dis = isYearDisabled(y);
          const isCurrent = parsed && parsed.getFullYear() === y;
          const isThisYear = today.getFullYear() === y;
          const extraProps = getYearControlProps ? getYearControlProps(new Date(y, 0, 1)) : {};
          const finalDisabled = dis || extraProps.disabled;

          return (
            <button
              key={y}
              type="button"
              disabled={finalDisabled || outside}
              onClick={() => !finalDisabled && !outside && selectYear(y)}
              className={`${s.monthCell} rounded-lg font-medium transition-all duration-150
                ${outside
                  ? 'text-muted-foreground/25 cursor-default'
                  : isCurrent
                    ? 'bg-blue-600 text-white shadow-sm shadow-blue-600/30'
                    : isThisYear
                      ? 'border border-blue-500 text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30'
                      : finalDisabled
                        ? 'text-muted-foreground/30 cursor-not-allowed'
                        : 'text-foreground hover:bg-muted'
                }`}
            >
              {y}
            </button>
          );
        })}
      </div>
    );
  };

  const calendar = coords && open && (
    <div
      ref={calRef}
      style={{
        position: 'fixed',
        top: coords.top,
        left: coords.left,
        width: 260,
        zIndex: 99999,
      }}
      data-datepicker-portal="true"
      className={`bg-background border border-border rounded-xl shadow-2xl ${s.gap} animate-in fade-in zoom-in-95 duration-150 origin-top`}
    >
      <div className="flex items-center justify-between mb-2">
        <button type="button" onClick={headerPrev} className="p-1 rounded-md hover:bg-muted transition-colors">
          <ChevronLeft className="w-4 h-4 text-muted-foreground" />
        </button>
        <button
          type="button"
          onClick={canZoomOut(level) ? zoomOut : undefined}
          className={`${s.header} font-semibold text-foreground px-2 py-0.5 rounded-md transition-colors
            ${canZoomOut(level) ? 'hover:bg-muted cursor-pointer' : 'cursor-default'}`}
        >
          {headerLabel()}
        </button>
        <button type="button" onClick={headerNext} className="p-1 rounded-md hover:bg-muted transition-colors">
          <ChevronRight className="w-4 h-4 text-muted-foreground" />
        </button>
      </div>

      {level === 'month' && renderDayView()}
      {level === 'year' && renderMonthView()}
      {level === 'decade' && renderDecadeView()}

      {level === 'month' && (
        <div className="mt-2 pt-2 border-t border-border flex justify-end">
          <button
            type="button"
            onClick={() => {
              const mm = String(today.getMonth() + 1).padStart(2, '0');
              const dd = String(today.getDate()).padStart(2, '0');
              onChange(`${today.getFullYear()}-${mm}-${dd}`);
              setOpen(false);
            }}
            className={`px-2.5 py-1 ${s.header} font-medium border border-border rounded-md text-foreground hover:bg-muted transition-colors`}
          >
            Today
          </button>
        </div>
      )}
    </div>
  );

  const showError = hasError || !!error;

  const getDisplayValue = () => {
    if (!value) return '';
    const parts = value.split('-');
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return value;
  };

  return (
    <div className="relative">
      {label && <label className="block text-sm font-medium text-foreground mb-1">{label}</label>}
      {description && <p className="text-xs text-muted-foreground mb-1">{description}</p>}
      <div className="relative">
        <div className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none">
          <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
        </div>
        <input
          ref={inputRef}
          readOnly
          value={getDisplayValue()}
          placeholder={placeholder}
          onClick={toggle}
          disabled={disabled}
          className={`w-full border rounded-lg pl-8 pr-8 ${s.input} cursor-pointer bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 transition-colors
            ${disabled ? 'opacity-50 cursor-not-allowed' : ''}
            ${showError ? 'border-destructive focus:ring-destructive/40' : 'border-border focus:ring-ring'}`}
        />
        {clearable && value && !disabled && (
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); onChange(''); setOpen(false); }}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground text-xs transition-colors"
          >✕</button>
        )}
      </div>
      {error && typeof error === 'string' && <p className="text-xs text-destructive mt-1">{error}</p>}
      {open && coords && createPortal(calendar, document.body)}
    </div>
  );
};

export default DatePicker;
