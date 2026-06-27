import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AnnotatorTaskWorkspaceModal } from './AnnotatorTaskWorkspaceModal';
import { TaskBatchDetailModal } from './TaskBatchDetailModal';
import { AppButton } from '../../components/shared/AppButton';
import { AppModal } from '../../components/shared/AppModal';
import { StatusBadge } from '../../components/shared/StatusBadge';
import { AppAlert } from '../../components/shared/AppAlert';
import { EmptyState } from '../../components/shared/EmptyState';
import { PageToolbar } from '../../components/shared/PageToolbar';
import { AppTable, AppTableBody, AppTableHead, AppTableRow } from '../../components/shared/AppTable';

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
      <PageToolbar
        actions={
          <AppButton type="button" variant="secondary" disabled={loading} onClick={loadGroups}>
            {loading ? '刷新中...' : '刷新'}
          </AppButton>
        }
      >
        共 {groups.length} 个任务单
      </PageToolbar>

      {error && <AppAlert kind="error" className="mb-6">{error}</AppAlert>}

      {loading ? (
        <EmptyState>正在加载任务...</EmptyState>
      ) : groups.length === 0 ? (
        <EmptyState align="center">暂无已领取的任务，去「可标注数据集」页面领取吧</EmptyState>
      ) : (
        <AppTable>
          <AppTableHead>
            <tr>
              <th className="w-[30%] px-4 py-3 text-left">数据集</th>
              <th className="px-4 py-3 text-left">状态</th>
              <th className="px-4 py-3 text-left">数据项</th>
              <th className="px-4 py-3 text-left">领取时间</th>
              <th className="px-4 py-3 text-right">操作</th>
            </tr>
          </AppTableHead>
          <AppTableBody>
            {groups.map((group) => (
              <AppTableRow key={group.batchId} className="align-top">
                <td className="px-4 py-4 align-middle">
                  <div className="text-base font-semibold text-gray-900">{group.datasetName}</div>
                  <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                    {group.orderNo}
                  </div>
                </td>
                <td className="px-4 py-4 align-middle">
                  <StatusBadge status={group.status}>
                    {taskBatchStatusLabels[group.status] || group.status}
                  </StatusBadge>
                </td>
                <td className="px-4 py-4 align-middle text-gray-600">
                  <div className="text-sm text-gray-900">{group.submittedCount} / {group.totalCount}</div>
                  <div className="mt-1 text-xs text-gray-500">已完成 / 总数</div>
                </td>
                <td className="px-4 py-4 align-middle text-gray-500">
                  {new Date(group.assignedAt).toLocaleString()}
                </td>
                <td className="px-4 py-4 align-middle">
                  <div className="flex justify-end gap-2">
                    <AppButton
                      type="button"
                      variant="secondary"
                      size="sm"
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
                          onClick={() => openWorkspace(group)}
                          disabled={startingBatchId === group.batchId}
                        >
                          {startingBatchId === group.batchId ? '启动中...' : '开始标注'}
                        </AppButton>
                        <AppButton
                          type="button"
                          variant="danger"
                          size="sm"
                          onClick={() => openReturnDialog(group.batchId)}
                        >
                          退回
                        </AppButton>
                      </>
                    )}
                  </div>
                </td>
              </AppTableRow>
            ))}
          </AppTableBody>
        </AppTable>
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
