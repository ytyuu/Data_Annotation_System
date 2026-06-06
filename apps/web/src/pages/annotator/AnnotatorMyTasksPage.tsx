import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AnnotatorTaskWorkspaceModal } from './AnnotatorTaskWorkspaceModal';
import { TaskBatchDetailModal } from './TaskBatchDetailModal';
import { AppButton } from '../../components/shared/AppButton';
import { AppModal } from '../../components/shared/AppModal';
import { StatusBadge } from '../../components/shared/StatusBadge';

const apiBaseUrl = 'http://localhost:7000';

interface TaskGroup {
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

const taskBatchStatusLabels: Record<string, string> = {
  assigned: '待开始',
  in_progress: '进行中',
  submitted: '已提交',
  returned: '已退回',
  accepted: '已通过',
  cancelled: '已退回',
};

export function AnnotatorMyTasksPage() {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<TaskGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [returningBatchId, setReturningBatchId] = useState<string | null>(null);
  const [returnLoading, setReturnLoading] = useState(false);
  const [startingBatchId, setStartingBatchId] = useState<string | null>(null);
  const [activeBatchId, setActiveBatchId] = useState<string | null>(null);
  const [detailBatchId, setDetailBatchId] = useState<string | null>(null);

  useEffect(() => {
    loadGroups();
  }, []);

  async function loadGroups() {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/tasks?status=assigned,in_progress`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `任务加载失败 (${response.status})`);
      }

      setGroups(data as TaskGroup[]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '任务加载失败');
    } finally {
      setLoading(false);
    }
  }

  async function handleStartAnnotating(batchId: string): Promise<boolean> {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return false;
    }

    setStartingBatchId(batchId);
    setError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/task-batches/${batchId}/start`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `开始失败 (${response.status})`);
      }

      loadGroups();
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : '开始失败');
      return false;
    } finally {
      setStartingBatchId(null);
    }
  }

  async function openWorkspace(group: TaskGroup) {
    if (!['assigned', 'in_progress'].includes(group.status)) {
      return;
    }
    if (group.status === 'assigned') {
      const started = await handleStartAnnotating(group.batchId);
      if (!started) {
        return;
      }
    }
    setActiveBatchId(group.batchId);
  }

  function openReturnDialog(batchId: string) {
    setReturningBatchId(batchId);
  }

  function closeReturnDialog() {
    setReturningBatchId(null);
  }

  async function handleReturn(batchId: string) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setReturnLoading(true);

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/task-batches/${batchId}/return`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `退回失败 (${response.status})`);
      }

      setReturningBatchId(null);
      loadGroups();
    } catch (err) {
      setError(err instanceof Error ? err.message : '退回失败');
      setReturningBatchId(null);
    } finally {
      setReturnLoading(false);
    }
  }

  function openDetail(batchId: string) {
    setDetailBatchId(batchId);
  }

  function closeDetail() {
    setDetailBatchId(null);
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <div className="text-sm text-gray-500">共 {groups.length} 个任务单</div>
        <AppButton type="button" variant="secondary" disabled={loading} onClick={loadGroups}>
          {loading ? '刷新中...' : '刷新'}
        </AppButton>
      </div>

      {error && <div className="app-alert-error">{error}</div>}

      {loading ? (
        <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
          正在加载任务...
        </div>
      ) : groups.length === 0 ? (
        <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-500">
          暂无已领取的任务，去「可标注数据集」页面领取吧
        </div>
      ) : (
        <div className="grid gap-4">
          {groups.map((group) => (
            <div
              key={group.batchId}
              className="rounded border border-gray-300 bg-white shadow-sm transition-colors hover:bg-gray-50"
            >
              <div className="grid gap-4 px-4 py-4 lg:grid-cols-[minmax(220px,1fr)_minmax(360px,auto)_auto] lg:items-center">
                <div>
                  <div className="font-medium text-gray-900">{group.datasetName}</div>
                  <div className="mt-1 text-xs text-gray-500">{group.orderNo}</div>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    <StatusBadge status={group.status}>
                      {taskBatchStatusLabels[group.status] || group.status}
                    </StatusBadge>
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-2">
                  <TaskMetric label="数据项" value={`${group.totalCount} 条`} />
                  <TaskMetric label="待开始" value={`${group.assignedCount} 条`} />
                  <TaskMetric label="已完成" value={`${group.submittedCount} 条`} />
                </div>

                <div className="flex shrink-0 flex-wrap items-center justify-start gap-2 text-xs text-gray-500 lg:justify-end">
                  <span>领取 {new Date(group.assignedAt).toLocaleDateString()}</span>
                  <AppButton
                    type="button"
                    variant="primary"
                    size="sm"
                    className="h-8"
                    onClick={() => openDetail(group.batchId)}
                  >
                    查看详情
                  </AppButton>
                  {['assigned', 'in_progress'].includes(group.status) && (
                    <>
                      <AppButton
                        type="button"
                        variant="primary"
                        size="sm"
                        className="h-8"
                        onClick={() => openWorkspace(group)}
                        disabled={startingBatchId === group.batchId}
                      >
                        {startingBatchId === group.batchId ? '启动中...' : '开始标注'}
                      </AppButton>
                      <AppButton
                        type="button"
                        variant="danger"
                        size="sm"
                        className="h-8"
                        onClick={() => openReturnDialog(group.batchId)}
                      >
                        退回
                      </AppButton>
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {returningBatchId && (
        <AppModal
          title="确认退回"
          subtitle={`确定要退回任务单「${groups.find((g) => g.batchId === returningBatchId)?.orderNo}」下的所有任务吗？`}
          width="sm"
          footerClassName="border-transparent bg-white"
          footer={
            <>
              <AppButton type="button" variant="secondary" onClick={closeReturnDialog} disabled={returnLoading}>
                取消
              </AppButton>
              <AppButton
                type="button"
                variant="danger"
                onClick={() => handleReturn(returningBatchId)}
                disabled={returnLoading}
              >
                {returnLoading ? '退回中...' : '确认退回'}
              </AppButton>
            </>
          }
        />
      )}
      {activeBatchId && (
        <AnnotatorTaskWorkspaceModal
          batchId={activeBatchId}
          onClose={() => setActiveBatchId(null)}
          onSubmitted={() => {
            setActiveBatchId(null);
            loadGroups();
          }}
        />
      )}
      {detailBatchId && (
        <TaskBatchDetailModal
          batchId={detailBatchId}
          onClose={closeDetail}
        />
      )}
    </div>
  );
}

function TaskMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="app-metric min-w-28">
      <div className="app-metric-label">{label}</div>
      <div className="app-metric-value">{value}</div>
    </div>
  );
}
