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
  canClaim: boolean;
}

export function AnnotatorOpenDatasetsPage() {
  const navigate = useNavigate();
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [claimingDatasetId, setClaimingDatasetId] = useState<string | null>(null);
  const [claimStep, setClaimStep] = useState<1 | 2>(1);
  const [claimTaskType, setClaimTaskType] = useState<'annotation' | 'review'>('annotation');
  const [claimCount, setClaimCount] = useState(20);
  const [claimLoading, setClaimLoading] = useState(false);
  const [claimError, setClaimError] = useState('');
  const [claimSuccess, setClaimSuccess] = useState('');

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

  async function handleClaim(datasetId: string) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setClaimLoading(true);
    setClaimError('');
    setClaimSuccess('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/datasets/${datasetId}/claim`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ count: claimCount, taskType: claimTaskType }),
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `领取失败 (${response.status})`);
      }

      setClaimSuccess(`成功领取任务单 ${data?.orderNo || ''}，共 ${data?.assignedCount || 0} 条任务`);
      setClaimingDatasetId(null);
      loadOpenDatasets();
    } catch (err) {
      setClaimError(err instanceof Error ? err.message : '领取失败');
    } finally {
      setClaimLoading(false);
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
      {claimSuccess && <div className="app-alert-success">{claimSuccess}</div>}

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
                <th className="px-4 py-3 text-right">操作</th>
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
                  <td className="px-4 py-3 text-right">
                    {!dataset.canClaim ? (
                      <button
                        type="button"
                        disabled
                        className="rounded bg-gray-200 px-3 py-1.5 text-sm font-medium text-gray-400 cursor-not-allowed"
                        title="已持有该数据集任务或已达上限"
                      >
                        不可领取
                      </button>
                    ) : claimingDatasetId === dataset.id ? (
                      claimStep === 1 ? (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            type="button"
                            className={`rounded px-3 py-1 text-sm font-medium transition-colors ${
                              claimTaskType === 'annotation'
                                ? 'bg-blue-600 text-white'
                                : 'border border-gray-300 text-gray-600 hover:bg-gray-50'
                            }`}
                            onClick={() => setClaimTaskType('annotation')}
                          >
                            标注任务
                          </button>
                          <button
                            type="button"
                            className={`rounded px-3 py-1 text-sm font-medium transition-colors ${
                              claimTaskType === 'review'
                                ? 'bg-purple-600 text-white'
                                : 'border border-gray-300 text-gray-600 hover:bg-gray-50'
                            }`}
                            onClick={() => setClaimTaskType('review')}
                          >
                            互查任务
                          </button>
                          <button
                            type="button"
                            className="rounded bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700"
                            onClick={() => setClaimStep(2)}
                          >
                            下一步
                          </button>
                          <button
                            type="button"
                            className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-600 hover:bg-gray-50"
                            onClick={() => {
                              setClaimingDatasetId(null);
                              setClaimStep(1);
                              setClaimError('');
                            }}
                          >
                            取消
                          </button>
                        </div>
                      ) : (
                        <div className="flex items-center justify-end gap-2">
                          <span className="text-xs text-gray-500">
                            {claimTaskType === 'annotation' ? '标注任务' : '互查任务'}
                          </span>
                          <input
                            type="number"
                            min={1}
                            max={100}
                            value={claimCount}
                            onChange={(e) => setClaimCount(Math.max(1, parseInt(e.target.value, 10) || 1))}
                            className="w-16 rounded border border-gray-300 px-2 py-1 text-right text-sm"
                          />
                          <button
                            type="button"
                            className="rounded bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                            onClick={() => handleClaim(dataset.id)}
                            disabled={claimLoading}
                          >
                            {claimLoading ? '...' : '确认'}
                          </button>
                          <button
                            type="button"
                            className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-600 hover:bg-gray-50"
                            onClick={() => setClaimStep(1)}
                          >
                            返回
                          </button>
                          <button
                            type="button"
                            className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-600 hover:bg-gray-50"
                            onClick={() => {
                              setClaimingDatasetId(null);
                              setClaimStep(1);
                              setClaimError('');
                            }}
                          >
                            取消
                          </button>
                        </div>
                      )
                    ) : (
                      <button
                        type="button"
                        className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
                        onClick={() => {
                          setClaimingDatasetId(dataset.id);
                          setClaimStep(1);
                          setClaimTaskType('annotation');
                          setClaimCount(1);
                          setClaimError('');
                        }}
                      >
                        领取任务
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {claimError && (
        <div className="mt-3 app-alert-error">{claimError}</div>
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
