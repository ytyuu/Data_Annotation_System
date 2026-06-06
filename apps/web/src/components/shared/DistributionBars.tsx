interface DistributionBarsProps {
  entries: Array<{
    key: string;
    label: string;
    count: number;
    color?: string;
  }>;
  total: number;
  barClassName?: string;
}

export function DistributionBars({ entries, total, barClassName = 'h-2' }: DistributionBarsProps) {
  return (
    <div className="space-y-4">
      {entries.map((entry) => {
        const percentage = total > 0 ? Math.round((entry.count / total) * 100) : 0;
        return (
          <div key={entry.key}>
            <div className="mb-1.5 flex items-center justify-between gap-4 text-sm">
              <div className="flex min-w-0 items-center gap-2">
                {entry.color && (
                  <span
                    className="inline-block h-3 w-3 shrink-0 rounded-full"
                    style={{ backgroundColor: entry.color }}
                  />
                )}
                <span className="truncate text-gray-700" title={entry.label}>
                  {entry.label}
                </span>
              </div>
              <span className="shrink-0 text-gray-500">
                {entry.count} 条 ({percentage}%)
              </span>
            </div>
            <div className={['rounded-full bg-gray-100', barClassName].join(' ')}>
              <div
                className={['rounded-full transition-all', barClassName].join(' ')}
                style={{
                  width: `${percentage}%`,
                  backgroundColor: entry.color || '#6b7280',
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
