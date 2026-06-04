import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataItemViewer } from '../../components/shared/DataItemViewer';
import { AnnotationResultViewer } from '../../components/shared/AnnotationResultViewer';
import { DonutChart } from '../../components/shared/DonutChart';
import type { AnnotationSchema } from '../../components/shared/AnnotationEditor';
import { AppButton } from '../../components/shared/AppButton';

const apiBaseUrl = 'http://localhost:7000';

interface DataItem {
  id: string;
  datasetId: string;
  content: string;
  contentType: string;
  metadata: string;
  finalResult: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface TaskDetail {
  batchId: string;
  orderNo: string;
  taskId: string;
  item: DataItem;
  status: string;
  assignedAt: string;
  startedAt: string | null;
  submittedAt: string | null;
  annotationResult: string | null;
  annotationIsDisputed: boolean | null;
  annotationStatus: string | null;
  adoptionStatus: number | null;
  adoptionComment: string | null;
}

const taskStatusLabels: Record<string, string> = {
  assigned: '已分配',
  in_progress: '进行中',
  submitted: '已提交',
  returned: '已退回',
  accepted: '已通过',
  cancelled: '已取消',
};

const annotationStatusLabels: Record<string, string> = {
  submitted: '待审核',
  returned: '已退回',
  accepted: '已通过',
  rejected: '已拒绝',
};

const adoptionStatusLabels: Record<number, string> = {
  0: '未处理',
  1: '已采纳',
  2: '已拒绝',
};

const annotationStatusColors: Record<string, string> = {
  submitted: '#3b82f6',
  returned: '#ef4444',
  accepted: '#22c55e',
  rejected: '#f97316',
};

const adoptionStatusColors: Record<number, string> = {
  0: '#9ca3af',
  1: '#22c55e',
  2: '#ef4444',
};

interface TaskBatchDetailModalProps {
  batchId: string;
  onClose: () => void;
}

function parseSchema(rawSchema: string): AnnotationSchema | null {
  try {
    return JSON.parse(rawSchema) as AnnotationSchema;
  } catch {
    return null;
  }
}

export function TaskBatchDetailModal({ batchId, onClose }: TaskBatchDetailModalProps) {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<TaskDetail[]>([]);
  const [schema, setSchema] = useState<AnnotationSchema | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [viewMode, setViewMode] = useState<'summary' | 'list'>('summary');

  useEffect(() => {
    loadTaskDetails();
  }, [batchId]);

  async function loadTaskDetails() {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/task-batches/${batchId}/tasks`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `加载失败 (${response.status})`);
      }

      const loadedTasks = data as TaskDetail[];
      setTasks(loadedTasks);

      if (loadedTasks.length > 0) {
        const datasetId = loadedTasks[0].item.datasetId;
        await loadDatasetSchema(datasetId, token);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }

  async function loadDatasetSchema(datasetId: string, token: string) {
    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/datasets`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (response.ok && Array.isArray(data)) {
        const dataset = data.find((d: { id: string }) => d.id === datasetId);
        if (dataset?.annotationSchema) {
          setSchema(parseSchema(dataset.annotationSchema));
        }
      }
    } catch {
      // 静默失败，使用原始值显示
    }
  }

  function computeStats() {
    const total = tasks.length;
    if (total === 0) return null;

    const annotationStatusCounts: Record<string, number> = {};
    const adoptionStatusCounts: Record<number, number> = {};
    const commentCounts: Record<string, number> = {};

    tasks.forEach((task) => {
      const annStatus = task.annotationStatus || '未提交';
      annotationStatusCounts[annStatus] = (annotationStatusCounts[annStatus] || 0) + 1;

      if (task.adoptionStatus !== null) {
        adoptionStatusCounts[task.adoptionStatus] = (adoptionStatusCounts[task.adoptionStatus] || 0) + 1;
      }

      if (task.adoptionComment) {
        commentCounts[task.adoptionComment] = (commentCounts[task.adoptionComment] || 0) + 1;
      }
    });

    return {
      total,
      annotationStatusCounts,
      adoptionStatusCounts,
      commentCounts,
      hasComments: Object.keys(commentCounts).length > 0,
    };
  }

  const stats = computeStats();

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/45 px-6 py-8">
      <div className="flex h-full w-full max-w-5xl flex-col overflow-hidden rounded border border-gray-200 bg-white shadow-2xl">
        <div className="flex items-start justify-between border-b border-gray-200 px-6 py-5">
          <div>
            <div className="text-base font-semibold text-gray-900">任务单详情</div>
            <div className="mt-1 text-sm text-gray-500">
              {tasks[0]?.orderNo || batchId} · 共 {tasks.length} 条任务
            </div>
          </div>
          <div className="flex items-center gap-2">
            <div className="inline-flex rounded border border-gray-300 bg-white p-1">
              <AppButton
                type="button"
                className={`rounded px-4 py-1.5 text-sm font-medium ${
                  viewMode === 'summary'
                    ? 'border border-gray-400 text-gray-900'
                    : 'text-gray-600 hover:bg-gray-50'
                }`}
                onClick={() => setViewMode('summary')}
              >
                统计概览
              </AppButton>
              <AppButton
                type="button"
                className={`rounded px-4 py-1.5 text-sm font-medium ${
                  viewMode === 'list'
                    ? 'border border-gray-400 text-gray-900'
                    : 'text-gray-600 hover:bg-gray-50'
                }`}
                onClick={() => setViewMode('list')}
              >
                逐条查看
              </AppButton>
            </div>
            <AppButton
              type="button"
              className="rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50"
              onClick={onClose}
            >
              关闭
            </AppButton>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-6 py-5">
          {error && <div className="app-alert-error">{error}</div>}

