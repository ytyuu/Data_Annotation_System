import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { TaskBatchDetailModal } from './TaskBatchDetailModal';
import { AppButton } from '../../components/shared/AppButton';
import { StatusBadge } from '../../components/shared/StatusBadge';
import { AppAlert } from '../../components/shared/AppAlert';
import { EmptyState } from '../../components/shared/EmptyState';
import { PageToolbar } from '../../components/shared/PageToolbar';
import { AppTable, AppTableBody, AppTableHead, AppTableRow } from '../../components/shared/AppTable';

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
          <PageToolbar
            actions={
              <AppButton type="button" variant="secondary" disabled={loading} onClick={loadSubmissions}>
                {loading ? '刷新中...' : '刷新'}
              </AppButton>
            }
          >
            共 {summaries.length} 个数据集，{batches.length} 个任务单
          </PageToolbar>

          {error && <AppAlert kind="error" className="mb-6">{error}</AppAlert>}

          {loading ? (
            <EmptyState>正在加载提交记录...</EmptyState>
          ) : batches.length === 0 ? (
            <EmptyState align="center">暂无提交记录</EmptyState>
          ) : (
            <AppTable>
              <AppTableHead>
                <tr>
                  <th className="w-[34%] px-4 py-3 text-left">数据集</th>
                  <th className="px-4 py-3 text-left">任务单</th>
                  <th className="px-4 py-3 text-left">数据项</th>
                  <th className="px-4 py-3 text-left">状态</th>
                  <th className="px-4 py-3 text-left">最近提交</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </AppTableHead>
              <AppTableBody>
                {summaries.map((summary) => (
                  <AppTableRow key={summary.datasetId} className="align-top">
                    <td className="px-4 py-4 align-middle">
                      <div className="text-base font-semibold text-gray-900">{summary.datasetName}</div>
                      <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                        {summary.datasetId}
                      </div>
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-600">
                      {summary.batchCount} 个
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-600">
                      {summary.totalItemCount} 条
                    </td>
                    <td className="px-4 py-4 align-middle">
                      <div className="flex flex-wrap gap-1.5">
                        {Object.entries(summary.statusCounts).map(([status, count]) => (
                          <StatusBadge key={status} status={status}>
                            {taskBatchStatusLabels[status] || status} {count}
                          </StatusBadge>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-500">
                      {summary.lastSubmittedAt
                        ? new Date(summary.lastSubmittedAt).toLocaleString()
                        : '暂无提交时间'}
                    </td>
                    <td className="px-4 py-4 align-middle text-right">
                      <AppButton
                        type="button"
                        variant="primary"
                        size="sm"
                        onClick={() => handleViewDetail(summary.datasetId)}
                      >
                        查看记录
                      </AppButton>
                    </td>
                  </AppTableRow>
                ))}
              </AppTableBody>
            </AppTable>
          )}
        </>
      ) : (
        <>
          <PageToolbar
            actions={
              <AppButton type="button" variant="secondary" disabled={loading} onClick={loadSubmissions}>
                {loading ? '刷新中...' : '刷新'}
              </AppButton>
            }
          >
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
          </PageToolbar>

          {error && <AppAlert kind="error" className="mb-6">{error}</AppAlert>}

          {loading ? (
            <EmptyState>正在加载...</EmptyState>
          ) : selectedDatasetBatches.length === 0 ? (
            <EmptyState align="center">该数据集下暂无提交记录</EmptyState>
          ) : (
            <AppTable>
              <AppTableHead>
                <tr>
                  <th className="w-[28%] px-4 py-3 text-left">任务单</th>
                  <th className="px-4 py-3 text-left">状态</th>
                  <th className="px-4 py-3 text-left">数据项</th>
                  <th className="px-4 py-3 text-left">待处理</th>
                  <th className="px-4 py-3 text-left">领取时间</th>
                  <th className="px-4 py-3 text-left">提交时间</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </AppTableHead>
              <AppTableBody>
                {selectedDatasetBatches.map((batch) => (
                  <AppTableRow key={batch.batchId} className="align-top">
                    <td className="px-4 py-4 align-middle">
                      <div className="text-base font-semibold text-gray-900">{batch.orderNo}</div>
                      <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                        {batch.datasetName}
                      </div>
                    </td>
                    <td className="px-4 py-4 align-middle">
                      <StatusBadge status={batch.status}>
                        {taskBatchStatusLabels[batch.status] || batch.status}
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-600">
                      <div className="text-sm text-gray-900">{batch.submittedCount} / {batch.totalCount}</div>
                      <div className="mt-1 text-xs text-gray-500">已提交 / 总数</div>
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-600">
                      {batch.assignedCount + batch.inProgressCount} 条
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-500">
                      {new Date(batch.assignedAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-500">
                      {batch.submittedAt ? new Date(batch.submittedAt).toLocaleString() : '未提交'}
                    </td>
                    <td className="px-4 py-4 align-middle text-right">
                      <AppButton
                        type="button"
                        variant="primary"
                        size="sm"
                        onClick={() => openDetailModal(batch.batchId)}
                      >
                        查看详情
                      </AppButton>
                    </td>
                  </AppTableRow>
                ))}
              </AppTableBody>
            </AppTable>
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
