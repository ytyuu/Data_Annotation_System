import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

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
  cancelled: '已取消',
};

export function AnnotatorMyTasksPage() {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<TaskGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [startMessage, setStartMessage] = useState('');
  const [returningBatchId, setReturningBatchId] = useState<string | null>(null);
  const [returnLoading, setReturnLoading] = useState(false);
  const [startingBatchId, setStartingBatchId] = useState<string | null>(null);

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
      const response = await fetch(`${apiBaseUrl}/api/annotator/tasks`, {
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

  async function handleStartAnnotating(batchId: string) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setStartingBatchId(batchId);
    setError('');
    setStartMessage('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/task-batches/${batchId}/start`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `开始失败 (${response.status})`);
      }

      const group = groups.find((item) => item.batchId === batchId);
      setStartMessage(`任务单 ${group?.orderNo || ''} 已开始，标注工作台接入后会加载具体任务项。`);
      loadGroups();
    } catch (err) {
      setError(err instanceof Error ? err.message : '开始失败');
    } finally {
      setStartingBatchId(null);
    }
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

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <div className="text-sm text-gray-500">共 {groups.length} 个任务单</div>
        <button
          type="button"
          className="rounded border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          onClick={loadGroups}
        >
          刷新
        </button>
      </div>

      {error && <div className="app-alert-error">{error}</div>}
      {startMessage && (
        <div className="mb-4 rounded border border-blue-200 bg-blue-50 p-3 text-sm text-blue-700">
          {startMessage}
        </div>
      )}

      {loading ? (
        <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
          正在加载任务...
        </div>
      ) : groups.length === 0 ? (
        <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-500">
          暂无已领取的任务，去「可标注数据集」页面领取吧
        </div>
      ) : (
        <div className="space-y-4">
          {groups.map((group) => (
            <div key={group.batchId} className="rounded border border-gray-200 bg-white">
              <div className="flex items-center justify-between px-4 py-3">
                <div className="flex items-center gap-4">
                  <div>
                    <div className="font-medium text-gray-900">{group.datasetName}</div>
                    <div className="mt-1 text-xs text-gray-500">{group.orderNo}</div>
                  </div>
                  <div className="flex gap-2 text-xs">
                    <span className="rounded bg-gray-100 px-2 py-0.5 text-gray-600">
                      {taskBatchStatusLabels[group.status] || group.status}
                    </span>
                    {group.assignedCount > 0 && (
                      <span className="rounded bg-gray-100 px-2 py-0.5 text-gray-600">
                        待开始 {group.assignedCount}
                      </span>
                    )}
                    {group.inProgressCount > 0 && (
                      <span className="rounded bg-blue-50 px-2 py-0.5 text-blue-600">
                        已完成 {group.submittedCount}/{group.totalCount}
                      </span>
                    )}
                    {group.submittedCount > 0 && (
                      <span className="rounded bg-green-50 px-2 py-0.5 text-green-600">
                        已提交 {group.submittedCount}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-gray-400">
                    领取时间 {new Date(group.assignedAt).toLocaleString()}
                  </span>
                  <button
                    type="button"
                    className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                    onClick={() => handleStartAnnotating(group.batchId)}
                    disabled={!['assigned', 'in_progress'].includes(group.status) || startingBatchId === group.batchId}
                  >
                    {startingBatchId === group.batchId ? '启动中...' : '开始标注'}
                  </button>
                  <button
                    type="button"
                    className="rounded bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                    onClick={() => openReturnDialog(group.batchId)}
                    disabled={!['assigned', 'in_progress'].includes(group.status)}
                  >
                    退回
                  </button>
                </div>
              </div>
              {group.totalCount > 0 && group.inProgressCount > 0 && (
                <div className="px-4 pb-3">
                  <div className="flex items-center gap-3 text-xs text-gray-500">
                    <span>
                      进度 {Math.min(100, Math.round((group.submittedCount / group.totalCount) * 100))}%
                    </span>
                    <div className="h-1 flex-1 rounded-full bg-gray-100">
                      <div
                        className="h-1 rounded-full bg-blue-500"
                        style={{ width: `${Math.min(100, Math.round((group.submittedCount / group.totalCount) * 100))}%` }}
                      />
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {returningBatchId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-lg">
            <h3 className="text-lg font-medium text-gray-900">确认退回</h3>
            <p className="mt-2 text-sm text-gray-600">
              确定要退回任务单「{groups.find((g) => g.batchId === returningBatchId)?.orderNo}」下的所有任务吗？
            </p>
            <div className="mt-6 flex justify-end gap-3">
              <button
                type="button"
                className="rounded border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                onClick={closeReturnDialog}
                disabled={returnLoading}
              >
                取消
              </button>
              <button
                type="button"
                className="rounded bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                onClick={() => handleReturn(returningBatchId)}
                disabled={returnLoading}
              >
                {returnLoading ? '退回中...' : '确认退回'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
