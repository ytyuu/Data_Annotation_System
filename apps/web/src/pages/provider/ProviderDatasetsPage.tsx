import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { DonutChart } from '../../components/shared/DonutChart';
import { AppButton } from '../../components/shared/AppButton';
import { SegmentedControl } from '../../components/shared/SegmentedControl';
import { AppModal } from '../../components/shared/AppModal';
import { StatusBadge } from '../../components/shared/StatusBadge';
import { AppAlert } from '../../components/shared/AppAlert';
import { MetricBox } from '../../components/shared/MetricBox';
import { EmptyState } from '../../components/shared/EmptyState';
import { AppTable, AppTableBody, AppTableHead, AppTableRow } from '../../components/shared/AppTable';
import { PageToolbar } from '../../components/shared/PageToolbar';
import { OptionRuleEditor } from '../../components/shared/OptionRuleEditor';
import { StatsPanel } from '../../components/shared/StatsPanel';
import { DistributionBars } from '../../components/shared/DistributionBars';

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

const dataItemStatusLabels: Record<string, string> = {
  pending: '待处理',
  assigned: '已分配',
  annotated: '已标注',
  disputed: '有争议',
  accepted: '已通过',
  rejected: '未通过',
};

const dataItemStatusColors: Record<string, string> = {
  pending: '#9ca3af',
  assigned: '#3b82f6',
  annotated: '#22c55e',
  disputed: '#ef4444',
  accepted: '#8b5cf6',
  rejected: '#f97316',
};

type ProviderDatasetView = 'list' | 'create';

type DatasetFormMode = 'create' | 'edit';

type ImportDialogState = {
  open: boolean;
  dataset: Dataset | null;
};

type DeleteDialogState = {
  open: boolean;
  dataset: Dataset | null;
};

type PublishDialogState = {
  open: boolean;
  dataset: Dataset | null;
};

type ViewDialogState = {
  open: boolean;
  dataset: Dataset | null;
};

type DataItemsDialogState = {
  open: boolean;
  dataset: Dataset | null;
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
}

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

interface DatasetForm {
  name: string;
  description: string;
  annotationGuide: string;
  selectionMode: 'single' | 'multiple';
  options: AnnotationOptionForm[];
  targetCompletionRatio: string;
}

interface AnnotationSubOptionForm {
  id: string;
  label: string;
}

interface AnnotationOptionForm {
  id: string;
  label: string;
  hasSubOptions: boolean;
  subSelectionMode: 'single' | 'multiple';
  subOptions: AnnotationSubOptionForm[];
}

const initialDatasetForm: DatasetForm = {
  name: '',
  description: '',
  annotationGuide: '',
  selectionMode: 'single',
  options: [
    {
      id: 'option-1',
      label: '',
      hasSubOptions: false,
      subSelectionMode: 'single',
      subOptions: [],
    },
    {
      id: 'option-2',
      label: '',
      hasSubOptions: false,
      subSelectionMode: 'single',
      subOptions: [],
    },
  ],
  targetCompletionRatio: '50',
};