          {loading ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
              正在加载任务详情...
            </div>
          ) : tasks.length === 0 ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-6 text-center text-sm text-gray-500">
              暂无任务数据
            </div>
          ) : viewMode === 'summary' ? (
            <div className="space-y-6">
              <div className="grid grid-cols-2 gap-6">
                <div className="rounded border border-gray-200">
                  <div className="border-b border-gray-200 bg-gray-50 px-4 py-3 text-sm font-medium text-gray-700">
                    标注审核状态分布
                  </div>
                  <div className="p-6">
                    {stats && (
                      <DonutChart
                        data={stats.annotationStatusCounts}
                        total={stats.total}
                        colors={annotationStatusColors}
                        labels={annotationStatusLabels}
                      />
                    )}
                  </div>
                </div>

                <div className="rounded border border-gray-200">
                  <div className="border-b border-gray-200 bg-gray-50 px-4 py-3 text-sm font-medium text-gray-700">
                    采纳状态分布
                  </div>
                  <div className="p-6">
                    {stats && (
                      <DonutChart
                        data={stats.adoptionStatusCounts}
                        total={stats.total}
                        colors={adoptionStatusColors}
                        labels={adoptionStatusLabels}
                      />
                    )}
                  </div>
                </div>
              </div>

              {stats?.hasComments && (
                <div className="rounded border border-gray-200">
                  <div className="border-b border-gray-200 bg-gray-50 px-4 py-3 text-sm font-medium text-gray-700">
                    审核意见分布
                  </div>
                  <div className="p-6">
                    <div className="space-y-4">
                      {Object.entries(stats.commentCounts).map(([comment, count]) => {
                        const percentage = Math.round((count / stats.total) * 100);
                        return (
                          <div key={comment}>
                            <div className="mb-1 flex items-center justify-between text-sm">
                              <span className="max-w-md truncate text-gray-700" title={comment}>{comment}</span>
                              <span className="shrink-0 text-gray-500">{count} 条 ({percentage}%)</span>
                            </div>
                            <div className="h-4 rounded-full bg-gray-100">
                              <div
                                className="h-4 rounded-full bg-yellow-500 transition-all"
                                style={{ width: `${percentage}%` }}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="overflow-hidden rounded border border-gray-200">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs font-medium text-gray-500">
                  <tr>
                    <th className="px-4 py-3">#序号</th>
                    <th className="px-4 py-3">数据内容</th>
                    <th className="px-4 py-3">任务状态</th>
                    <th className="px-4 py-3">标注结果</th>
                    <th className="px-4 py-3">审核状态</th>
                    <th className="px-4 py-3">采纳状态</th>
                    <th className="px-4 py-3">审核意见</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 bg-white">
                  {tasks.map((task, index) => (
                    <tr key={task.taskId}>
                      <td className="px-4 py-3 text-gray-500">{index + 1}</td>
                      <td className="max-w-md px-4 py-3 text-gray-900">
                        <DataItemViewer item={task.item} className="max-h-24" />
                      </td>
                      <td className="px-4 py-3">
                        <span className="app-badge" data-kind="status" data-status={task.status}>
                          {taskStatusLabels[task.status] || task.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        <AnnotationResultViewer
                          result={task.annotationResult}
                          schema={schema}
                        />
                      </td>
                      <td className="px-4 py-3">
                        {task.annotationStatus ? (
                          <span
                            className={`app-badge ${
                              task.annotationStatus === 'accepted'
                                ? 'bg-green-50 text-green-700'
                                : task.annotationStatus === 'rejected' || task.annotationStatus === 'returned'
                                ? 'bg-red-50 text-red-700'
                                : 'bg-blue-50 text-blue-700'
                            }`}
                          >
                            {annotationStatusLabels[task.annotationStatus] || task.annotationStatus}
                          </span>
                        ) : (
                          <span className="text-gray-400">-</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        {task.adoptionStatus !== null ? (
                          <span
                            className={`app-badge ${
                              task.adoptionStatus === 1
                                ? 'bg-green-50 text-green-700'
                                : task.adoptionStatus === 2
                                ? 'bg-red-50 text-red-700'
                                : 'bg-gray-50 text-gray-600'
                            }`}
                          >
                            {adoptionStatusLabels[task.adoptionStatus] || task.adoptionStatus}
                          </span>
                        ) : (
                          <span className="text-gray-400">-</span>
                        )}
                      </td>
                      <td className="max-w-xs px-4 py-3 text-gray-600">
                        <div className="line-clamp-2">{task.adoptionComment || '-'}</div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
