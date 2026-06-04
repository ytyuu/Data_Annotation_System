import React from 'react';

export interface DataItem {
  id: string;
  datasetId: string;
  content: string;
  contentType: string;
  metadata: string;
  finalResult?: string | null;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DataItemViewerProps {
  item: DataItem;
  className?: string;
}

/**
 * 数据项内容展示组件。
 *
 * 根据 contentType 自动选择合适的展示方式：
 * - text: 纯文本展示
 * - image: 图片展示
 * - audio: 音频播放器
 * - video: 视频播放器
 * - json: 格式化 JSON
 *
 * 未来扩展新标注类型时，主要在此组件中添加新的展示方式。
 */
export const DataItemViewer: React.FC<DataItemViewerProps> = ({ item, className = '' }) => {
  const { content, contentType } = item;
  const contentFrame =
    'max-h-40 overflow-auto rounded border border-gray-300 bg-gray-50 px-3 py-2 shadow-inner';

  switch (contentType) {
    case 'image':
      return (
        <div className={`rounded border border-gray-300 bg-gray-50 p-2 ${className}`}>
          <img
            src={content}
            alt="数据项"
            className="max-h-64 w-full rounded object-contain"
          />
        </div>
      );

    case 'audio':
      return (
        <audio
          controls
          src={content}
          className={`w-full rounded border border-gray-300 bg-gray-50 ${className}`}
        >
          您的浏览器不支持音频播放
        </audio>
      );

    case 'video':
      return (
        <div className={`rounded border border-gray-300 bg-gray-50 p-2 ${className}`}>
          <video
            controls
            src={content}
            className="max-h-64 w-full rounded object-contain"
          >
            您的浏览器不支持视频播放
          </video>
        </div>
      );

    case 'json':
      return (
        <pre
          className={`whitespace-pre-wrap break-words font-mono text-xs leading-5 text-gray-700 ${contentFrame} ${className}`}
        >
          {content}
        </pre>
      );

    case 'text':
    default:
      return (
        <div className={`whitespace-pre-wrap break-words text-sm leading-6 text-gray-800 ${contentFrame} ${className}`}>
          {content}
        </div>
      );
  }
};

export default DataItemViewer;
