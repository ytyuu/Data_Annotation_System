import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { TaskBatchDetailModal } from './TaskBatchDetailModal';
import { AppButton } from '../../components/shared/AppButton';

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
  submitted: 'bg-gray-200 text-gray-700 ring-1 ring-inset ring-gray-300',
  returned: 'bg-gray-200 text-gray-700 ring-1 ring-inset ring-gray-300',
  accepted: 'bg-gray-200 text-gray-700 ring-1 ring-inset ring-gray-300',
  cancelled: 'bg-gray-200 text-gray-700 ring-1 ring-inset ring-gray-300',
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
            <AppButton type="button" variant="secondary" onClick={loadSubmissions}>
              刷新
            </AppButton>
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
            <div className="grid gap-4">
              {summaries.map((summary) => (
                <div
                  key={summary.datasetId}
                  className="rounded border border-gray-300 bg-white shadow-sm transition-colors hover:bg-gray-50"
                >
                  <div className="grid gap-4 px-4 py-4 lg:grid-cols-[minmax(220px,1fr)_minmax(280px,auto)_auto] lg:items-center">
                    <div>
                        <div className="font-medium text-gray-900">
                          {summary.datasetName}
                        </div>
                        <div className="mt-1 text-xs text-gray-500">
                          {summary.lastSubmittedAt
                            ? `最近提交 ${new Date(summary.lastSubmittedAt).toLocaleDateString()}`
                            : '暂无提交时间'}
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <SubmissionMetric label="任务单" value={`${summary.batchCount} 个`} />
                      <SubmissionMetric label="数据项" value={`${summary.totalItemCount} 条`} />
                    </div>

                    <div className="flex flex-wrap items-center justify-start gap-2 lg:justify-end">
                      <div className="flex flex-wrap justify-end gap-1.5">
                        {Object.entries(summary.statusCounts).map(([status, count]) => (
                          <span
                            key={status}
                            className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                              statusBadgeColors[status] || 'bg-gray-200 text-gray-700 ring-1 ring-inset ring-gray-300'
                            }`}
                          >
                            {taskBatchStatusLabels[status] || status} {count}
                          </span>
                        ))}
                      </div>
                      <AppButton
                        type="button"
                        variant="primary"
                        size="sm"
                        className="h-8"
                        onClick={() => handleViewDetail(summary.datasetId)}
                      >
                        查看记录
                      </AppButton>
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
              <AppButton
                type="button"
                variant="secondary"
                size="sm"
                className="h-8"
                onClick={handleBackToSummary}
              >
                ← 返回
              </AppButton>
              <div className="text-base font-medium text-gray-900">
                {selectedDatasetName}
              </div>
              <div className="text-sm text-gray-500">
                共 {selectedDatasetBatches.length} 个任务单
              </div>
            </div>
            <AppButton type="button" variant="secondary" onClick={loadSubmissions}>
              刷新
            </AppButton>
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
            <div className="grid gap-4">
              {selectedDatasetBatches.map((batch) => (
                <div
                  key={batch.batchId}
                  className="rounded border border-gray-300 bg-white shadow-sm transition-colors hover:bg-gray-50"
                >
                  <div className="grid gap-4 px-4 py-4 lg:grid-cols-[minmax(220px,1fr)_minmax(360px,auto)_auto] lg:items-center">
                    <div>
                        <div className="font-medium text-gray-900">
                          {batch.orderNo}
                        </div>
                        <div className="mt-1">
                          <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${statusBadgeColors[batch.status] || 'bg-gray-200 text-gray-700 ring-1 ring-inset ring-gray-300'}`}>
                            {taskBatchStatusLabels[batch.status] || batch.status}
                          </span>
                        </div>
                    </div>

                    <div className="grid grid-cols-3 gap-2">
                      <SubmissionMetric label="数据项" value={`${batch.totalCount} 条`} />
                      <SubmissionMetric label="已提交" value={`${batch.submittedCount} 条`} />
                      <SubmissionMetric label="待处理" value={`${batch.assignedCount + batch.inProgressCount} 条`} />
                    </div>

                    <div className="flex shrink-0 flex-wrap items-center justify-start gap-3 text-xs text-gray-500 lg:justify-end">
                      <span>领取 {new Date(batch.assignedAt).toLocaleDateString()}</span>
                      {batch.submittedAt && <span>提交 {new Date(batch.submittedAt).toLocaleDateString()}</span>}
                      <AppButton
                        type="button"
                        variant="primary"
                        size="sm"
                        className="h-8"
                        onClick={() => openDetailModal(batch.batchId)}
                      >
                        查看详情
                      </AppButton>
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

function SubmissionMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="app-metric min-w-28">
      <div className="app-metric-label">{label}</div>
      <div className="app-metric-value">{value}</div>
    </div>
  );
}
