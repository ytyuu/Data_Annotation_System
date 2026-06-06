import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AppButton } from '../../components/shared/AppButton';
import { SegmentedControl } from '../../components/shared/SegmentedControl';
import { AppModal } from '../../components/shared/AppModal';
import { StatusBadge } from '../../components/shared/StatusBadge';
import { AppAlert } from '../../components/shared/AppAlert';
import { MetricBox } from '../../components/shared/MetricBox';
import { EmptyState } from '../../components/shared/EmptyState';
import { AppTable, AppTableBody, AppTableHead, AppTableRow } from '../../components/shared/AppTable';
import { PageToolbar } from '../../components/shared/PageToolbar';

const apiBaseUrl = 'http://localhost:7000';

const datasetStatusLabels: Record<string, string> = {
  draft: '草稿',
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
  annotationGuide: string | null;
  annotationSchema: string;
  status: string;
  targetCompletionRatio: string;
  itemCount: number;
  completedItemCount: number;
  createdAt: string;
  updatedAt: string;
  canClaim: boolean;
  pendingItemCount?: number;
  reviewableItemCount?: number;
}

export function AnnotatorOpenDatasetsPage() {
  const navigate = useNavigate();
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [claimOpen, setClaimOpen] = useState(false);
  const [claimDataset, setClaimDataset] = useState<Dataset | null>(null);
  const [claimTaskType, setClaimTaskType] = useState<'annotation' | 'review'>('annotation');
  const [claimCount, setClaimCount] = useState(1);
  const [claimLoading, setClaimLoading] = useState(false);
  const [claimError, setClaimError] = useState('');
  const [claimSuccess, setClaimSuccess] = useState('');

  useEffect(() => {
    loadOpenDatasets();
  }, []);

  async function loadOpenDatasets() {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/datasets`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `数据集加载失败 (${response.status})`);
      }

      setDatasets(data as Dataset[]);
    } catch (err) {
      setError(err instanceof Error ? err.message : '数据集加载失败');
    } finally {
      setLoading(false);
    }
  }

  function getMaxCount(dataset: Dataset, taskType: 'annotation' | 'review'): number {
    if (taskType === 'annotation') {
      return dataset.pendingItemCount ?? 100;
    }
    return dataset.reviewableItemCount ?? 100;
  }

  function openClaimModal(dataset: Dataset) {
    setClaimDataset(dataset);
    setClaimTaskType('annotation');
    const maxCount = getMaxCount(dataset, 'annotation');
    setClaimCount(Math.min(1, maxCount));
    setClaimError('');
    setClaimOpen(true);
  }

  function closeClaimModal() {
    setClaimOpen(false);
    setClaimDataset(null);
    setClaimError('');
  }

  async function handleClaim() {
    if (!claimDataset) return;

    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setClaimLoading(true);
    setClaimError('');
    setClaimSuccess('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/annotator/datasets/${claimDataset.id}/claim`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ count: claimCount, taskType: claimTaskType }),
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `领取失败 (${response.status})`);
      }

      setClaimSuccess(`成功领取任务单 ${data?.orderNo || ''}，共 ${data?.assignedCount || 0} 条任务`);
      closeClaimModal();
      loadOpenDatasets();
    } catch (err) {
      setClaimError(err instanceof Error ? err.message : '领取失败');
    } finally {
      setClaimLoading(false);
    }
  }

  return (
    <div>
      <PageToolbar
        actions={
          <AppButton type="button" variant="secondary" disabled={loading} onClick={loadOpenDatasets}>
            {loading ? '刷新中...' : '刷新'}
          </AppButton>
        }
      >
        共 {datasets.length} 个数据集
      </PageToolbar>

      {error && <AppAlert kind="error" className="mb-6">{error}</AppAlert>}
      {claimSuccess && <AppAlert kind="success" className="mb-6">{claimSuccess}</AppAlert>}

      {loading ? (
        <EmptyState>正在加载数据集...</EmptyState>
      ) : datasets.length === 0 ? (
        <EmptyState align="center">暂无已发布数据集</EmptyState>
      ) : (
        <AppTable>
            <AppTableHead>
              <tr>
                <th className="w-[30%] px-4 py-3 text-left">名称</th>
                <th className="px-4 py-3 text-left">状态</th>
                <th className="px-4 py-3 text-left">标注方式</th>
                <th className="px-4 py-3 text-left">数据项</th>
                <th className="px-4 py-3 text-left">余量</th>
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
                    {getDatasetSchemaSummary(dataset)}
                  </td>
                  <td className="px-4 py-4 align-middle">
                    <DatasetItemMetric completed={dataset.completedItemCount} total={dataset.itemCount} />
                  </td>
                  <td className="px-4 py-4 align-middle">
                    <div className="flex gap-2">
                      <MetricBox label="标注" value={`${dataset.pendingItemCount ?? 0} 条`} compact />
                      <MetricBox label="互查" value={`${dataset.reviewableItemCount ?? 0} 条`} compact />
                    </div>
                  </td>
                  <td className="px-4 py-4 align-middle text-right">
                    {!dataset.canClaim ? (
                      <AppButton
                        type="button"
                        disabled
                        variant="secondary"
                        size="sm"
                        title="已持有该数据集任务或已达上限"
                      >
                        不可领取
                      </AppButton>
                    ) : (
                      <AppButton
                        type="button"
                        variant="primary"
                        size="sm"
                        onClick={() => openClaimModal(dataset)}
                      >
                        领取任务
                      </AppButton>
                    )}
                  </td>
                </AppTableRow>
              ))}
            </AppTableBody>
        </AppTable>
      )}

      {claimError && (
        <AppAlert kind="error" className="mt-3">{claimError}</AppAlert>
      )}

      {/* 领取任务弹窗 */}
      {claimOpen && claimDataset && (
        <AppModal
          title="领取任务"
          subtitle={claimDataset.name}
          width="md"
          closeOnOverlayClick
          onClose={closeClaimModal}
          contentClassName="space-y-5 px-6 py-5"
          footerClassName="border-gray-100 bg-white"
          footer={
            <>
              <AppButton
                type="button"
                variant="secondary"
                onClick={closeClaimModal}
                disabled={claimLoading}
              >
                取消
              </AppButton>
              <AppButton
                type="button"
                variant="primary"
                onClick={handleClaim}
                disabled={claimLoading || claimCount <= 0 || claimCount > getMaxCount(claimDataset, claimTaskType)}
              >
                {claimLoading ? '领取中...' : '确认领取'}
              </AppButton>
            </>
          }
        >
              {/* 任务类别 */}
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">任务类别</label>
                <SegmentedControl
                  value={claimTaskType}
                  options={[
                    {
                      value: 'annotation',
                      label: '标注任务',
                      description: `余量 ${claimDataset.pendingItemCount ?? 0} 条`,
                    },
                    {
                      value: 'review',
                      label: '互查任务',
                      description: `余量 ${claimDataset.reviewableItemCount ?? 0} 条`,
                    },
                  ]}
                  onChange={(taskType) => {
                    setClaimTaskType(taskType);
                    const max = getMaxCount(claimDataset, taskType);
                    setClaimCount((c) => Math.min(c, max));
                  }}
                  fullWidth
                />
              </div>

              {/* 领取数量 */}
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">
                  领取数量（上限 {getMaxCount(claimDataset, claimTaskType)} 条）
                </label>
                <input
                  type="number"
                  min={1}
                  max={getMaxCount(claimDataset, claimTaskType)}
                  value={claimCount}
                  onChange={(e) => {
                    const max = getMaxCount(claimDataset, claimTaskType);
                    const val = parseInt(e.target.value, 10) || 1;
                    setClaimCount(Math.max(1, Math.min(val, max)));
                  }}
                  className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-gray-400 focus:outline-none"
                />
                <p className="mt-1 text-xs text-gray-400">
                  请输入 1~{getMaxCount(claimDataset, claimTaskType)} 之间的数量
                </p>
              </div>

              {claimError && (
                <AppAlert kind="error" className="border-0 px-3 py-2">{claimError}</AppAlert>
              )}
        </AppModal>
      )}
    </div>
  );
}

function DatasetItemMetric({ completed, total }: { completed: number; total: number }) {
  const percent = total > 0 ? Math.min(100, Math.round((completed / total) * 100)) : 0;

  return (
    <MetricBox label="数据项" compact>
        {completed}
        <span className="mx-1 text-gray-400">/</span>
        {total}
        <span className="ml-2 text-xs font-medium text-gray-500">{percent}%</span>
    </MetricBox>
  );
}

function getDatasetSchemaSummary(dataset: Dataset) {
  const schema = (() => {
    try {
      return JSON.parse(dataset.annotationSchema || '{}') as {
        type?: string;
        selectionMode?: string;
        options?: unknown[];
      };
    } catch {
      return {};
    }
  })();

  if (schema.type !== 'classification') {
    return '未配置';
  }

  const mode = schema.selectionMode === 'multiple' ? '多选' : '单选';
  const count = Array.isArray(schema.options) ? schema.options.length : 0;
  return `${mode} · ${count} 个选项`;
}
