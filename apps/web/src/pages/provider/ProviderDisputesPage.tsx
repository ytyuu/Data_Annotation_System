import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DataItemViewer } from '../../components/shared/DataItemViewer';
import { AnnotationEditor } from '../../components/shared/AnnotationEditor';
import { AnnotationResultViewer, formatAnnotationResult } from '../../components/shared/AnnotationResultViewer';
import { buildAnnotationResult } from '../../components/shared/AnnotationResultBuilder';
import { AnnotationCard } from '../../components/shared/AnnotationCard';
import type { AnnotationSchema } from '../../components/shared/AnnotationEditor';
import { AppButton } from '../../components/shared/AppButton';
import { AppModal } from '../../components/shared/AppModal';
import { StatusBadge } from '../../components/shared/StatusBadge';
import { AppAlert } from '../../components/shared/AppAlert';
import { EmptyState } from '../../components/shared/EmptyState';
import { AppTable, AppTableBody, AppTableHead, AppTableRow } from '../../components/shared/AppTable';
import { PageToolbar } from '../../components/shared/PageToolbar';

const apiBaseUrl = 'http://localhost:7000';

const datasetStatusLabels: Record<string, string> = {
  draft: '草稿',
  in_progress: '进行中',
  open: '已开放',
  annotating: '标注中',
  reviewing: '审核中',
  revision_required: '需调整',
  completed: '已完成',
  closed: '已关闭',
};

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
    if (!disputeDetail?.annotationSchema) return null;
    try {
      return JSON.parse(disputeDetail.annotationSchema) as AnnotationSchema;
    } catch {
      return null;
    }
  }, [disputeDetail]);

  async function handleResolveSubmit() {
    if (!selectedDataset || !resolveItem) return;

    const token = localStorage.getItem('token');
    if (!token) return;

    const supportsSelection = schema?.type === 'classification' && Array.isArray(schema?.options);

    if (supportsSelection && resolveSelection.length === 0) {
      setDetailError('请选择最终标注结果');
      return;
    }

    setResolveSubmitting(true);
    setDetailError('');

    try {
      const finalResult = supportsSelection
        ? buildAnnotationResult({ main: resolveSelection, sub: {} }, schema)
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
          共 {datasets.length} 个数据集存在争议数据项
        </PageToolbar>

        {datasetsError && <AppAlert kind="error" className="mb-6">{datasetsError}</AppAlert>}

        {datasetsLoading ? (
          <EmptyState>正在加载...</EmptyState>
        ) : datasets.length === 0 ? (
          <EmptyState align="center" spacious>暂无争议数据项</EmptyState>
        ) : (
          <AppTable>
            <AppTableHead>
              <tr>
                <th className="w-[40%] px-4 py-3 text-left">名称</th>
                <th className="px-4 py-3 text-left">状态</th>
                <th className="px-4 py-3 text-left">数据项</th>
                <th className="px-4 py-3 text-left">争议数</th>
                <th className="px-4 py-3 text-left">更新时间</th>
                <th className="px-4 py-3 text-right">操作</th>
              </tr>
            </AppTableHead>
            <AppTableBody>
              {datasets.map((dataset) => (
                <AppTableRow key={dataset.id} className="align-top">
                  <td className="px-4 py-4 align-middle">
                    <div className="text-base font-semibold text-gray-900">{dataset.name}</div>
                    <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                      {dataset.description || '暂无简介'}
                    </div>
                  </td>
                  <td className="px-4 py-4 align-middle">
                    <StatusBadge status={dataset.status}>
                      {datasetStatusLabels[dataset.status] || dataset.status}
                    </StatusBadge>
                  </td>
                  <td className="px-4 py-4 align-middle text-gray-600">
                    {dataset.itemCount} 条
                  </td>
                  <td className="px-4 py-4 align-middle">
                    <StatusBadge status="disputed">{dataset.disputedItemCount} 条争议</StatusBadge>
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
                      查看争议
                    </AppButton>
                  </td>
                </AppTableRow>
              ))}
            </AppTableBody>
          </AppTable>
        )}
      </div>
    );
  }

  function renderItemsView() {
    if (!selectedDataset) return null;

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
          <div className="text-sm text-gray-500">争议数据项</div>
        </div>

        {itemsError && <AppAlert kind="error" className="mb-6">{itemsError}</AppAlert>}

        {itemsLoading ? (
          <EmptyState>正在加载...</EmptyState>
        ) : disputedItems.length === 0 ? (
          <EmptyState align="center" spacious>该数据集暂无争议数据项</EmptyState>
        ) : (
          <AppTable>
              <AppTableHead>
                <tr>
                  <th className="px-4 py-3">内容</th>
                  <th className="px-4 py-3">状态</th>
                  <th className="px-4 py-3">更新时间</th>
                  <th className="px-4 py-3 text-right">操作</th>
                </tr>
              </AppTableHead>
              <AppTableBody>
                {disputedItems.map((item) => (
                  <AppTableRow key={item.id}>
                    <td className="max-w-xl px-4 py-3 text-gray-900">
                      <DataItemViewer
                        item={{
                          id: item.id,
                          datasetId: item.datasetId,
                          content: item.content,
                          contentType: item.contentType,
                          metadata: '{}',
                        }}
                        className="max-h-24"
                      />
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status="disputed">
                        有争议
                      </StatusBadge>
                    </td>
                    <td className="px-4 py-3 text-gray-500">
                      {new Date(item.updatedAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <AppButton
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={() => openResolveModal(item)}
                      >
                        处理争议
                      </AppButton>
                    </td>
                  </AppTableRow>
                ))}
              </AppTableBody>
          </AppTable>
        )}
      </div>
    );
  }

  function renderResolveModal() {
    if (!resolveModalOpen || !resolveItem) return null;

    return (
      <AppModal
        title="处理争议"
        subtitle={disputeDetail?.datasetName}
        width="2xl"
        fullHeight
        contentClassName="flex-1 overflow-hidden"
        actions={
          <AppButton
            type="button"
            variant="secondary"
            size="sm"
            onClick={closeResolveModal}
          >
            关闭
          </AppButton>
        }
      >
          {detailLoading ? (
            <div className="flex flex-1 items-center justify-center p-6 text-sm text-gray-500">
              正在加载争议详情...
            </div>
          ) : detailError && !disputeDetail ? (
            <div className="flex flex-1 items-center justify-center p-6">
              <AppAlert kind="error">{detailError}</AppAlert>
            </div>
          ) : disputeDetail ? (
            <div className="flex flex-1 gap-6 overflow-hidden p-6">
              <div className="flex flex-1 flex-col gap-4 overflow-hidden">
                <div className="flex flex-1 flex-col gap-4 overflow-auto">
                  <div>
                    <div className="mb-2 text-sm font-medium text-gray-700">数据内容</div>
                    <div className="rounded border border-gray-200 p-4">
                      <DataItemViewer
                        item={{
                          id: disputeDetail.item.id,
                          datasetId: disputeDetail.item.datasetId,
                          content: disputeDetail.item.content,
                          contentType: disputeDetail.item.contentType,
                          metadata: '{}',
                        }}
                      />
                    </div>
                  </div>

                  <div>
                    <div className="mb-2 text-sm font-medium text-gray-700">裁决结果</div>
                    <AnnotationEditor
                      schema={schema}
                      selection={{ main: resolveSelection, sub: {} }}
                      onChange={(sel) => setResolveSelection(sel.main)}
                    />

                    <div className="mt-3">
                      <label className="app-label">备注（可选）</label>
                      <textarea
                        value={resolveComment}
                        onChange={(e) => setResolveComment(e.target.value)}
                        className="app-input min-h-20 resize-y"
                        placeholder="填写裁决说明"
                      />
                    </div>

                    {detailError && <AppAlert kind="error" className="mt-3">{detailError}</AppAlert>}

                    <div className="mt-4">
                      <AppButton
                        type="button"
                        variant="primary"
                        disabled={resolveSubmitting}
                        onClick={handleResolveSubmit}
                      >
                        {resolveSubmitting ? '提交中...' : '确认裁决'}
                      </AppButton>
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex w-64 shrink-0 flex-col gap-4 overflow-auto">
                <div className="rounded border border-gray-200 bg-gray-50 p-4 text-sm text-gray-700">
                  <div className="font-medium text-gray-900">标注说明</div>
                  <div className="mt-2 whitespace-pre-wrap">
                    {disputeDetail.annotationGuide || '暂无标注说明'}
                  </div>
                </div>

                <div>
                  <div className="mb-2 text-sm font-medium text-gray-700">标注结果</div>
                  <div className="grid gap-3">
                    {disputeDetail.annotations.map((ann) => (
                      <AnnotationCard
                        key={ann.id}
                        annotatorName={ann.annotatorName}
                        annotationType={ann.annotationType}
                        result={ann.result}
                        comment={ann.comment}
                        isDisputed={ann.isDisputed}
                        submittedAt={ann.submittedAt}
                        schema={schema}
                      />
                    ))}
                  </div>
                </div>
              </div>
            </div>
          ) : null}
      </AppModal>
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
