interface DonutChartProps {
  data: Record<string, number>;
  total: number;
  colors: Record<string, string> | Record<number, string>;
  labels: Record<string, string> | Record<number, string>;
}

export function DonutChart({ data, total, colors, labels }: DonutChartProps) {
  const entries = Object.entries(data);
  if (entries.length === 0 || total === 0) return null;

  const radius = 80;
  const strokeWidth = 24;
  const center = radius + strokeWidth;
  const circumference = 2 * Math.PI * radius;
  let accumulated = 0;

  return (
    <div className="flex items-center gap-8">
      <svg width={center * 2} height={center * 2} className="shrink-0 -rotate-90">
        <circle
          cx={center}
          cy={center}
          r={radius}
          fill="none"
          stroke="#f3f4f6"
          strokeWidth={strokeWidth}
        />
        {entries.map(([key, count]) => {
          const percentage = count / total;
          const dashLength = circumference * percentage;
          const gapLength = circumference - dashLength;
          const offset = circumference - accumulated * circumference;
          accumulated += percentage;
          const color = colors[key as keyof typeof colors] || '#9ca3af';

          return (
            <circle
              key={key}
              cx={center}
              cy={center}
              r={radius}
              fill="none"
              stroke={color}
              strokeWidth={strokeWidth}
              strokeDasharray={`${dashLength} ${gapLength}`}
              strokeDashoffset={offset}
              strokeLinecap="butt"
            />
          );
        })}
        <text
          x={center}
          y={center}
          textAnchor="middle"
          dominantBaseline="central"
          className="fill-gray-700"
          style={{ fontSize: '28px', fontWeight: 600 }}
          transform={`rotate(90 ${center} ${center})`}
        >
          {total}
        </text>
        <text
          x={center}
          y={center + 22}
          textAnchor="middle"
          dominantBaseline="central"
          className="fill-gray-400"
          style={{ fontSize: '12px' }}
          transform={`rotate(90 ${center} ${center})`}
        >
          总计
        </text>
      </svg>

      <div className="flex flex-col gap-2">
        {entries.map(([key, count]) => {
          const percentage = Math.round((count / total) * 100);
          const color = colors[key as keyof typeof colors] || '#9ca3af';
          const label = labels[key as keyof typeof labels] || key;

          return (
            <div key={key} className="flex items-center gap-2">
              <span
                className="inline-block h-3 w-3 rounded-full"
                style={{ backgroundColor: color }}
              />
              <span className="text-sm text-gray-700">{label}</span>
              <span className="text-sm text-gray-500">{count} ({percentage}%)</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
