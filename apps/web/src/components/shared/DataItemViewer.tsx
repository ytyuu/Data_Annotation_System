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

  switch (contentType) {
    case 'image':
      return (
        <img
          src={content}
          alt="数据项"
          className={`max-h-64 w-full rounded border border-gray-200 object-contain ${className}`}
        />
      );

    case 'audio':
      return (
        <audio
          controls
          src={content}
          className={`w-full rounded border border-gray-200 ${className}`}
        >
          您的浏览器不支持音频播放
        </audio>
      );

    case 'video':
      return (
        <video
          controls
          src={content}
          className={`max-h-64 w-full rounded border border-gray-200 object-contain ${className}`}
        >
          您的浏览器不支持视频播放
        </video>
      );

    case 'json':
      return (
        <pre
          className={`whitespace-pre-wrap rounded border border-gray-200 bg-gray-50 p-4 text-sm leading-6 text-gray-700 ${className}`}
        >
          {content}
        </pre>
      );

    case 'text':
    default:
      return (
        <div className={`whitespace-pre-wrap text-sm leading-6 text-gray-800 ${className}`}>
          {content}
        </div>
      );
  }
};

export default DataItemViewer;
