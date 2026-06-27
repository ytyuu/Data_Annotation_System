import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataItemViewer } from '../../components/shared/DataItemViewer';
import { AnnotationResultViewer } from '../../components/shared/AnnotationResultViewer';
import type { AnnotationSchema } from '../../components/shared/AnnotationEditor';
import { AppButton } from '../../components/shared/AppButton';
import { SegmentedControl } from '../../components/shared/SegmentedControl';
import { StatusBadge } from '../../components/shared/StatusBadge';
import { AppAlert } from '../../components/shared/AppAlert';
import { EmptyState } from '../../components/shared/EmptyState';
import { AppTable, AppTableBody, AppTableHead, AppTableRow } from '../../components/shared/AppTable';
import { PageToolbar } from '../../components/shared/PageToolbar';

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
  const [completeSubmitting, setCompleteSubmitting] = useState(false);
  const [completeError, setCompleteError] = useState('');
  const [republishSubmitting, setRepublishSubmitting] = useState(false);
  const [republishError, setRepublishError] = useState('');

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
    setCompleteError('');
    setRepublishError('');
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

      const reviewingDataset = { ...selectedDataset, status: 'reviewing' };
      setSelectedDataset(reviewingDataset);
      await loadReviewItems(reviewingDataset);
      setFinishDone(true);
      await loadDatasets();
    } catch (err) {
      setFinishError(err instanceof Error ? err.message : '提交失败');
    } finally {
      setFinishSubmitting(false);
    }
  }

  async function handleCompleteReview() {
    if (!selectedDataset) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    setCompleteSubmitting(true);
    setCompleteError('');

    try {
      const response = await fetch(
        `${apiBaseUrl}/api/provider/datasets/${selectedDataset.id}/complete-review`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || '标记完成失败');
      }

      await loadDatasets();
      backToDatasets();
    } catch (err) {
      setCompleteError(err instanceof Error ? err.message : '标记完成失败');
    } finally {
      setCompleteSubmitting(false);
    }
  }

  async function handleRepublishRejected() {
    if (!selectedDataset) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    setRepublishSubmitting(true);
    setRepublishError('');

    try {
      const response = await fetch(
        `${apiBaseUrl}/api/provider/datasets/${selectedDataset.id}/republish`,
        {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
          },
        }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || '重新发布失败');
      }

      await loadDatasets();
      await loadReviewItems(selectedDataset);
    } catch (err) {
      setRepublishError(err instanceof Error ? err.message : '重新发布失败');
    } finally {
      setRepublishSubmitting(false);
    }
  }

  function renderDatasetsView() {
    const pendingDatasets = datasets.filter((d) => {
      if (d.status === 'completed') return false;
      if (d.status === 'reviewing') return true;
      if (d.status !== 'in_progress') return false;
      if (d.itemCount <= 0) return false;
      const ratio = Number(d.targetCompletionRatio);
      return (d.completedItemCount / d.itemCount) * 100 >= ratio;
    });

    const reviewedDatasets = datasets.filter((d) => d.status === 'completed');

    return (
      <div>
        <PageToolbar
          actions={
            <AppButton
              type="button"
              variant="secondary"
              size="sm"
              disabled={datasetsLoading}
              onClick={loadDatasets}
            >
              {datasetsLoading ? '刷新中...' : '刷新'}
            </AppButton>
          }
        >
          <SegmentedControl
            value={reviewTab}
            options={[
              { value: 'pending', label: `待审核 ${pendingDatasets.length}` },
              { value: 'completed', label: `已完成审核 ${reviewedDatasets.length}` },
            ]}
            onChange={setReviewTab}
            size="sm"
          />
        </PageToolbar>

        {datasetsError && <AppAlert kind="error" className="mb-6">{datasetsError}</AppAlert>}

        {datasetsLoading ? (
          <EmptyState>正在加载...</EmptyState>
        ) : reviewTab === 'pending' ? (
          pendingDatasets.length === 0 ? (
            <EmptyState align="center" spacious>暂无待审核的数据集</EmptyState>
          ) : (
            <AppTable>
              <AppTableHead>
                <tr>
                  <th className="w-[34%] px-4 py-3 text-left">名称</th>
                  <th className="px-4 py-3 text-left">状态</th>
                  <th className="px-4 py-3 text-left">数据项</th>
                  <th className="px-4 py-3 text-left">审核阈值</th>
                  <th className="px-4 py-3 text-left">更新时间</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </AppTableHead>
              <AppTableBody>
                {pendingDatasets.map((dataset) => (
                  <AppTableRow key={dataset.id} className="align-top">
                    <td className="px-4 py-4 align-middle">
                      <div className="text-base font-semibold text-gray-900">{dataset.name}</div>
                      <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                        {dataset.description || '暂无简介'}
                      </div>
                    </td>
                    <td className="px-4 py-4 align-middle">
                      <StatusBadge status={dataset.status === 'reviewing' ? 'reviewing' : 'in_progress'}>
                        {dataset.status === 'reviewing' ? '审核中' : '待开始审核'}
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-600">
                      <div className="text-sm text-gray-900">{dataset.completedItemCount} / {dataset.itemCount}</div>
                      <div className="mt-1 text-xs text-gray-500">已完成 / 总数</div>
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-600">
                      {dataset.targetCompletionRatio}%
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-500">
                      {new Date(dataset.updatedAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-4 align-middle text-right">
                      <AppButton
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => openDataset(dataset)}
                      >
                        {dataset.status === 'reviewing' ? '继续审核' : '开始审核'}
                      </AppButton>
                    </td>
                  </AppTableRow>
                ))}
              </AppTableBody>
            </AppTable>
          )
        ) : (
          <div>
            {reviewedDatasets.length === 0 ? (
              <EmptyState align="center" spacious>暂无已完成审核的数据集</EmptyState>
            ) : (
              <AppTable>
                <AppTableHead>
                  <tr>
                    <th className="w-[40%] px-4 py-3 text-left">名称</th>
                    <th className="px-4 py-3 text-left">状态</th>
                    <th className="px-4 py-3 text-left">数据项</th>
                    <th className="px-4 py-3 text-left">审核阈值</th>
                    <th className="px-4 py-3 text-left">更新时间</th>
                  </tr>
                </AppTableHead>
                <AppTableBody>
                  {reviewedDatasets.map((dataset) => (
                    <AppTableRow key={dataset.id} className="align-top">
                      <td className="px-4 py-4 align-middle">
                        <div className="text-base font-semibold text-gray-900">{dataset.name}</div>
                        <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                          {dataset.description || '暂无简介'}
                        </div>
                      </td>
                      <td className="px-4 py-4 align-middle">
                        <StatusBadge tone="success">已完成审核</StatusBadge>
                      </td>
                      <td className="px-4 py-4 align-middle text-gray-600">
                        <div className="text-sm text-gray-900">{dataset.completedItemCount} / {dataset.itemCount}</div>
                        <div className="mt-1 text-xs text-gray-500">已完成 / 总数</div>
                      </td>
                      <td className="px-4 py-4 align-middle text-gray-600">
                        {dataset.targetCompletionRatio}%
                      </td>
                      <td className="px-4 py-4 align-middle text-gray-500">
                        {new Date(dataset.updatedAt).toLocaleString()}
                      </td>
                    </AppTableRow>
                  ))}
                </AppTableBody>
              </AppTable>
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
    const canComplete = reviewDetail
      ? reviewDetail.items.length > 0 &&
        reviewDetail.items.every((ri) => ri.item.status === 'accepted')
      : false;
    const hasRejectedItems = reviewDetail
      ? reviewDetail.items.some((ri) => ri.item.status === 'rejected')
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
          <div className="text-sm text-gray-500">
            {selectedDataset.status === 'reviewing' ? '审核中' : '开始审核'}
          </div>
        </div>

        {detailError && <AppAlert kind="error" className="mb-6">{detailError}</AppAlert>}

        {detailLoading ? (
          <EmptyState>正在加载审核数据...</EmptyState>
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
                数据集进入审核阶段后会一直保持审核中，直到手动标记完成。
              </div>
            </AppAlert>

            {reviewDetail.items.length === 0 ? (
              <EmptyState align="center" spacious>该数据集暂无已标注的数据项</EmptyState>
            ) : (
              <AppTable>
                  <AppTableHead>
                    <tr>
                      <th className="px-4 py-3">内容</th>
                      <th className="px-4 py-3">标注结果</th>
                      <th className="px-4 py-3">审核</th>
                    </tr>
                  </AppTableHead>
                  <AppTableBody>
                    {reviewDetail.items.map((ri) => {
                      const decided = ri.item.id in reviewDecisions;
                      const accepted = reviewDecisions[ri.item.id];
                      const rejected = decided && !accepted;
                      return (
                        <AppTableRow key={ri.item.id}>
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
                        </AppTableRow>
                      );
                    })}
                  </AppTableBody>
              </AppTable>
            )}

            {!finishDone && allItemsDecided && (
              <div className="flex items-center gap-3">
                <AppButton
                  type="button"
                  variant="primary"
                  disabled={finishSubmitting}
                  onClick={handleFinishReview}
                >
                  {finishSubmitting ? '保存中...' : '保存审核结果'}
                </AppButton>
                {finishError && (
                  <AppAlert kind="error" className="px-3 py-1.5">
                    {finishError}
                  </AppAlert>
                )}
              </div>
            )}

            {canComplete && (
              <div className="flex items-center gap-3">
                <AppButton
                  type="button"
                  variant="primary"
                  disabled={completeSubmitting}
                  onClick={handleCompleteReview}
                >
                  {completeSubmitting ? '提交中...' : '标记完成'}
                </AppButton>
                {completeError && (
                  <AppAlert kind="error" className="px-3 py-1.5">
                    {completeError}
                  </AppAlert>
                )}
              </div>
            )}

            {hasRejectedItems && (
              <div className="flex items-center gap-3">
                <AppButton
                  type="button"
                  variant="secondary"
                  disabled={republishSubmitting}
                  onClick={handleRepublishRejected}
                >
                  {republishSubmitting ? '重新发布中...' : '重新发布未通过项'}
                </AppButton>
                {republishError && (
                  <AppAlert kind="error" className="px-3 py-1.5">
                    {republishError}
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
