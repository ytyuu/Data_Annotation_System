import React from 'react';
import { AnnotationSchema } from './AnnotationEditor';
import { formatAnnotationResult } from './AnnotationResultViewer';

export interface AnnotationCardProps {
  /** 标注员名称 */
  annotatorName: string;
  /** 标注类型：annotation / review */
  annotationType: string;
  /** 标注结果 JSON */
  result: string;
  /** 备注 */
  comment?: string | null;
  /** 是否争议 */
  isDisputed?: boolean;
  /** 提交时间 */
  submittedAt: string;
  /** 标注结构 */
  schema: AnnotationSchema | null;
}

/**
 * 单条标注记录卡片组件。
 *
 * 用于在争议处理、审核等场景展示标注员的标注结果。
 */
export const AnnotationCard: React.FC<AnnotationCardProps> = ({
  annotatorName,
  annotationType,
  result,
  comment,
  isDisputed = false,
  submittedAt,
  schema,
}) => {
  const typeLabel = annotationType === 'annotation' ? '原始标注' : '互查标注';
  const displayResult = formatAnnotationResult(result, schema);

  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-900">{annotatorName}</span>
        <span className="rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-700">
          {typeLabel}
        </span>
      </div>
      <div className="mt-1 text-xs text-gray-500">
        {new Date(submittedAt).toLocaleString()}
      </div>
      <div className="mt-2 text-sm">
        <span className="font-medium text-gray-700">结果：</span>
        <span className="text-blue-700">{displayResult}</span>
      </div>
      {comment && (
        <div className="mt-1 text-sm text-gray-600">备注：{comment}</div>
      )}
      {isDisputed && (
        <span className="mt-2 inline-block rounded bg-red-100 px-2 py-0.5 text-xs text-red-700">
          存在争议
        </span>
      )}
    </div>
  );
};

export default AnnotationCard;