export function ProviderDatasetsPage() {
  const navigate = useNavigate();
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(false);
  const [datasetsError, setDatasetsError] = useState('');
  const [datasetForm, setDatasetForm] = useState<DatasetForm>(initialDatasetForm);
  const [providerDatasetView, setProviderDatasetView] = useState<ProviderDatasetView>('list');
  const [datasetFormMode, setDatasetFormMode] = useState<DatasetFormMode>('create');
  const [editingDataset, setEditingDataset] = useState<Dataset | null>(null);
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState('');
  const [importDialog, setImportDialog] = useState<ImportDialogState>({ open: false, dataset: null });
  const [importText, setImportText] = useState('');
  const [importLoading, setImportLoading] = useState(false);
  const [importError, setImportError] = useState('');
  const [deleteDialog, setDeleteDialog] = useState<DeleteDialogState>({ open: false, dataset: null });
  const [deleteLoadingId, setDeleteLoadingId] = useState('');
  const [publishDialog, setPublishDialog] = useState<PublishDialogState>({ open: false, dataset: null });
  const [publishLoadingId, setPublishLoadingId] = useState('');
  const [viewDialog, setViewDialog] = useState<ViewDialogState>({ open: false, dataset: null });
  const [dataItemsDialog, setDataItemsDialog] = useState<DataItemsDialogState>({ open: false, dataset: null });
  const [dataItems, setDataItems] = useState<DataItem[]>([]);
  const [dataItemsLoading, setDataItemsLoading] = useState(false);
  const [dataItemsError, setDataItemsError] = useState('');
  const [exportLoadingId, setExportLoadingId] = useState('');

  useEffect(() => {
    loadProviderDatasets();
  }, []);

  function openCreateDatasetView() {
    setCreateError('');
    setDatasetFormMode('create');
    setEditingDataset(null);
    setDatasetForm(initialDatasetForm);
    setProviderDatasetView('create');
  }

  function closeCreateDatasetView() {
    setCreateError('');
    setEditingDataset(null);
    setProviderDatasetView('list');
  }

  function openEditDatasetView(dataset: Dataset) {
    setCreateError('');
    setDatasetFormMode('edit');
    setEditingDataset(dataset);
    setDatasetForm(datasetToForm(dataset));
    setProviderDatasetView('create');
  }

  function openImportDialog(dataset: Dataset) {
    setImportDialog({ open: true, dataset });
    setImportText('');
    setImportError('');
  }

  function closeImportDialog() {
    setImportDialog({ open: false, dataset: null });
    setImportText('');
    setImportError('');
  }

  function openDeleteDialog(dataset: Dataset) {
    setDeleteDialog({ open: true, dataset });
    setDatasetsError('');
  }

  function closeDeleteDialog() {
    setDeleteDialog({ open: false, dataset: null });
  }

  function openPublishDialog(dataset: Dataset) {
    setPublishDialog({ open: true, dataset });
    setDatasetsError('');
  }

  function closePublishDialog() {
    setPublishDialog({ open: false, dataset: null });
  }

  function openViewDialog(dataset: Dataset) {
    setViewDialog({ open: true, dataset });
  }

  function closeViewDialog() {
    setViewDialog({ open: false, dataset: null });
  }

  function openDataItemsDialog(dataset: Dataset) {
    setDataItemsDialog({ open: true, dataset });
    loadDataItems(dataset);
  }

  function closeDataItemsDialog() {
    setDataItemsDialog({ open: false, dataset: null });
    setDataItems([]);
    setDataItemsError('');
  }

  async function loadProviderDatasets() {
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
        throw new Error(data?.message || `数据集加载失败 (${response.status})`);
      }

      setDatasets(data as Dataset[]);
    } catch (err) {
      setDatasetsError(err instanceof Error ? err.message : '数据集加载失败');
    } finally {
      setDatasetsLoading(false);
    }
  }

  async function loadDataItems(dataset: Dataset) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setDataItemsLoading(true);
    setDataItemsError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/provider/datasets/${dataset.id}/items`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `数据项加载失败 (${response.status})`);
      }

      setDataItems(data as DataItem[]);
    } catch (err) {
      setDataItemsError(err instanceof Error ? err.message : '数据项加载失败');
    } finally {
      setDataItemsLoading(false);
    }
  }

  function updateDatasetForm(field: keyof DatasetForm, value: string) {
    setDatasetForm((current) => ({ ...current, [field]: value }));
  }

  function updateSelectionMode(selectionMode: DatasetForm['selectionMode']) {
    setDatasetForm((current) => ({ ...current, selectionMode }));
  }

  function updateAnnotationOption(id: string, label: string) {
    setDatasetForm((current) => ({
      ...current,
      options: current.options.map((option) => (
        option.id === id ? { ...option, label } : option
      )),
    }));
  }

  function addAnnotationOption() {
    setDatasetForm((current) => ({
      ...current,
      options: [
        ...current.options,
        {
          id: `option-${Date.now()}`,
          label: '',
          hasSubOptions: false,
          subSelectionMode: 'single',
          subOptions: [],
        },
      ],
    }));
  }

  function removeAnnotationOption(id: string) {
    setDatasetForm((current) => ({
      ...current,
      options: current.options.length <= 2
        ? current.options
        : current.options.filter((option) => option.id !== id),
    }));
  }

  function toggleSubOptions(id: string) {
    setDatasetForm((current) => ({
      ...current,
      options: current.options.map((option) =>
        option.id === id
          ? { ...option, hasSubOptions: !option.hasSubOptions }
          : option
      ),
    }));
  }

  function updateSubSelectionMode(id: string, subSelectionMode: AnnotationOptionForm['subSelectionMode']) {
    setDatasetForm((current) => ({
      ...current,
      options: current.options.map((option) =>
        option.id === id
          ? { ...option, subSelectionMode }
          : option
      ),
    }));
  }

  function addSubOption(optionId: string) {
    setDatasetForm((current) => ({
      ...current,
      options: current.options.map((option) =>
        option.id === optionId
          ? {
              ...option,
              subOptions: [
                ...option.subOptions,
                { id: `sub-${Date.now()}`, label: '' },
              ],
            }
          : option
      ),
    }));
  }

  function removeSubOption(optionId: string, subOptionId: string) {
    setDatasetForm((current) => ({
      ...current,
      options: current.options.map((option) =>
        option.id === optionId
          ? {
              ...option,
              subOptions: option.subOptions.length <= 2
                ? option.subOptions
                : option.subOptions.filter((sub: AnnotationSubOptionForm) => sub.id !== subOptionId),
            }
          : option
      ),
    }));
  }

  function updateSubOption(optionId: string, subOptionId: string, label: string) {
    setDatasetForm((current) => ({
      ...current,
      options: current.options.map((option) =>
        option.id === optionId
          ? {
              ...option,
              subOptions: option.subOptions.map((sub) =>
                sub.id === subOptionId ? { ...sub, label } : sub
              ),
            }
          : option
      ),
    }));
  }

  function buildAnnotationSchema() {
    let optionIndex = 0;
    const normalizedOptions = datasetForm.options
      .filter((option) => option.label.trim().length > 0)
      .map((option) => {
        optionIndex++;
        const mainValue = `option_${optionIndex}`;
        const normalized: Record<string, unknown> = {
          value: mainValue,
          label: option.label.trim(),
        };
        if (option.hasSubOptions && option.subOptions.some((s: AnnotationSubOptionForm) => s.label.trim().length > 0)) {
          normalized.hasSubOptions = true;
          normalized.subSelectionMode = option.subSelectionMode;
          let subIndex = 0;
          normalized.subOptions = option.subOptions
            .filter((s: AnnotationSubOptionForm) => s.label.trim().length > 0)
            .map((s: AnnotationSubOptionForm) => {
              subIndex++;
              return {
                value: `sub_${optionIndex}_${subIndex}`,
                label: s.label.trim(),
              };
            });
        }
        return normalized;
      });

    return {
      version: 1,
      type: 'classification',
      selectionMode: datasetForm.selectionMode,
      options: normalizedOptions,
    };
  }

  function datasetToForm(dataset: Dataset): DatasetForm {
    const schema = (() => {
      try {
        return JSON.parse(dataset.annotationSchema || '{}') as {
          selectionMode?: string;
          options?: {
            label?: string;
            hasSubOptions?: boolean;
            subSelectionMode?: string;
            subOptions?: { label?: string }[];
          }[];
        };
      } catch {
        return {};
      }
    })();

    const options = Array.isArray(schema.options)
      ? schema.options
        .map((option, index) => ({
          id: `option-${index + 1}`,
          label: option.label?.trim() || '',
          hasSubOptions: option.hasSubOptions || false,
          subSelectionMode: (option.subSelectionMode === 'multiple' ? 'multiple' : 'single') as 'single' | 'multiple',
          subOptions: Array.isArray(option.subOptions)
            ? option.subOptions.map((s, sidx) => ({
                id: `sub-${index + 1}-${sidx + 1}`,
                label: s.label?.trim() || '',
              }))
            : [],
        }))
        .filter((option) => option.label.length > 0)
      : [];

    return {
      name: dataset.name,
      description: dataset.description || '',
      annotationGuide: dataset.annotationGuide || '',
      selectionMode: schema.selectionMode === 'multiple' ? 'multiple' : 'single',
      options: options.length >= 2 ? options : initialDatasetForm.options,
      targetCompletionRatio: dataset.targetCompletionRatio,
    };
  }

  function getDatasetSchemaSummary(dataset: Dataset) {
    const schema = (() => {
      try {
        return JSON.parse(dataset.annotationSchema || '{}') as {
          type?: string;
          selectionMode?: string;
          options?: { hasSubOptions?: boolean; subOptions?: unknown[] }[];
        };
      } catch {
        return {};
      }
    })();

    if (schema.type !== 'classification') {
      return '未配置';
    }

    const mode = schema.selectionMode === 'multiple' ? '多选' : '单选';
    const options = Array.isArray(schema.options) ? schema.options : [];
    const subOptionCount = options.reduce(
      (sum, opt) => sum + (opt.hasSubOptions ? (Array.isArray(opt.subOptions) ? opt.subOptions.length : 0) : 0),
      0,
    );
    const hasSubs = subOptionCount > 0;
    return `${mode} · ${options.length} 个选项${hasSubs ? ' · 含子选项' : ''}`;
  }

  async function handleSaveDataset(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError('');

    if (!datasetForm.name.trim()) {
      setCreateError('请输入数据集名称');
      return;
    }

    const annotationSchema = buildAnnotationSchema();
    if (annotationSchema.options.length < 2) {
      setCreateError('请至少填写 2 个标注选项');
      return;
    }

    const uniqueOptionLabels = new Set(annotationSchema.options.map((option) => option.label));
    if (uniqueOptionLabels.size !== annotationSchema.options.length) {
      setCreateError('标注选项不能重复');
      return;
    }

    // 校验同一主选项下的子选项标签是否重复
    for (const option of annotationSchema.options) {
      if (option.subOptions && Array.isArray(option.subOptions)) {
        const subLabels = option.subOptions.map((sub: { label: string }) => sub.label);
        const uniqueSubLabels = new Set(subLabels);
        if (uniqueSubLabels.size !== subLabels.length) {
          setCreateError(`「${option.label}」的子选项不能重复`);
          return;
        }
      }
    }

    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setCreateLoading(true);
    try {
      const isEditing = datasetFormMode === 'edit' && editingDataset;
      const response = await fetch(
        isEditing
          ? `${apiBaseUrl}/api/provider/datasets/${editingDataset.id}`
          : `${apiBaseUrl}/api/provider/datasets`,
        {
          method: isEditing ? 'PUT' : 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            name: datasetForm.name,
            description: datasetForm.description,
            annotationGuide: datasetForm.annotationGuide,
            annotationSchema: JSON.stringify(annotationSchema),
            targetCompletionRatio: datasetForm.targetCompletionRatio,
          }),
        }
      );
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `${isEditing ? '更新' : '创建'}失败 (${response.status})`);
      }

      setDatasetForm(initialDatasetForm);
      await loadProviderDatasets();
      closeCreateDatasetView();
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : '保存失败，请重试');
    } finally {
      setCreateLoading(false);
    }
  }

  async function handleImportItems(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setImportError('');

    const dataset = importDialog.dataset;
    if (!dataset) {
      return;
    }

    const lines = importText
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line.length > 0);

    if (lines.length === 0) {
      setImportError('请至少输入 1 条数据项');
      return;
    }

    if (lines.length > 500) {
      setImportError('单次最多导入 500 条数据项');
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setImportLoading(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/provider/datasets/${dataset.id}/items`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          items: lines.map((content) => ({
            content,
            contentType: 'text',
            metadata: '{}',
          })),
        }),
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `导入失败 (${response.status})`);
      }

      closeImportDialog();
      await loadProviderDatasets();
    } catch (err) {
      setImportError(err instanceof Error ? err.message : '导入失败，请重试');
    } finally {
      setImportLoading(false);
    }
  }

  async function handleConfirmDeleteDataset() {
    const dataset = deleteDialog.dataset;
    if (!dataset) {
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setDeleteLoadingId(dataset.id);
    try {
      const response = await fetch(`${apiBaseUrl}/api/provider/datasets/${dataset.id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `删除失败 (${response.status})`);
      }

      await loadProviderDatasets();
      closeDeleteDialog();
    } catch (err) {
      setDatasetsError(err instanceof Error ? err.message : '删除失败，请重试');
    } finally {
      setDeleteLoadingId('');
    }
  }

  async function handleConfirmPublishDataset() {
    const dataset = publishDialog.dataset;
    if (!dataset) {
      return;
    }

    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setPublishLoadingId(dataset.id);
    setDatasetsError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/provider/datasets/${dataset.id}/publish`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `发布失败 (${response.status})`);
      }

      await loadProviderDatasets();
      closePublishDialog();
    } catch (err) {
      setDatasetsError(err instanceof Error ? err.message : '发布失败，请重试');
    } finally {
      setPublishLoadingId('');
    }
  }

  function escapeCsvField(value: string): string {
    if (value.includes(',') || value.includes('"') || value.includes('\n') || value.includes('\r')) {
      return '"' + value.replace(/"/g, '""') + '"';
    }
    return value;
  }

  function buildCsvContent(datasetName: string, items: DataItem[]): string {
    const headers = ['dataset_name', 'data_item_id', 'content', 'content_type', 'metadata', 'final_result'];
    const lines: string[] = [headers.join(',')];

    for (const item of items) {
      const fields = [
        datasetName,
        item.id,
        item.content,
        item.contentType,
        item.metadata,
        item.finalResult || '',
      ];
      lines.push(fields.map((f) => escapeCsvField(String(f))).join(','));
    }

    return '\uFEFF' + lines.join('\n');
  }

  function triggerDownload(filename: string, content: string) {
    const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  async function handleExportDataset(dataset: Dataset) {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setExportLoadingId(dataset.id);
    setDatasetsError('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/provider/datasets/${dataset.id}/items`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `数据项加载失败 (${response.status})`);
      }

      const allItems = data as DataItem[];
      const exportableItems = allItems.filter((item) => item.finalResult !== null && item.finalResult !== '');

      if (exportableItems.length === 0) {
        setDatasetsError(`数据集「${dataset.name}」没有已确认 final_result 的数据项，无法导出。`);
        return;
      }

      const csvContent = buildCsvContent(dataset.name, exportableItems);
      const filename = `${dataset.name}_export_${new Date().toISOString().slice(0, 10)}.csv`;
      triggerDownload(filename, csvContent);
    } catch (err) {
      setDatasetsError(err instanceof Error ? err.message : '导出失败，请重试');
    } finally {
      setExportLoadingId('');
    }
  }

  function renderCreateDatasetDialog() {
    if (providerDatasetView !== 'create') {
      return null;
    }

    return (
      <form onSubmit={handleSaveDataset}>
        <AppModal
          title={datasetFormMode === 'edit' ? '编辑数据集' : '创建数据集'}
          subtitle="填写数据集基本信息和打标签选项配置。"
          width="xl"
          contentClassName="max-h-[calc(100vh-220px)] overflow-y-auto px-6 py-5"
          actions={
            <AppButton
              type="button"
              variant="secondary"
              size="sm"
              onClick={closeCreateDatasetView}
            >
              关闭
            </AppButton>
          }
          footer={
            <>
              <AppButton
                type="button"
                variant="secondary"
                onClick={closeCreateDatasetView}
              >
                取消
              </AppButton>
              <AppButton
                type="submit"
                variant="primary"
                disabled={createLoading}
              >
                {createLoading
                  ? (datasetFormMode === 'edit' ? '保存中...' : '创建中...')
                  : (datasetFormMode === 'edit' ? '保存修改' : '创建数据集')}
              </AppButton>
            </>
          }
        >
              {createError && <AppAlert kind="error" className="mb-6">{createError}</AppAlert>}

              <div className="app-field">
                <label htmlFor="dataset-name" className="app-label">
                  数据集名称
                </label>
                <input
                  id="dataset-name"
                  type="text"
                  value={datasetForm.name}
                  onChange={(event) => updateDatasetForm('name', event.target.value)}
                  className="app-input"
                  maxLength={120}
                  placeholder="例如：客服评论情感标注"
                />
              </div>

              <div className="app-field">
                <label htmlFor="dataset-description" className="app-label">
                  数据集描述
                </label>
                <textarea
                  id="dataset-description"
                  value={datasetForm.description}
                  onChange={(event) => updateDatasetForm('description', event.target.value)}
                  className="app-input min-h-24 resize-y"
                  placeholder="说明数据来源、用途和标注目标"
                />
              </div>

              <div className="app-field">
                <label htmlFor="annotation-guide" className="app-label">
                  标注说明
                </label>
                <textarea
                  id="annotation-guide"
                  value={datasetForm.annotationGuide}
                  onChange={(event) => updateDatasetForm('annotationGuide', event.target.value)}
                  className="app-input min-h-32 resize-y"
                  placeholder="写明标注规则、边界情况和提交要求"
                />
              </div>

              <div className="app-field">
                <div className="app-label">标注类型</div>
                <div className="inline-flex rounded border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-medium text-blue-700">
                  打标签
                </div>
              </div>

              <div className="app-field">
                <div className="app-label">选项规则</div>
                <SegmentedControl
                  value={datasetForm.selectionMode}
                  options={[
                    { value: 'single', label: '单选' },
                    { value: 'multiple', label: '多选' },
                  ]}
                  onChange={updateSelectionMode}
                  size="lg"
                />
              </div>

              <OptionRuleEditor
                options={datasetForm.options}
                onAddOption={addAnnotationOption}
                onRemoveOption={removeAnnotationOption}
                onChangeOptionLabel={updateAnnotationOption}
                onToggleSubOptions={toggleSubOptions}
                onChangeSubSelectionMode={updateSubSelectionMode}
                onAddSubOption={addSubOption}
                onRemoveSubOption={removeSubOption}
                onChangeSubOptionLabel={updateSubOption}
              />

              <div className="app-field max-w-xs">
                <label htmlFor="completion-ratio" className="app-label">
                  目标完成比例
                </label>
                <div className="flex items-center gap-2">
                  <input
                    id="completion-ratio"
                    type="number"
                    min="1"
                    max="100"
                    step="0.01"
                    value={datasetForm.targetCompletionRatio}
                    onChange={(event) => updateDatasetForm('targetCompletionRatio', event.target.value)}
                    className="app-input"
                  />
                  <span className="text-sm text-gray-500">%</span>
                </div>
              </div>
        </AppModal>
      </form>
    );
  }

  function renderImportItemsDialog() {
    if (!importDialog.open || !importDialog.dataset) {
      return null;
    }

    return (
      <form onSubmit={handleImportItems}>
        <AppModal
          title="导入数据项"
          subtitle={importDialog.dataset.name}
          width="lg"
          actions={
            <AppButton
              type="button"
              variant="secondary"
              size="sm"
              onClick={closeImportDialog}
            >
              关闭
            </AppButton>
          }
          footer={
            <>
              <AppButton
                type="button"
                variant="secondary"
                onClick={closeImportDialog}
              >
                取消
              </AppButton>
              <AppButton
                type="submit"
                variant="primary"
                disabled={importLoading}
              >
                {importLoading ? '导入中...' : '导入数据项'}
              </AppButton>
            </>
          }
        >
              {importError && <AppAlert kind="error" className="mb-6">{importError}</AppAlert>}

              <label htmlFor="import-items" className="app-label">
                文本数据
              </label>
              <textarea
                id="import-items"
                value={importText}
                onChange={(event) => setImportText(event.target.value)}
                className="app-input min-h-72 resize-y font-mono text-sm"
                placeholder="每行一条数据，例如：\n这次服务很好\n配送速度太慢\n商品包装完整"
              />
              <div className="mt-2 text-xs text-gray-500">
                当前支持按行导入文本数据，空行会自动忽略。
              </div>
        </AppModal>
      </form>
    );
  }

  function renderDeleteDatasetDialog() {
    if (!deleteDialog.open || !deleteDialog.dataset) {
      return null;
    }

    return (
      <AppModal
        title="删除数据集"
        subtitle={`确定删除“${deleteDialog.dataset.name}”吗？删除后该数据集下的数据项也会一并删除。`}
        width="md"
        contentClassName="hidden"
        footer={
          <>
            <AppButton
              type="button"
              variant="secondary"
              onClick={closeDeleteDialog}
            >
              取消
            </AppButton>
            <AppButton
              type="button"
              variant="danger"
              disabled={deleteLoadingId === deleteDialog.dataset.id}
              onClick={handleConfirmDeleteDataset}
            >
              {deleteLoadingId === deleteDialog.dataset.id ? '删除中...' : '确认删除'}
            </AppButton>
          </>
        }
      >
      </AppModal>
    );
  }

  function renderPublishDatasetDialog() {
    if (!publishDialog.open || !publishDialog.dataset) {
      return null;
    }

    const dataset = publishDialog.dataset;
    const canPublish = dataset.itemCount > 0;

    return (
      <AppModal
        title="发布数据集"
        subtitle={`发布“${dataset.name}”后，标注员将可以在可标注数据集中看到它。`}
        width="md"
        footer={
          <>
            <AppButton
              type="button"
              variant="secondary"
              onClick={closePublishDialog}
            >
              取消
            </AppButton>
            <AppButton
              type="button"
              variant="primary"
              disabled={!canPublish || publishLoadingId === dataset.id}
              onClick={handleConfirmPublishDataset}
            >
              {publishLoadingId === dataset.id ? '发布中...' : '确认发布'}
            </AppButton>
          </>
        }
      >
        {!canPublish ? (
          <AppAlert kind="error">
            发布前请先导入至少 1 条数据项。
          </AppAlert>
        ) : null}
      </AppModal>
    );
  }

  function renderViewDatasetDialog() {
    if (!viewDialog.open || !viewDialog.dataset) {
      return null;
    }

    const dataset = viewDialog.dataset;
    const schema = (() => {
      try {
        return JSON.parse(dataset.annotationSchema || '{}') as {
          type?: string;
          selectionMode?: string;
          options?: { value: string; label: string }[];
        };
      } catch {
        return {};
      }
    })();

    return (
      <AppModal
        title="数据集信息"
        subtitle="查看数据集的基本配置和状态"
        width="lg"
        contentClassName="max-h-[calc(100vh-220px)] overflow-y-auto px-6 py-5"
        actions={
          <>
              {dataset.status === 'draft' && (
                <AppButton
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    closeViewDialog();
                    openEditDatasetView(dataset);
                  }}
                >
                  编辑
                </AppButton>
              )}
              <AppButton
                type="button"
                variant="secondary"
                size="sm"
                onClick={closeViewDialog}
              >
                关闭
              </AppButton>
          </>
        }
      >
            <div className="grid grid-cols-2 gap-4">
              <div className="app-field">
                <div className="app-label">数据集名称</div>
                <div className="text-sm text-gray-900">{dataset.name}</div>
              </div>
              <div className="app-field">
                <div className="app-label">状态</div>
                <StatusBadge status={dataset.status}>
                  {datasetStatusLabels[dataset.status] || dataset.status}
                </StatusBadge>
              </div>
              <div className="app-field">
                <div className="app-label">数据项</div>
                <div className="text-sm text-gray-900">
                  {dataset.completedItemCount} / {dataset.itemCount} 已完成
                </div>
              </div>
              <div className="app-field">
                <div className="app-label">目标完成比例</div>
                <div className="text-sm text-gray-900">{dataset.targetCompletionRatio}%</div>
              </div>
              <div className="app-field">
                <div className="app-label">创建时间</div>
                <div className="text-sm text-gray-900">
                  {new Date(dataset.createdAt).toLocaleString()}
                </div>
              </div>
              <div className="app-field">
                <div className="app-label">更新时间</div>
                <div className="text-sm text-gray-900">
                  {new Date(dataset.updatedAt).toLocaleString()}
                </div>
              </div>
            </div>

            <div className="app-field">
              <div className="app-label">数据集描述</div>
              <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-900">
                {dataset.description || '无'}
              </div>
            </div>

            <div className="app-field">
              <div className="app-label">标注说明</div>
              <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-900">
                {dataset.annotationGuide || '无'}
              </div>
            </div>

            <div className="app-field">
              <div className="app-label">标注方式</div>
              <div className="text-sm text-gray-900">
                {getDatasetSchemaSummary(dataset)}
              </div>
            </div>

            {schema.type === 'classification' && Array.isArray(schema.options) && schema.options.length > 0 && (
              <div className="app-field">
                <div className="app-label">标注选项</div>
                <div className="space-y-1">
                  {schema.options.map((option, index) => (
                    <div
                      key={option.value}
                      className="flex items-center gap-2 rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm"
                    >
                      <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded bg-gray-200 text-xs text-gray-600">
                        {index + 1}
                      </span>
                      <span className="text-gray-900">{option.label}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
      </AppModal>
    );
  }

  function renderOverviewDialog() {
    if (!dataItemsDialog.open || !dataItemsDialog.dataset) {
      return null;
    }

    const dataset = dataItemsDialog.dataset;

    const statusCounts: Record<string, number> = {};
    dataItems.forEach((item) => {
      statusCounts[item.status] = (statusCounts[item.status] || 0) + 1;
    });

    const totalItems = dataItems.length;
    const hasStats = totalItems > 0 && Object.keys(statusCounts).length > 0;

    return (
      <AppModal
        title="数据集概览"
        subtitle={dataset.name}
        width="lg"
        contentClassName="max-h-[calc(100vh-220px)] overflow-y-auto px-6 py-5"
        actions={
          <>
              {dataset.status === 'draft' && (
                <AppButton
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    closeDataItemsDialog();
                    openImportDialog(dataset);
                  }}
                >
                  导入数据
                </AppButton>
              )}
              <AppButton
                type="button"
                variant="secondary"
                size="sm"
                onClick={closeDataItemsDialog}
              >
                关闭
              </AppButton>
          </>
        }
      >
            {dataItemsError && <AppAlert kind="error" className="mb-6">{dataItemsError}</AppAlert>}

            {dataItemsLoading ? (
              <EmptyState>正在加载数据概览...</EmptyState>
            ) : dataItems.length === 0 ? (
              <EmptyState align="center">还没有数据项</EmptyState>
            ) : (
              <div className="space-y-6">
                <StatsPanel title="数据项状态分布" contentClassName="flex justify-center p-6">
                  {hasStats && (
                    <DonutChart
                      data={statusCounts}
                      total={totalItems}
                      colors={dataItemStatusColors}
                      labels={dataItemStatusLabels}
                    />
                  )}
                </StatsPanel>

                <StatsPanel title="状态明细" footer={<div className="text-right">总计 {totalItems} 条数据项</div>}>
                  <DistributionBars
                    entries={Object.entries(statusCounts).map(([status, count]) => ({
                      key: status,
                      label: dataItemStatusLabels[status] || status,
                      count,
                      color: dataItemStatusColors[status] || '#9ca3af',
                    }))}
                    total={totalItems}
                  />
                </StatsPanel>
              </div>
            )}
      </AppModal>
    );
  }

  function renderProviderContent() {
    return (
      <div>
        <PageToolbar
          actions={
            <>
            <AppButton
              type="button"
              variant="secondary"
              disabled={datasetsLoading}
              onClick={loadProviderDatasets}
            >
              {datasetsLoading ? '刷新中...' : '刷新'}
            </AppButton>
            <AppButton
              type="button"
              variant="primary"
              onClick={openCreateDatasetView}
            >
              创建数据集
            </AppButton>
            </>
          }
        >
          共 {datasets.length} 个数据集
        </PageToolbar>

        {datasetsError && <AppAlert kind="error" className="mb-6">{datasetsError}</AppAlert>}

        {datasetsLoading ? (
          <EmptyState>正在加载数据集...</EmptyState>
        ) : datasets.length === 0 ? (
          <EmptyState align="center" spacious>
            <div className="text-sm text-gray-500">还没有数据集</div>
            <AppButton
              type="button"
              variant="primary"
              className="mt-4"
              onClick={openCreateDatasetView}
            >
              创建第一个数据集
            </AppButton>
          </EmptyState>
        ) : (
          <AppTable>
              <AppTableHead>
                <tr>
                  <th className="w-[30%] px-4 py-3 text-left">名称</th>
                  <th className="px-4 py-3 text-left">状态</th>
                  <th className="px-4 py-3 text-left">标注方式</th>
                  <th className="px-4 py-3 text-left">数据项</th>
                  <th className="px-4 py-3 text-left">审核阈值</th>
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
                      {getDatasetSchemaSummary(dataset)}
                    </td>
                    <td className="px-4 py-4 align-middle">
                      <DatasetItemMetric
                        completed={dataset.completedItemCount}
                        total={dataset.itemCount}
                      />
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-600">
                      {dataset.targetCompletionRatio}%
                    </td>
                    <td className="px-4 py-4 align-middle text-gray-500">
                      {new Date(dataset.updatedAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-4 align-middle">
                      <div className="flex justify-end gap-2">
                        <AppButton
                          type="button"
                          variant="secondary"
                          size="sm"
                          onClick={() => openViewDialog(dataset)}
                        >
                          基本信息
                        </AppButton>
                        <AppButton
                          type="button"
                          variant="secondary"
                          size="sm"
                          onClick={() => openDataItemsDialog(dataset)}
                        >
                          概览
                        </AppButton>
                        {(() => {
                          const completionRatio = dataset.itemCount > 0
                            ? Math.round((dataset.completedItemCount / dataset.itemCount) * 100)
                            : 0;
                          const targetRatio = parseFloat(dataset.targetCompletionRatio);
                          const canExport = completionRatio >= targetRatio && dataset.itemCount > 0;
                          const exportTooltip = canExport
                            ? ''
                            : dataset.itemCount === 0
                              ? '数据集暂无数据项，无法导出'
                              : `当前完成率 ${completionRatio}%，需达到 ${targetRatio}% 才能导出`;
                          return (
                            <span title={exportTooltip} className="inline-block">
                              <AppButton
                                type="button"
                                disabled={!canExport || exportLoadingId === dataset.id}
                                className="rounded border border-green-200 px-3 py-1.5 text-xs font-medium text-green-700 hover:bg-green-50 disabled:cursor-not-allowed disabled:opacity-50"
                                onClick={() => handleExportDataset(dataset)}
                              >
                                {exportLoadingId === dataset.id ? '导出中' : '导出'}
                              </AppButton>
                            </span>
                          );
                        })()}
                        {dataset.status === 'draft' && (
                          <>
                            <AppButton
                              type="button"
                              className="rounded border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-gray-100"
                              onClick={() => openImportDialog(dataset)}
                            >
                              导入数据
                            </AppButton>
                            <AppButton
                              type="button"
                              disabled={publishLoadingId === dataset.id}
                              className="rounded border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-700 hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
                              onClick={() => openPublishDialog(dataset)}
                            >
                              {publishLoadingId === dataset.id ? '发布中' : '发布'}
                            </AppButton>
                            <AppButton
                              type="button"
                              disabled={deleteLoadingId === dataset.id}
                              className="rounded border border-red-200 px-3 py-1.5 text-xs font-medium text-red-600 hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-50"
                              onClick={() => openDeleteDialog(dataset)}
                            >
                              {deleteLoadingId === dataset.id ? '删除中' : '删除'}
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

  return (
    <div>
      {renderProviderContent()}
      {renderCreateDatasetDialog()}
      {renderImportItemsDialog()}
      {renderDeleteDatasetDialog()}
      {renderPublishDatasetDialog()}
      {renderViewDatasetDialog()}
      {renderOverviewDialog()}
    </div>
  );
}

