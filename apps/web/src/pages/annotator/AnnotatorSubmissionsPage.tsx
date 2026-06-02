import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { TaskBatchDetailModal } from './TaskBatchDetailModal';

const apiBaseUrl = 'http://localhost:7000';

interface TaskBatch {
  batchId: string;
  orderNo: string;
  datasetId: string;
  datasetName: string;
  status: string;
  totalCount: number;
  assignedCount: number;
  inProgressCount: number;
  submittedCount: number;
  assignedAt: string;
  startedAt: string | null;
  submittedAt: string | null;
}

interface DatasetSummary {
  datasetId: string;
  datasetName: string;
  batchCount: number;
  totalItemCount: number;
  statusCounts: Record<string, number>;
  lastSubmittedAt: string | null;
}

const taskBatchStatusLabels: Record<string, string> = {
  assigned: '待开始',
  in_progress: '进行中',
  submitted: '已提交',
  returned: '已退回',
  accepted: '已通过',
  cancelled: '已退回',
};

const statusBadgeColors: Record<string, string> = {
  submitted: 'bg-blue-50 text-blue-600',
  returned: 'bg-red-50 text-red-600',
  accepted: 'bg-green-50 text-green-600',
  cancelled: 'bg-gray-50 text-gray-600',
};

function computeDatasetSummaries(batches: TaskBatch[]): DatasetSummary[] {
  const grouped = new Map<string, TaskBatch[]>();
  batches.forEach((batch) => {
    const list = grouped.get(batch.datasetId) || [];
    list.push(batch);
    grouped.set(batch.datasetId, list);
  });

  return Array.from(grouped.entries())
    .map(([datasetId, list]) => {
      const statusCounts: Record<string, number> = {};
      let totalItemCount = 0;
      let lastSubmittedAt: string | null = null;

      list.forEach((batch) => {
        statusCounts[batch.status] = (statusCounts[batch.status] || 0) + 1;
        totalItemCount += batch.totalCount;
        if (batch.submittedAt) {
          if (!lastSubmittedAt || batch.submittedAt > lastSubmittedAt) {
            lastSubmittedAt = batch.submittedAt;
          }
        }
      });

      return {
        datasetId,
        datasetName: list[0].datasetName,
        batchCount: list.length,
        totalItemCount,
        statusCounts,
        lastSubmittedAt,
      };
    })
    .sort((a, b) => {
      if (a.lastSubmittedAt && b.lastSubmittedAt) {
        return a.lastSubmittedAt < b.lastSubmittedAt ? 1 : -1;
      }
      return b.batchCount - a.batchCount;
    });
}

