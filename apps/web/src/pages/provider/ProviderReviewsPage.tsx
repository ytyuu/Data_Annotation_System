import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataItemViewer } from '../../components/shared/DataItemViewer';
import { AnnotationResultViewer } from '../../components/shared/AnnotationResultViewer';
import type { AnnotationSchema } from '../../components/shared/AnnotationEditor';
import { AppButton } from '../../components/shared/AppButton';
import { SegmentedControl } from '../../components/shared/SegmentedControl';
import { StatusBadge } from '../../components/shared/StatusBadge';
import { AppAlert } from '../../components/shared/AppAlert';

const apiBaseUrl = 'http://localhost:7000';

interface Dataset {
  id: string;
  providerId: string;
  name: string;
  description: string | null;
  status: string;
  itemCount: number;
  completedItemCount: number;
  targetCompletionRatio: string;
  annotationSchema: string;
  createdAt: string;
  updatedAt: string;
  hasBeenReviewed: boolean;
}

interface DataItem {
  id: string;
  datasetId: string;
  content: string;
  contentType: string;
  status: string;
  finalResult: string | null;
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

interface ReviewItem {
  item: DataItem;
  annotations: AnnotationDetail[];
}

interface ReviewDetail {
  datasetName: string;
  annotationSchema: string;
  annotationGuide: string | null;
  items: ReviewItem[];
  reviewedItemCount: number;
  totalItemCount: number;
}

type ViewState = 'datasets' | 'items';

export function ProviderReviewsPage() {
  const navigate = useNavigate();
  const [view, setView] = useState<ViewState>('datasets');
  const [selectedDataset, setSelectedDataset] = useState<Dataset | null>(null);

  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(false);
  const [datasetsError, setDatasetsError] = useState('');

  const [reviewTab, setReviewTab] = useState<'pending' | 'completed'>('pending');

  const [reviewDetail, setReviewDetail] = useState<ReviewDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');

  const [reviewDecisions, setReviewDecisions] = useState<Record<string, boolean>>({});
  const [finishSubmitting, setFinishSubmitting] = useState(false);
  const [finishError, setFinishError] = useState('');
  const [finishDone, setFinishDone] = useState(false);

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

      setDatasets(data as Dataset[]);
    } catch (err) {
      setDatasetsError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setDatasetsLoading(false);
    }
  }

  async function loadReviewItems(dataset: Dataset) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setDetailLoading(true);
    setDetailError('');
    setReviewDetail(null);
    setReviewDecisions({});
    setFinishDone(false);
    setFinishError('');

    try {
      const response = await fetch(
        `${apiBaseUrl}/api/provider/datasets/${dataset.id}/review-items`,
        { headers: { Authorization: `Bearer ${token}` } }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `加载失败 (${response.status})`);
      }

      const detail = data as ReviewDetail;

      const initialDecisions: Record<string, boolean> = {};
      for (const ri of detail.items) {
        if (ri.item.status === 'accepted') initialDecisions[ri.item.id] = true;
        else if (ri.item.status === 'rejected') initialDecisions[ri.item.id] = false;
      }

      setReviewDetail(detail);
      setReviewDecisions(initialDecisions);
    } catch (err) {
      setDetailError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setDetailLoading(false);
    }
  }

  function openDataset(dataset: Dataset) {
    setSelectedDataset(dataset);
    setView('items');
    loadReviewItems(dataset);
  }

  function backToDatasets() {
    setView('datasets');
    setSelectedDataset(null);
    setReviewDetail(null);
    setDetailError('');
    setReviewDecisions({});
    setFinishDone(false);
    setFinishError('');
  }

  const schema = useMemo(() => {
    if (!reviewDetail?.annotationSchema) return null;
    try {
      return JSON.parse(reviewDetail.annotationSchema) as AnnotationSchema;
    } catch {
      return null;
    }
  }, [reviewDetail]);

  async function handleReviewItem(itemId: string, accepted: boolean) {
    if (!selectedDataset) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    const prev = reviewDecisions[itemId];
    setReviewDecisions((d) => ({ ...d, [itemId]: accepted }));

    try {
      const response = await fetch(
        `${apiBaseUrl}/api/provider/datasets/${selectedDataset.id}/review-item/${itemId}`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ accepted }),
        }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || '操作失败');
      }
    } catch (err) {
      setReviewDecisions((d) => ({ ...d, [itemId]: prev }));
      setDetailError(err instanceof Error ? err.message : '操作失败');
    }
  }

  async function handleFinishReview() {
    if (!selectedDataset) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    setFinishSubmitting(true);
    setFinishError('');

    try {
      const response = await fetch(
        `${apiBaseUrl}/api/provider/datasets/${selectedDataset.id}/finish-review`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({}),
        }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || '提交失败');
      }

      setFinishDone(true);
      await loadDatasets();
    } catch (err) {
      setFinishError(err instanceof Error ? err.message : '提交失败');
    } finally {
      setFinishSubmitting(false);
    }
  }

  function renderDatasetsView() {
    const pendingDatasets = datasets.filter((d) => {
      if (d.hasBeenReviewed) return false;
      if (d.itemCount <= 0) return false;
      const ratio = Number(d.targetCompletionRatio);
      return (d.completedItemCount / d.itemCount) * 100 >= ratio;
    });

    const reviewedDatasets = datasets.filter((d) => d.hasBeenReviewed);

    return (
      <div>
        <div className="mb-4 flex items-center justify-between">
          <SegmentedControl
            value={reviewTab}
            options={[
              { value: 'pending', label: `待审核 ${pendingDatasets.length}` },
              { value: 'completed', label: `已完成审核 ${reviewedDatasets.length}` },
            ]}
            onChange={setReviewTab}
            size="sm"
          />
          <AppButton
            type="button"
            variant="secondary"
            size="sm"
            disabled={datasetsLoading}
            onClick={loadDatasets}
          >
            {datasetsLoading ? '刷新中...' : '刷新'}
          </AppButton>
        </div>

        {datasetsError && <AppAlert kind="error" className="mb-6">{datasetsError}</AppAlert>}

        {datasetsLoading ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
            正在加载...
          </div>
        ) : reviewTab === 'pending' ? (
          pendingDatasets.length === 0 ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-8 text-center text-sm text-gray-500">
              暂无待审核的数据集
            </div>
          ) : (
            <div className="space-y-4">
              {pendingDatasets.map((dataset) => (
                <div key={dataset.id} className="rounded border border-gray-200 bg-white">
                  <div className="flex items-center justify-between gap-4 px-4 py-3">
                    <div className="flex flex-1 items-center gap-4">
                      <div>
                        <div className="font-medium text-gray-900">{dataset.name}</div>
                        {dataset.description && (
                          <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                            {dataset.description}
                          </div>
                        )}
                        <div className="mt-1 flex items-center gap-3 text-xs text-gray-500">
                          <span>{dataset.itemCount} 条数据</span>
                          <span>{dataset.completedItemCount} 条已完成</span>
                          <span>阈值 {dataset.targetCompletionRatio}%</span>
                        </div>
                      </div>
                      <div className="ml-auto">
                        <StatusBadge status="reviewing">
                          审核中
                        </StatusBadge>
                      </div>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <AppButton
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => openDataset(dataset)}
                      >
                        开始审核
                      </AppButton>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )
        ) : (
          <div>
            {reviewedDatasets.length === 0 ? (
              <div className="rounded border border-gray-200 bg-gray-50 p-8 text-center text-sm text-gray-500">
                暂无已完成审核的数据集
              </div>
            ) : (
              <div className="space-y-4">
                {reviewedDatasets.map((dataset) => (
                  <div key={dataset.id} className="rounded border border-gray-200 bg-white">
                    <div className="flex items-center justify-between gap-4 px-4 py-3">
                      <div className="flex flex-1 items-center gap-4">
                        <div>
                          <div className="font-medium text-gray-900">{dataset.name}</div>
                          {dataset.description && (
                            <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                              {dataset.description}
                            </div>
                          )}
                          <div className="mt-1 flex items-center gap-3 text-xs text-gray-500">
                            <span>{dataset.itemCount} 条数据</span>
                            <span>{dataset.completedItemCount} 条已完成</span>
                          </div>
                        </div>
                        <div className="ml-auto">
                          <StatusBadge tone="success">
                            已完成审核
                          </StatusBadge>
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    );
  }

  function renderItemsView() {
    if (!selectedDataset) return null;

    const allItemsDecided = reviewDetail
      ? reviewDetail.items.length > 0 &&
        reviewDetail.items.every((ri) => ri.item.id in reviewDecisions)
      : false;

    return (
      <div>
        <div className="mb-4 flex items-center gap-3">
          <AppButton
            type="button"
            variant="secondary"
            size="sm"
            onClick={backToDatasets}
          >
            返回
          </AppButton>
          <div className="text-base font-semibold text-gray-900">{selectedDataset.name}</div>
          <div className="text-sm text-gray-500">逐条审核</div>
        </div>

        {detailError && <AppAlert kind="error" className="mb-6">{detailError}</AppAlert>}

        {detailLoading ? (
          <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
            正在加载审核数据...
          </div>
        ) : reviewDetail ? (
          <div className="space-y-6">
            {finishDone && (
              <AppAlert kind="success">审核已完成</AppAlert>
            )}

            <AppAlert kind="info" title="审核说明">
              <div>
                {reviewDetail.annotationGuide || '暂无标注说明'}
              </div>
              <div className="mt-2 text-xs text-blue-700">
                标记为通过后仍可改为不通过；标记为不通过后将无法修改。
              </div>
            </AppAlert>

            {reviewDetail.items.length === 0 ? (
              <div className="rounded border border-gray-200 bg-gray-50 p-8 text-center text-sm text-gray-500">
                该数据集暂无已标注的数据项
              </div>
            ) : (
              <div className="overflow-hidden rounded border border-gray-200">
                <table className="w-full text-left text-sm">
                  <thead className="bg-gray-50 text-xs font-medium text-gray-500">
                    <tr>
                      <th className="px-4 py-3">内容</th>
                      <th className="px-4 py-3">标注结果</th>
                      <th className="px-4 py-3">审核</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200 bg-white">
                    {reviewDetail.items.map((ri) => {
                      const decided = ri.item.id in reviewDecisions;
                      const accepted = reviewDecisions[ri.item.id];
                      const rejected = decided && !accepted;
                      return (
                        <tr key={ri.item.id} className="hover:bg-gray-50">
                          <td className="max-w-md px-4 py-3 text-gray-900">
                            <DataItemViewer
                              item={{
                                id: ri.item.id,
                                datasetId: ri.item.datasetId,
                                content: ri.item.content,
                                contentType: ri.item.contentType,
                                metadata: '{}',
                              }}
                            />
                          </td>
                          <td className="px-4 py-3">
                            {ri.item.finalResult ? (
                              <AnnotationResultViewer
                                result={ri.item.finalResult}
                                schema={schema}
                                isDisputed={false}
                              />
                            ) : (
                              <span className="text-xs text-gray-400">暂无结果</span>
                            )}
                          </td>
                          <td className="px-4 py-3">
                            {finishDone ? (
                              <span className="text-xs text-gray-400">
                                {accepted ? '已通过' : '未通过'}
                              </span>
                            ) : (
                              <div className="flex items-center gap-2">
                                <AppButton
                                  type="button"
                                  disabled={finishSubmitting || decided}
                                  className={`rounded border px-3 py-1 text-xs font-medium transition-colors ${
                                    decided && accepted
                                      ? 'border-green-200 bg-green-50 text-green-700'
                                      : 'border-gray-200 text-gray-600 hover:bg-green-50'
                                  }`}
                                  onClick={() => handleReviewItem(ri.item.id, true)}
                                >
                                  通过
                                </AppButton>
                                <AppButton
                                  type="button"
                                  disabled={finishSubmitting || rejected}
                                  className={`rounded border px-3 py-1 text-xs font-medium transition-colors ${
                                    decided && !accepted
                                      ? 'border-red-200 bg-red-50 text-red-700'
                                      : 'border-gray-200 text-gray-600 hover:bg-red-100'
                                  }`}
                                  onClick={() => handleReviewItem(ri.item.id, false)}
                                >
                                  不通过
                                </AppButton>
                              </div>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {!finishDone && allItemsDecided && (
              <div className="flex items-center gap-3">
                <AppButton
                  type="button"
                  variant="primary"
                  disabled={finishSubmitting}
                  onClick={handleFinishReview}
                >
                  {finishSubmitting ? '提交中...' : '审核完成'}
                </AppButton>
                {finishError && (
                  <AppAlert kind="error" className="px-3 py-1.5">
                    {finishError}
                  </AppAlert>
                )}
              </div>
            )}
          </div>
        ) : null}
      </div>
    );
  }

  return (
    <div>
      {view === 'datasets' && renderDatasetsView()}
      {view === 'items' && renderItemsView()}
    </div>
  );
}
