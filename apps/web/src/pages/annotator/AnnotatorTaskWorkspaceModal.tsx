import { useEffect, useMemo, useRef, useState } from 'react';
import { DataItemViewer } from '../../components/shared/DataItemViewer';
import { AnnotationEditor } from '../../components/shared/AnnotationEditor';
import { parseAnnotationSelection } from '../../components/shared/AnnotationResultViewer';
import type { AnnotationSchema } from '../../components/shared/AnnotationEditor';
import { buildAnnotationResult } from '../../components/shared/AnnotationResultBuilder';

const apiBaseUrl = 'http://localhost:7000';

interface TaskWorkspaceItem {
  id: string;
  datasetId: string;
  content: string;
  contentType: string;
  metadata: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface TaskWorkspaceTask {
  batchId: string;
  orderNo: string;
  taskId: string;
  item: TaskWorkspaceItem;
  status: string;
  assignedAt: string;
  startedAt: string | null;
  submittedAt: string | null;
  annotationResult?: string | null;
  annotationIsDisputed?: boolean | null;
}

interface TaskWorkspaceResponse {
  batchId: string;
  orderNo: string;
  datasetId: string;
  datasetName: string;
  annotationGuide: string | null;
  annotationSchema: string;
  totalCount: number;
  submittedCount: number;
  tasks: TaskWorkspaceTask[];
}

interface DraftResult {
  taskId: string;
  itemId: string;
  selection: string[];
}

interface AnnotatorTaskWorkspaceModalProps {
  batchId: string;
  onClose: () => void;
  onSubmitted: () => void;
}

function loadDrafts(storageKey: string): Record<string, DraftResult> {
  const raw = localStorage.getItem(storageKey);
  if (!raw) {
    return {};
  }
  try {
    const parsed = JSON.parse(raw) as Record<string, DraftResult>;
    return parsed ?? {};
  } catch {
    return {};
  }
}

function saveDrafts(storageKey: string, drafts: Record<string, DraftResult>) {
  localStorage.setItem(storageKey, JSON.stringify(drafts));
}

function parseSchema(rawSchema: string): AnnotationSchema | null {
  try {
    return JSON.parse(rawSchema) as AnnotationSchema;
  } catch {
    return null;
  }
}

export function AnnotatorTaskWorkspaceModal({ batchId, onClose, onSubmitted }: AnnotatorTaskWorkspaceModalProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [workspace, setWorkspace] = useState<TaskWorkspaceResponse | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [showResults, setShowResults] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [isAdvancing, setIsAdvancing] = useState(false);
  const [lastCompletedTaskId, setLastCompletedTaskId] = useState<string | null>(null);
  const advanceTimeoutRef = useRef<number | null>(null);
  const autoAdvanceTaskIdRef = useRef<string | null>(null);

  const storageKey = `annotator-batch-${batchId}`;
  const [drafts, setDrafts] = useState<Record<string, DraftResult>>(() => loadDrafts(storageKey));

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      return;
    }

    setLoading(true);
    setError('');

