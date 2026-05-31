import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const apiBaseUrl = 'http://localhost:7000';

interface TaskGroup {
  datasetId: string;
  datasetName: string;
  totalCount: number;
  assignedCount: number;
  inProgressCount: number;
  submittedCount: number;
  lastAssignedAt: string;
}

export function AnnotatorMyTasksPage() {
  const navigate = useNavigate();
  const [groups, setGroups] = useState<TaskGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [startMessage, setStartMessage] = useState('');
  const [returningDatasetId, setReturningDatasetId] = useState<string | null>(null);
  const [returnLoading, setReturnLoading] = useState(false);

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

  function handleStartAnnotating() {
    setStartMessage('标注工作台接入后，开始标注才会加载具体数据内容。');
  }

  function openReturnDialog(datasetId: string) {
    setReturningDatasetId(datasetId);
  }

  function closeReturnDialog() {
    setReturningDatasetId(null);
  }

  async function handleReturn(datasetId: string) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setReturnLoading(true);

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/datasets/${datasetId}/return-all`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `退回失败 (${response.status})`);
      }

      setReturningDatasetId(null);
      loadGroups();
    } catch (err) {
      setError(err instanceof Error ? err.message : '退回失败');
      setReturningDatasetId(null);
    } finally {
      setReturnLoading(false);
    }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <div className="text-sm text-gray-500">共 {groups.length} 个数据集</div>
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
            <div key={group.datasetId} className="rounded border border-gray-200 bg-white">
              <div className="flex items-center justify-between px-4 py-3">
                <div className="flex items-center gap-4">
                  <div className="font-medium text-gray-900">{group.datasetName}</div>
                  <div className="flex gap-2 text-xs">
                    {group.assignedCount > 0 && (
                      <span className="rounded bg-gray-100 px-2 py-0.5 text-gray-600">
                        待开始 {group.assignedCount}
                      </span>
                    )}
                    {group.inProgressCount > 0 && (
                      <span className="rounded bg-blue-50 px-2 py-0.5 text-blue-600">
                        进行中 {group.inProgressCount}
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
                    最近分配 {new Date(group.lastAssignedAt).toLocaleString()}
                  </span>
                  <button
                    type="button"
                    className="rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50"
                    onClick={() => openReturnDialog(group.datasetId)}
                  >
                    退回
                  </button>
                  <button
                    type="button"
                    className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
                    onClick={handleStartAnnotating}
                  >
                    开始标注
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {returningDatasetId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="w-full max-w-sm rounded-lg bg-white p-6 shadow-lg">
            <h3 className="text-lg font-medium text-gray-900">确认退回</h3>
            <p className="mt-2 text-sm text-gray-600">
              确定要退回数据集「{groups.find((g) => g.datasetId === returningDatasetId)?.datasetName}」下的所有任务吗？
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
                onClick={() => handleReturn(returningDatasetId)}
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
