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

  const [claimOpen, setClaimOpen] = useState(false);
  const [claimDataset, setClaimDataset] = useState<Dataset | null>(null);
  const [claimTaskType, setClaimTaskType] = useState<'annotation' | 'review'>('annotation');
  const [claimCount, setClaimCount] = useState(1);
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

  function openClaimModal(dataset: Dataset) {
    setClaimDataset(dataset);
    setClaimTaskType('annotation');
    setClaimCount(1);
    setClaimError('');
    setClaimOpen(true);
  }

  function closeClaimModal() {
    setClaimOpen(false);
    setClaimDataset(null);
    setClaimError('');
  }

  async function handleClaim() {
    if (!claimDataset) return;

    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setClaimLoading(true);
    setClaimError('');
    setClaimSuccess('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/datasets/${claimDataset.id}/claim`, {
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
      closeClaimModal();
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
                    ) : (
                      <button
                        type="button"
                        className="rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
                        onClick={() => openClaimModal(dataset)}
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

      {/* 领取任务弹窗 */}
      {claimOpen && claimDataset && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
          onClick={(e) => {
            if (e.target === e.currentTarget) closeClaimModal();
          }}
        >
          <div className="w-full max-w-md rounded-lg border border-gray-200 bg-white shadow-lg">
            <div className="border-b border-gray-100 px-6 py-4">
              <h3 className="text-base font-semibold text-gray-900">领取任务</h3>
              <p className="mt-1 text-sm text-gray-500">{claimDataset.name}</p>
            </div>

            <div className="px-6 py-5 space-y-5">
              {/* 任务类别 */}
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">任务类别</label>
                <div className="flex gap-3">
                  <button
                    type="button"
                    onClick={() => setClaimTaskType('annotation')}
                    style={{ outline: 'none' }}
                    className={`flex-1 rounded-lg border px-4 py-3 text-sm font-medium transition-all ${
                      claimTaskType === 'annotation'
                        ? 'border-blue-600 bg-blue-600 text-white'
                        : 'border-gray-200 text-gray-600 hover:border-gray-300 hover:bg-gray-50'
                    }`}
                  >
                    <div className="text-center">标注任务</div>
                    <div className={`mt-1 text-xs ${claimTaskType === 'annotation' ? 'text-blue-100' : 'text-gray-400'}`}>
                      对未标注数据进行首次标注
                    </div>
                  </button>
                  <button
                    type="button"
                    onClick={() => setClaimTaskType('review')}
                    style={{ outline: 'none' }}
                    className={`flex-1 rounded-lg border px-4 py-3 text-sm font-medium transition-all ${
                      claimTaskType === 'review'
                        ? 'border-blue-600 bg-blue-600 text-white'
                        : 'border-gray-200 text-gray-600 hover:border-gray-300 hover:bg-gray-50'
                    }`}
                  >
                    <div className="text-center">互查任务</div>
                    <div className={`mt-1 text-xs ${claimTaskType === 'review' ? 'text-blue-100' : 'text-gray-400'}`}>
                      对已标注数据进行复核
                    </div>
                  </button>
                </div>
              </div>

              {/* 领取数量 */}
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">领取数量</label>
                <input
                  type="number"
                  min={1}
                  max={100}
                  value={claimCount}
                  onChange={(e) => setClaimCount(Math.max(1, parseInt(e.target.value, 10) || 1))}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-gray-400 focus:outline-none"
                />
                <p className="mt-1 text-xs text-gray-400">请输入 1~100 之间的数量</p>
              </div>

              {claimError && (
                <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-600">{claimError}</div>
              )}
            </div>

            <div className="flex items-center justify-end gap-3 border-t border-gray-100 px-6 py-4">
              <button
                type="button"
                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                onClick={closeClaimModal}
                disabled={claimLoading}
              >
                取消
              </button>
              <button
                type="button"
                className="rounded-lg bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-800 disabled:opacity-50"
                onClick={handleClaim}
                disabled={claimLoading}
              >
                {claimLoading ? '领取中...' : '确认领取'}
              </button>
            </div>
          </div>
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
