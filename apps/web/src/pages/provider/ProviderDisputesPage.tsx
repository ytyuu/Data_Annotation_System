import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

const apiBaseUrl = 'http://localhost:7000';

interface Dataset {
  id: string;
  providerId: string;
  name: string;
  description: string | null;
  status: string;
  itemCount: number;
  disputedItemCount: number;
  annotationSchema: string;
  createdAt: string;
  updatedAt: string;
}

interface DataItem {
  id: string;
  datasetId: string;
  content: string;
  contentType: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface AnnotationDetail {
  id: string;
  annotatorId: string;
  annotatorName: string;
  annotationType: string;
  result: string;
  comment: string | null;
  isDisputed: boolean;
  status: string;
  submittedAt: string;
}

interface DisputeDetail {
  item: DataItem;
  annotations: AnnotationDetail[];
  annotationSchema: string;
  annotationGuide: string | null;
  datasetName: string;
}

interface AnnotationOption {
  label: string;
  value: string;
}

interface AnnotationSchema {
  type?: string;
  options?: AnnotationOption[];
  selectionMode?: 'single' | 'multiple';
}

function parseSchema(rawSchema: string): AnnotationSchema | null {
  try {
    return JSON.parse(rawSchema) as AnnotationSchema;
  } catch {
    return null;
  }
}

function parseAnnotationSelection(result: string, schema: AnnotationSchema | null): string[] {
  try {
    const parsed = JSON.parse(result) as unknown;
    if (schema?.selectionMode === 'multiple') {
      if (Array.isArray((parsed as { values?: unknown }).values)) {
        return (parsed as { values: string[] }).values.filter(Boolean);
      }
    }
    if (typeof (parsed as { value?: unknown }).value === 'string') {
      return [(parsed as { value: string }).value];
    }
    if (Array.isArray(parsed)) {
      return parsed.filter((item): item is string => typeof item === 'string');
    }
    if (typeof parsed === 'string') {
      return [parsed];
    }
    return [];
  } catch {
    return [];
  }
}

function buildResult(selection: string[], schema: AnnotationSchema | null): Record<string, unknown> {
  if (schema?.selectionMode === 'multiple') {
    return { values: selection };
  }
  return { value: selection[0] ?? null };
}

function renderItemContent(item: DataItem) {
  if (item.contentType === 'image') {
    return (
      <img
        src={item.content}
        alt="数据项"
        className="max-h-64 w-full rounded border border-gray-200 object-contain"
      />
    );
  }
  if (item.contentType === 'json') {
    return (
      <pre className="whitespace-pre-wrap rounded border border-gray-200 bg-gray-50 p-4 text-sm leading-6 text-gray-700">
        {item.content}
      </pre>
    );
  }
  return <div className="whitespace-pre-wrap text-sm leading-6 text-gray-800">{item.content}</div>;
}

function AnnotationCard({
  annotation,
  schema,
}: {
  annotation: AnnotationDetail;
  schema: AnnotationSchema | null;
}) {
  const selection = parseAnnotationSelection(annotation.result, schema);
  const labels = selection
    .map((v) => schema?.options?.find((o) => o.value === v)?.label || v)
    .join('，');
  const typeLabel = annotation.annotationType === 'annotation' ? '原始标注' : '互查标注';

  return (
    <div className="rounded border border-gray-200 bg-gray-50 p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-900">{annotation.annotatorName}</span>
        <span className="rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-700">{typeLabel}</span>
      </div>
      <div className="mt-1 text-xs text-gray-500">
        {new Date(annotation.submittedAt).toLocaleString()}
      </div>
      <div className="mt-2 text-sm">
        <span className="font-medium text-gray-700">结果：</span>
        <span className="text-blue-700">{labels || '无'}</span>
      </div>
      {annotation.comment && (
        <div className="mt-1 text-sm text-gray-600">备注：{annotation.comment}</div>
      )}
      {annotation.isDisputed && (
        <span className="mt-2 inline-block rounded bg-red-100 px-2 py-0.5 text-xs text-red-700">
          存在争议
        </span>
      )}
    </div>
  );
}

type ViewState = 'datasets' | 'items';

export function ProviderDisputesPage() {
  const navigate = useNavigate();
  const [view, setView] = useState<ViewState>('datasets');
  const [selectedDataset, setSelectedDataset] = useState<Dataset | null>(null);

  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(false);
  const [datasetsError, setDatasetsError] = useState('');

  const [disputedItems, setDisputedItems] = useState<DataItem[]>([]);
  const [itemsLoading, setItemsLoading] = useState(false);
  const [itemsError, setItemsError] = useState('');

  const [resolveModalOpen, setResolveModalOpen] = useState(false);
  const [resolveItem, setResolveItem] = useState<DataItem | null>(null);
  const [disputeDetail, setDisputeDetail] = useState<DisputeDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');
  const [resolveSelection, setResolveSelection] = useState<string[]>([]);
  const [resolveComment, setResolveComment] = useState('');
  const [resolveSubmitting, setResolveSubmitting] = useState(false);

  useEffect(() => {
    loadDatasets();
  }, []);

  async function loadDatasets() {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setDatasetsLoading(true);
    setDatasetsError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/provider/datasets`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `加载失败 (${response.status})`);
      }

      setDatasets((data as Dataset[]).filter((d) => (d.disputedItemCount ?? 0) > 0));
    } catch (err) {
      setDatasetsError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setDatasetsLoading(false);
    }
  }

  async function loadDisputedItems(dataset: Dataset) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setItemsLoading(true);
    setItemsError('');

    try {
      const response = await fetch(
        `${apiBaseUrl}/api/provider/datasets/${dataset.id}/disputed-items`,
        {
          headers: { Authorization: `Bearer ${token}` },
        }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `加载失败 (${response.status})`);
      }

      setDisputedItems(data as DataItem[]);
    } catch (err) {
      setItemsError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setItemsLoading(false);
    }
  }

  async function loadDisputeDetail(dataset: Dataset, item: DataItem) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setDetailLoading(true);
    setDetailError('');
    setResolveSelection([]);
    setResolveComment('');

    try {
      const response = await fetch(
        `${apiBaseUrl}/api/provider/datasets/${dataset.id}/items/${item.id}/dispute-detail`,
        {
          headers: { Authorization: `Bearer ${token}` },
        }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `加载失败 (${response.status})`);
      }

      setDisputeDetail(data as DisputeDetail);
    } catch (err) {
      setDetailError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setDetailLoading(false);
    }
  }

  function openDataset(dataset: Dataset) {
    setSelectedDataset(dataset);
    setView('items');
    loadDisputedItems(dataset);
  }

  function backToDatasets() {
    setView('datasets');
    setSelectedDataset(null);
    setDisputedItems([]);
    setItemsError('');
  }

  function openResolveModal(item: DataItem) {
    if (!selectedDataset) return;
    setResolveItem(item);
    setResolveModalOpen(true);
    loadDisputeDetail(selectedDataset, item);
  }

  function closeResolveModal() {
    setResolveModalOpen(false);
    setResolveItem(null);
    setDisputeDetail(null);
    setDetailError('');
    setResolveSelection([]);
    setResolveComment('');
  }

  const schema = useMemo(() => {
    return disputeDetail ? parseSchema(disputeDetail.annotationSchema) : null;
  }, [disputeDetail]);

  const supportsSelection = schema?.type === 'classification' && Array.isArray(schema?.options);
  const isMultiple = schema?.selectionMode === 'multiple';

  function updateSelection(value: string) {
    setResolveSelection((prev) => {
      if (isMultiple) {
        return prev.includes(value)
          ? prev.filter((item) => item !== value)
          : [...prev, value];
      }
      return [value];
    });
  }

  async function handleResolveSubmit() {
    if (!selectedDataset || !resolveItem) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    if (supportsSelection && resolveSelection.length === 0) {
      setDetailError('请选择最终标注结果');
      return;
    }

    setResolveSubmitting(true);
    setDetailError('');

    try {
      const finalResult = supportsSelection
        ? JSON.stringify(buildResult(resolveSelection, schema))
        : '{}';

      const response = await fetch(
        `${apiBaseUrl}/api/provider/datasets/${selectedDataset.id}/items/${resolveItem.id}/resolve-dispute`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            finalResult,
            comment: resolveComment.trim() || null,
          }),
        }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `提交失败 (${response.status})`);
      }

      closeResolveModal();
      await loadDisputedItems(selectedDataset);
      await loadDatasets();
      if (disputedItems.length <= 1) {
        backToDatasets();
      }
    } catch (err) {
      setDetailError(err instanceof Error ? err.message : '提交失败');
    } finally {
      setResolveSubmitting(false);
    }
  }

  function renderDatasetsView() {
    return (
      <div>
        <div className="mb-4 text-sm text-gray-500">
          共 {datasets.length} 个数据集存在争议数据项
        </div>

        {datasetsError && <div className="app-alert-error">{datasetsError}</div>}

        {datasetsLoading ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
            正在加载...
          </div>
        ) : datasets.length === 0 ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-8 text-center text-sm text-gray-500">
            暂无争议数据项
          </div>
        ) : (
          <div className="overflow-hidden rounded border border-gray-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 text-xs font-medium text-gray-500">
                <tr>
                  <th className="px-4 py-3">数据集名称</th>
                  <th className="px-4 py-3">数据项总数</th>
                  <th className="px-4 py-3">争议数量</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {datasets.map((dataset) => (
                  <tr key={dataset.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <div className="font-medium text-gray-900">{dataset.name}</div>
                      {dataset.description && (
                        <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                          {dataset.description}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-gray-600">{dataset.itemCount}</td>
                    <td className="px-4 py-3">
                      <span className="app-badge" data-kind="status" data-status="disputed">
                        {dataset.disputedItemCount} 条争议
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        className="rounded border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-50"
                        onClick={() => openDataset(dataset)}
                      >
                        查看争议
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    );
  }

  function renderItemsView() {
    if (!selectedDataset) return null;

    return (
      <div>
        <div className="mb-4 flex items-center gap-3">
          <button
            type="button"
            className="rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
            onClick={backToDatasets}
          >
            返回
          </button>
          <div className="text-base font-semibold text-gray-900">{selectedDataset.name}</div>
          <div className="text-sm text-gray-500">争议数据项</div>
        </div>

        {itemsError && <div className="app-alert-error">{itemsError}</div>}

        {itemsLoading ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
            正在加载...
          </div>
        ) : disputedItems.length === 0 ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-8 text-center text-sm text-gray-500">
            该数据集暂无争议数据项
          </div>
        ) : (
          <div className="overflow-hidden rounded border border-gray-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 text-xs font-medium text-gray-500">
                <tr>
                  <th className="px-4 py-3">内容</th>
                  <th className="px-4 py-3">状态</th>
                  <th className="px-4 py-3">更新时间</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {disputedItems.map((item) => (
                  <tr key={item.id} className="hover:bg-gray-50">
                    <td className="max-w-md px-4 py-3 text-gray-900">
                      <div className="line-clamp-2">{item.content}</div>
                    </td>
                    <td className="px-4 py-3">
                      <span className="app-badge" data-kind="status" data-status="disputed">
                        有争议
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500">
                      {new Date(item.updatedAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        className="rounded border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-blue-50"
                        onClick={() => openResolveModal(item)}
                      >
                        处理争议
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    );
  }

  function renderResolveModal() {
    if (!resolveModalOpen || !resolveItem) return null;

    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-gray-900/45 px-6 py-8">
        <div className="flex h-full w-full max-w-5xl flex-col overflow-hidden rounded bg-white shadow-2xl">
          <div className="flex items-start justify-between border-b border-gray-200 px-6 py-4">
            <div>
              <div className="text-base font-semibold text-gray-900">处理争议</div>
              {disputeDetail && (
                <div className="mt-1 text-sm text-gray-500">{disputeDetail.datasetName}</div>
              )}
            </div>
            <button
              type="button"
              className="rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-50"
              onClick={closeResolveModal}
            >
              关闭
            </button>
          </div>

          {detailLoading ? (
            <div className="flex flex-1 items-center justify-center p-6 text-sm text-gray-500">
              正在加载争议详情...
            </div>
          ) : detailError && !disputeDetail ? (
            <div className="flex flex-1 items-center justify-center p-6 text-sm text-red-600">
              {detailError}
            </div>
          ) : disputeDetail ? (
            <div className="flex flex-1 gap-6 overflow-hidden p-6">
              <div className="flex flex-1 flex-col gap-4 overflow-hidden">
                <div className="flex flex-1 flex-col gap-4 overflow-auto">
                  <div>
                    <div className="mb-2 text-sm font-medium text-gray-700">数据内容</div>
                    <div className="rounded border border-gray-200 p-4">
                      {renderItemContent(disputeDetail.item)}
                    </div>
                  </div>

                  <div>
                    <div className="mb-2 text-sm font-medium text-gray-700">标注结果</div>
                    <div className="grid gap-3">
                      {disputeDetail.annotations.map((ann) => (
                        <AnnotationCard key={ann.id} annotation={ann} schema={schema} />
                      ))}
                    </div>
                  </div>

                  <div>
                    <div className="mb-2 text-sm font-medium text-gray-700">裁决结果</div>
                    {!supportsSelection ? (
                      <div className="text-sm text-gray-500">暂不支持该标注类型</div>
                    ) : (
                      <div className="grid gap-3">
                        {(schema?.options ?? []).map((option) => {
                          const checked = resolveSelection.includes(option.value);
                          return (
                            <label
                              key={option.value}
                              className={`flex min-h-12 cursor-pointer items-center gap-3 rounded border px-4 py-3 text-sm font-medium transition-colors ${
                                checked
                                  ? 'border-blue-500 bg-blue-50 text-blue-700'
                                  : 'border-gray-200 text-gray-700'
                              }`}
                            >
                              <input
                                type={isMultiple ? 'checkbox' : 'radio'}
                                checked={checked}
                                onChange={() => updateSelection(option.value)}
                                className="h-4 w-4"
                              />
                              {option.label}
                            </label>
                          );
                        })}
                      </div>
                    )}

                    <div className="mt-3">
                      <label className="app-label">备注（可选）</label>
                      <textarea
                        value={resolveComment}
                        onChange={(e) => setResolveComment(e.target.value)}
                        className="app-input min-h-20 resize-y"
                        placeholder="填写裁决说明"
                      />
                    </div>

                    {detailError && <div className="app-alert-error mt-3">{detailError}</div>}

                    <div className="mt-4">
                      <button
                        type="button"
                        disabled={resolveSubmitting || (supportsSelection && resolveSelection.length === 0)}
                        className="rounded bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                        onClick={handleResolveSubmit}
                      >
                        {resolveSubmitting ? '提交中...' : '确认裁决'}
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex w-64 shrink-0 flex-col gap-4">
                <div className="rounded border border-gray-200 bg-gray-50 p-4 text-sm text-gray-700">
                  <div className="font-medium text-gray-900">标注说明</div>
                  <div className="mt-2 whitespace-pre-wrap">
                    {disputeDetail.annotationGuide || '暂无标注说明'}
                  </div>
                </div>
              </div>
            </div>
          ) : null}
        </div>
      </div>
    );
  }

  return (
    <div>
      {view === 'datasets' && renderDatasetsView()}
      {view === 'items' && renderItemsView()}
      {renderResolveModal()}
    </div>
  );
}
