interface SectionPlaceholderProps {
  title: string;
  description?: string;
}

export function SectionPlaceholder({ title, description }: SectionPlaceholderProps) {
  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
      <div className="text-base font-semibold text-gray-900">{title}</div>
      {description && <div className="mt-2 text-sm text-gray-500">{description}</div>}
      {!description && <div className="mt-2 text-sm text-gray-500">功能正在完善中。</div>}
    </div>
  );
}

