'use client';

// Shared filter bar for /shop and /cards.
// Left: category underline tabs. Right: sort chips. Presentation only —
// the owning page holds the state.
interface FilterOption {
  key: string;
  label: string;
}

interface FilterBarProps {
  tabs: FilterOption[];
  activeTab: string;
  onTab: (key: string) => void;
  sorts: FilterOption[];
  activeSort: string;
  onSort: (key: string) => void;
}

export default function FilterBar({ tabs, activeTab, onTab, sorts, activeSort, onSort }: FilterBarProps) {
  return (
    <div className="mb-[22px] flex flex-wrap items-center justify-between gap-4 border-b border-line">
      <div className="flex gap-6">
        {tabs.map((t) => {
          const on = t.key === activeTab;
          return (
            <button
              key={t.key}
              type="button"
              onClick={() => onTab(t.key)}
              className={`-mb-px cursor-pointer border-b-[3px] px-0.5 pb-3 text-base font-bold transition-colors duration-150 ${
                on ? 'border-brand text-brand-dark' : 'border-transparent text-faint hover:border-line hover:text-[#5b6a54]'
              }`}
            >
              {t.label}
            </button>
          );
        })}
      </div>
      <div className="flex gap-1 pb-2">
        {sorts.map((s) => {
          const on = s.key === activeSort;
          return (
            <button
              key={s.key}
              type="button"
              onClick={() => onSort(s.key)}
              className={`cursor-pointer whitespace-nowrap rounded-lg px-3 py-[7px] text-[13px] font-semibold transition-colors duration-150 ${
                on ? 'bg-brand-soft text-brand-dark' : 'bg-transparent text-faint hover:bg-brand-soft hover:text-brand-dark'
              }`}
            >
              {s.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}
