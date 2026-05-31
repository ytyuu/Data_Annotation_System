import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const apiBaseUrl = 'http://localhost:7000';

const datasetStatusLabels: Record<string, string> = {
  draft: '草稿',
  open: '已开放',
  annotating: '标注中',
  reviewing: '审核中',
  revision_required: '需调整',
  completed: '已完成',
  closed: '已关闭',
};

interface Dataset {
  id: string;
  providerId: string;
  name: string;
  description: string | null;
  annotationGuide: string | null;
  annotationSchema: string;
  status: string;
  targetCompletionRatio: string;
  itemCount: number;
  completedItemCount: number;
  createdAt: string;
  updatedAt: string;
}

export function AnnotatorOpenDatasetsPage() {
  const navigate = useNavigate();
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    loadOpenDatasets();
  }, []);

  async function loadOpenDatasets() {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/datasets`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `数据集加载失败 (${response.status})`);
      }

      setDatasets(data as Dataset[]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '数据集加载失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <div className="text-sm text-gray-500">共 {datasets.length} 个数据集</div>
        <button
          type="button"
          className="rounded border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          onClick={loadOpenDatasets}
        >
          刷新
        </button>
      </div>

      {error && <div className="app-alert-error">{error}</div>}

      {loading ? (
        <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
          正在加载数据集...
        </div>
      ) : datasets.length === 0 ? (
        <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-500">
          暂无已发布数据集
        </div>
      ) : (
        <div className="overflow-hidden rounded border border-gray-200">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50 text-xs font-medium text-gray-500">
              <tr>
                <th className="px-4 py-3">名称</th>
                <th className="px-4 py-3">状态</th>
                <th className="px-4 py-3">标注方式</th>
                <th className="px-4 py-3">数据项</th>
                <th className="px-4 py-3">更新时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200 bg-white">
              {datasets.map((dataset) => (
                <tr key={dataset.id}>
                  <td className="px-4 py-3">
                    <div className="font-medium text-gray-900">{dataset.name}</div>
                    {dataset.description && (
                      <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                        {dataset.description}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className="app-badge" data-kind="status" data-status={dataset.status}>
                      {datasetStatusLabels[dataset.status] || dataset.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {getDatasetSchemaSummary(dataset)}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {dataset.completedItemCount}/{dataset.itemCount}
                  </td>
                  <td className="px-4 py-3 text-gray-500">
                    {new Date(dataset.updatedAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function getDatasetSchemaSummary(dataset: Dataset) {
  const schema = (() => {
    try {
      return JSON.parse(dataset.annotationSchema || '{}') as {
        type?: string;
        selectionMode?: string;
        options?: unknown[];
      };
    } catch {
      return {};
    }
  })();

  if (schema.type !== 'classification') {
    return '未配置';
  }

  const mode = schema.selectionMode === 'multiple' ? '多选' : '单选';
  const count = Array.isArray(schema.options) ? schema.options.length : 0;
  return `${mode} · ${count} 个选项`;
}