    fetch(`${apiBaseUrl}/api/annotator/task-batches/${batchId}/workspace`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then(async (response) => {
        const data = await response.json().catch(() => null);
        if (!response.ok) {
          throw new Error(data?.message || `任务加载失败 (${response.status})`);
        }
        setWorkspace(data as TaskWorkspaceResponse);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : '任务加载失败');
      })
      .finally(() => {
        setLoading(false);
      });
  }, [batchId]);

  useEffect(() => {
    saveDrafts(storageKey, drafts);
  }, [drafts, storageKey]);

  useEffect(
    () => () => {
      if (advanceTimeoutRef.current !== null) {
        window.clearTimeout(advanceTimeoutRef.current);
      }
    },
    []
  );

  const schema = useMemo(() => {
    return workspace ? parseSchema(workspace.annotationSchema) : null;
  }, [workspace]);

  const tasks = workspace?.tasks ?? [];
  const currentTask = tasks[currentIndex];
  const completedCount = Object.keys(drafts).length;
  const totalCount = workspace?.totalCount ?? tasks.length;

  const selection = currentTask ? drafts[currentTask.taskId]?.selection ?? [] : [];

  useEffect(() => {
    if (!workspace) {
      return;
    }
    setDrafts((prev) => {
      const next = { ...prev };
      workspace.tasks.forEach((task) => {
        if (next[task.taskId] || !task.annotationResult) {
          return;
        }
        const sel = parseAnnotationSelection(task.annotationResult, schema);
        if (sel.length === 0) {
          return;
        }
        next[task.taskId] = {
          taskId: task.taskId,
          itemId: task.item.id,
          selection: sel,
        };
      });
      return next;
    });
  }, [schema, workspace]);

  useEffect(() => {
    if (!currentTask) {
      return;
    }
    const selectionForCurrent = drafts[currentTask.taskId]?.selection ?? [];
    if (selectionForCurrent.length === 0) {
      return;
    }
    if (autoAdvanceTaskIdRef.current !== currentTask.taskId) {
      return;
    }

    const nextIndex = Math.min(currentIndex + 1, tasks.length - 1);
    autoAdvanceTaskIdRef.current = null;

    if (nextIndex === currentIndex) {
      setIsAdvancing(false);
      return;
    }

    setIsAdvancing(true);
    if (advanceTimeoutRef.current !== null) {
      window.clearTimeout(advanceTimeoutRef.current);
    }
    advanceTimeoutRef.current = window.setTimeout(() => {
      setIsAdvancing(false);
      setCurrentIndex(nextIndex);
    }, 450);
  }, [currentIndex, currentTask, drafts, tasks.length]);

  function handleSelectionChange(newSelection: string[]) {
    if (!currentTask) {
      return;
    }

    setDrafts((prev) => {
      const next = { ...prev };
      next[currentTask.taskId] = {
        taskId: currentTask.taskId,
        itemId: currentTask.item.id,
        selection: newSelection,
      };

      if (newSelection.length > 0) {
        setLastCompletedTaskId(currentTask.taskId);
        autoAdvanceTaskIdRef.current = currentTask.taskId;
      }

      return next;
    });
  }

  function goToIndex(index: number) {
    if (index < 0 || index >= tasks.length) {
      return;
    }
    setCurrentIndex(index);
  }

  async function handleSubmitBatch() {
    if (!workspace) {
      return;
    }
    if (completedCount === 0) {
      setError('请至少完成一条标注后再提交');
      return;
    }
    const token = localStorage.getItem('token');
    if (!token) {
      return;
    }

    setSubmitting(true);
    setError('');

    try {
      const submissions = tasks
        .map((task) => {
          const draft = drafts[task.taskId];
          if (!draft) {
            return null;
          }
          return {
            taskId: task.taskId,
            itemId: task.item.id,
            result: buildAnnotationResult(draft.selection, schema),
          };
        })
        .filter(Boolean);

      const response = await fetch(`${apiBaseUrl}/api/annotator/task-batches/${batchId}/submit`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ submissions }),
      });
      const data = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(data?.message || `提交失败 (${response.status})`);
      }

      localStorage.removeItem(storageKey);
      setDrafts({});
      onSubmitted();
    } catch (err) {
      setError(err instanceof Error ? err.message : '提交失败');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="flex h-full w-full max-w-5xl flex-col overflow-hidden rounded-lg bg-white shadow-lg">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <div>
            <div className="text-xl font-semibold text-gray-900">{workspace?.datasetName || '标注工作台'}</div>
            {workspace && <div className="mt-1 text-sm text-gray-500">任务单 {workspace.orderNo}</div>}
          </div>
          <button
            type="button"
            className="rounded border border-gray-300 px-4 py-2 text-base text-gray-600 hover:bg-gray-50"
            onClick={onClose}
          >
            关闭
          </button>
        </div>

        {loading ? (
          <div className="p-6 text-base text-gray-500">正在加载任务...</div>
        ) : error ? (
          <div className="p-6 text-base text-red-600">{error}</div>
        ) : workspace ? (
          <div className="flex h-full flex-1 flex-col gap-4 overflow-hidden p-6">
            <div className="flex flex-1 gap-6 overflow-hidden">
              <div className="flex flex-1 flex-col gap-4 overflow-hidden">
                <div className="flex items-center justify-between text-base text-gray-600">
                  <div>
                    当前任务 {currentIndex + 1}/{tasks.length}
                  </div>
                  <div>
                    已完成 {completedCount}/{totalCount}
                  </div>
                </div>

                {currentTask ? (
                  <div
                    className={`flex flex-1 flex-col gap-4 overflow-hidden rounded border border-gray-200 p-4 transition-opacity duration-300 ${
                      isAdvancing ? 'opacity-50' : 'opacity-100'
                    }`}
                  >
                    <div className="text-base font-medium text-gray-700">数据内容</div>
                    <div className="flex-1 overflow-auto">
                      <DataItemViewer
                        item={{
                          id: currentTask.item.id,
                          datasetId: currentTask.item.datasetId,
                          content: currentTask.item.content,
                          contentType: currentTask.item.contentType,
                          metadata: currentTask.item.metadata,
                        }}
                      />
                    </div>

                    <div className="border-t border-gray-200 pt-4">
                      <div className="text-base font-medium text-gray-700">标注结果</div>
                      <div className="mt-4">
                        <AnnotationEditor
                          schema={schema}
                          selection={selection}
                          onChange={handleSelectionChange}
                        />
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="rounded border border-gray-200 p-6 text-base text-gray-500">暂无任务</div>
                )}

                <div className="flex items-center justify-between">
                  <div className="flex gap-2">
                    <button
                      type="button"
                      className="rounded border border-gray-300 px-5 py-2.5 text-base text-gray-700 hover:bg-gray-50"
                      onClick={() => goToIndex(currentIndex - 1)}
                      disabled={currentIndex === 0}
                    >
                      上一条
                    </button>
                    <button
                      type="button"
                      className="rounded border border-gray-300 px-5 py-2.5 text-base text-gray-700 hover:bg-gray-50"
                      onClick={() => goToIndex(currentIndex + 1)}
                      disabled={currentIndex >= tasks.length - 1}
                    >
                      下一条
                    </button>
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      className="rounded border border-gray-300 px-5 py-2.5 text-base text-gray-700 hover:bg-gray-50"
                      onClick={() => setShowResults((prev) => !prev)}
                    >
                      {showResults ? '收起结果' : '查看已标注结果'}
                    </button>
                    <button
                      type="button"
                      className="rounded bg-green-600 px-5 py-2.5 text-base font-medium text-white hover:bg-green-700 disabled:opacity-50"
                      onClick={handleSubmitBatch}
                      disabled={submitting || completedCount === 0}
                    >
                      {submitting ? '提交中...' : '提交任务单'}
                    </button>
                  </div>
                </div>
              </div>

              <div className="flex w-72 shrink-0 flex-col gap-4 overflow-hidden">
                <div className="shrink-0 rounded border border-gray-200 bg-gray-50 p-4 text-base text-gray-700">
                  <div className="font-medium text-gray-900">标注说明</div>
                  <div className="mt-2 whitespace-pre-wrap">
                    {workspace.annotationGuide || '暂无标注说明'}
                  </div>
                </div>

                {showResults && (
                  <div className="flex min-h-0 flex-1 flex-col rounded border border-gray-200 bg-gray-50 p-4">
                    <div className="text-base font-medium text-gray-700">已标注结果</div>
                    <div className="mt-3 min-h-0 flex-1 space-y-2 overflow-auto pr-1 text-sm text-gray-600">
                      {tasks.map((task, index) => {
                        const draft = drafts[task.taskId];
                        const label = draft?.selection
                          .map((value: string) => schema?.options?.find((opt: { value: string; label: string }) => opt.value === value)?.label || value)
                          .join(', ');
                        const isCompleted = Boolean(draft?.selection.length);
                        const isJustCompleted = task.taskId === lastCompletedTaskId;
                        return (
                          <button
                            key={task.taskId}
                            type="button"
                            className={`w-full rounded border px-4 py-3 text-left transition-colors duration-300 ${
                              index === currentIndex ? 'border-blue-500 bg-white text-blue-700' : 'border-gray-200 bg-white'
                            } ${isJustCompleted ? 'bg-green-50 border-green-200' : ''}`}
                            onClick={() => goToIndex(index)}
                          >
                            <div className="flex items-center justify-between">
                              <span>任务 {index + 1}</span>
                              {isCompleted && <span className="text-green-600">已完成</span>}
                            </div>
                            <div className="mt-1 text-gray-500">{label || '未标注'}</div>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