export function AnnotatorSubmissionsPage() {
  const navigate = useNavigate();
  const [view, setView] = useState<'summary' | 'detail'>('summary');
  const [selectedDatasetId, setSelectedDatasetId] = useState<string | null>(null);
  const [batches, setBatches] = useState<TaskBatch[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [detailBatchId, setDetailBatchId] = useState<string | null>(null);

  useEffect(() => {
    loadSubmissions();
  }, []);

  async function loadSubmissions() {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch(
        `${apiBaseUrl}/api/annotator/tasks?status=submitted,returned,cancelled,accepted`,
        { headers: { Authorization: `Bearer ${token}` } },
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `加载失败 (${response.status})`);
      }

      setBatches(data as TaskBatch[]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  const summaries = useMemo(() => computeDatasetSummaries(batches), [batches]);

  const selectedDatasetBatches = useMemo(() => {
    if (!selectedDatasetId) return [];
    return batches
      .filter((b) => b.datasetId === selectedDatasetId)
      .sort((a, b) => {
        const aTime = a.submittedAt || a.assignedAt;
        const bTime = b.submittedAt || b.assignedAt;
        return bTime.localeCompare(aTime);
      });
  }, [batches, selectedDatasetId]);

  const selectedDatasetName = useMemo(() => {
    if (!selectedDatasetId) return '';
    return batches.find((b) => b.datasetId === selectedDatasetId)?.datasetName || '';
  }, [batches, selectedDatasetId]);

  function handleViewDetail(datasetId: string) {
    setSelectedDatasetId(datasetId);
    setView('detail');
  }

  function handleBackToSummary() {
    setView('summary');
    setSelectedDatasetId(null);
  }

  function openDetailModal(batchId: string) {
    setDetailBatchId(batchId);
  }

  function closeDetailModal() {
    setDetailBatchId(null);
  }

  return (
    <div>
      {view === 'summary' ? (
        <>
          <div className="mb-4 flex items-center justify-between">
            <div className="text-sm text-gray-500">
              共 {summaries.length} 个数据集，{batches.length} 个任务单
            </div>
            <button
              type="button"
              className="rounded border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              onClick={loadSubmissions}
            >
              刷新
            </button>
          </div>

          {error && <div className="app-alert-error">{error}</div>}

          {loading ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
              正在加载提交记录...
            </div>
          ) : batches.length === 0 ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-500">
              暂无提交记录
            </div>
          ) : (
            <div className="space-y-4">
              {summaries.map((summary) => (
                <div
                  key={summary.datasetId}
                  className="rounded border border-gray-200 bg-white"
                >
                  <div className="flex items-center justify-between gap-4 px-4 py-3">
                    <div className="flex flex-1 items-center gap-4">
                      <div>
                        <div className="font-medium text-gray-900">
                          {summary.datasetName}
                        </div>
                        <div className="mt-1 flex items-center gap-3 text-xs text-gray-500">
                          <span>{summary.batchCount} 个任务单</span>
                          <span>·</span>
                          <span>{summary.totalItemCount} 条数据</span>
                        </div>
                      </div>
                      <div className="ml-auto flex flex-wrap justify-end gap-1.5">
                        {Object.entries(summary.statusCounts).map(
                          ([status, count]) => (
                            <span
                              key={status}
                              className={`rounded px-2 py-0.5 text-xs ${
                                statusBadgeColors[status] ||
                                'bg-gray-100 text-gray-600'
                              }`}
                            >
                              {taskBatchStatusLabels[status] || status} {count}
                            </span>
                          ),
                        )}
                      </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <button
                        type="button"
                        className="rounded border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
                        onClick={() => handleViewDetail(summary.datasetId)}
                      >
                        查看记录
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <>
          <div className="mb-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <button
                type="button"
                className="rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
                onClick={handleBackToSummary}
              >
                ← 返回
              </button>
              <div className="text-base font-medium text-gray-900">
                {selectedDatasetName}
              </div>
              <div className="text-sm text-gray-500">
                共 {selectedDatasetBatches.length} 个任务单
              </div>
            </div>
            <button
              type="button"
              className="rounded border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              onClick={loadSubmissions}
            >
              刷新
            </button>
          </div>

          {error && <div className="app-alert-error">{error}</div>}

          {loading ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
              正在加载...
            </div>
          ) : selectedDatasetBatches.length === 0 ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-500">
              该数据集下暂无提交记录
            </div>
          ) : (
            <div className="space-y-4">
              {selectedDatasetBatches.map((batch) => (
                <div
                  key={batch.batchId}
                  className="rounded border border-gray-200 bg-white"
                >
                  <div className="flex items-center justify-between gap-4 px-4 py-3">
                    <div className="flex flex-1 items-center gap-4">
                      <div>
                        <div className="font-medium text-gray-900">
                          {batch.orderNo}
                        </div>
                        <div className="mt-1 flex items-center gap-3 text-xs text-gray-500">
                          <span>
                            {taskBatchStatusLabels[batch.status] || batch.status}
                          </span>
                          <span>·</span>
                          <span>{batch.totalCount} 条数据</span>
                        </div>
                      </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-3 text-xs text-gray-400">
                      <span>
                        领取{' '}
                        {new Date(batch.assignedAt).toLocaleDateString()}
                      </span>
                      {batch.submittedAt && (
                        <span>
                          提交{' '}
                          {new Date(batch.submittedAt).toLocaleDateString()}
                        </span>
                      )}
                      <button
                        type="button"
                        className="rounded border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
                        onClick={() => openDetailModal(batch.batchId)}
                      >
                        查看详情
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {detailBatchId && (
        <TaskBatchDetailModal
          batchId={detailBatchId}
          onClose={closeDetailModal}
        />
      )}
    </div>
  );
}
