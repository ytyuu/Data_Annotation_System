import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  batchAcceptAiResults,
  createAiBatch,
  listAiBatches,
  listAiResults,
  listProviderDatasets,
  reviewAiResult,
  runAiBatch,
  type AiBatch,
  type AiResult,
  type CreateBatchInput,
  type ProviderDataset,
} from '../../api/aiAnnotation';
import { AppAlert } from '../../components/shared/AppAlert';
import { AppButton } from '../../components/shared/AppButton';
import { AppModal } from '../../components/shared/AppModal';
import { AppTable, AppTableBody, AppTableHead, AppTableRow } from '../../components/shared/AppTable';
import { DataItemViewer } from '../../components/shared/DataItemViewer';
import { AnnotationEditor, type AnnotationSchema, type AnnotationSelection } from '../../components/shared/AnnotationEditor';
import { AnnotationResultViewer, parseAnnotationSelection } from '../../components/shared/AnnotationResultViewer';
import { buildAnnotationResult } from '../../components/shared/AnnotationResultBuilder';
import { EmptyState } from '../../components/shared/EmptyState';
import { PageToolbar } from '../../components/shared/PageToolbar';
import { SegmentedControl } from '../../components/shared/SegmentedControl';
import { StatusBadge } from '../../components/shared/StatusBadge';

type ResultView = 'mandatory' | 'sampling' | 'low-risk' | 'failed' | 'accepted';
type ReviewCounts = Record<'mandatory' | 'sampling' | 'low-risk', number>;

const emptyReviewCounts: ReviewCounts = { mandatory: 0, sampling: 0, 'low-risk': 0 };
const autoAdvanceResultViews = new Set<ResultView>(['mandatory', 'sampling', 'low-risk']);
const autoAdvanceStorageKey = 'provider-ai-review-auto-advance';

const batchStatusLabels: Record<string, string> = {
  pending: '等待执行', running: '执行中', completed: '执行完成', failed: '执行失败', cancelled: '已取消',
};

const resultStatusLabels: Record<string, string> = {
  pending: '待执行', processing: '执行中', ai_labeled: '待采用', needs_review: '必须审核',
  accepted: '已采用', rejected: '已转人工', failed: '执行失败',
};

const datasetStatusLabels: Record<string, string> = {
  in_progress: '标注中',
  reviewing: '审核中',
};

const initialForm: CreateBatchInput = {
  maxItems: 1000,
  modelName: 'deepseek-v4-flash',
  promptVersion: 'classification-v1',
  confidenceThreshold: 0.85,
  samplingRatio: 0.1,
  highRiskOptionValues: [],
  metadataAllowList: [],
  maxAttempts: 3,
};

export function ProviderAiAnnotationsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [datasets, setDatasets] = useState<ProviderDataset[]>([]);
  const [selectedDatasetId, setSelectedDatasetId] = useState(searchParams.get('datasetId') || '');
  const [allBatches, setAllBatches] = useState<AiBatch[]>([]);
  const [selectedBatchId, setSelectedBatchId] = useState('');
  const [results, setResults] = useState<AiResult[]>([]);
  const [resultTotal, setResultTotal] = useState(0);
  const [reviewCounts, setReviewCounts] = useState<ReviewCounts>(emptyReviewCounts);
  const [resultView, setResultView] = useState<ResultView>('mandatory');
  const [selectedResultIds, setSelectedResultIds] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [resultsLoading, setResultsLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [createDatasetOpen, setCreateDatasetOpen] = useState(false);
  const [createDatasetId, setCreateDatasetId] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateBatchInput>(initialForm);
  const [saving, setSaving] = useState(false);
  const [copiedBatchId, setCopiedBatchId] = useState('');
  const [runningBatchId, setRunningBatchId] = useState('');
  const [runConfirmBatch, setRunConfirmBatch] = useState<AiBatch | null>(null);
  const [reviewResult, setReviewResult] = useState<AiResult | null>(null);
  const [reviewComment, setReviewComment] = useState('');
  const [modifyMode, setModifyMode] = useState(false);
  const [modifiedSelection, setModifiedSelection] = useState<AnnotationSelection>({ main: [], sub: {} });
  const [reviewing, setReviewing] = useState(false);
  const [autoAdvance, setAutoAdvance] = useState(() => window.localStorage.getItem(autoAdvanceStorageKey) !== 'false');
  const preferredBatchIdRef = useRef<string | null>(null);
  const pollingIntervalRef = useRef<number | null>(null);
  const reviewCountsRequestRef = useRef(0);

  const eligibleDatasets = useMemo(
    () => datasets.filter((dataset) => dataset.status === 'in_progress' || dataset.status === 'reviewing'),
    [datasets],
  );
  const batches = useMemo(
    () => selectedDatasetId
      ? allBatches.filter((batch) => batch.datasetId === selectedDatasetId)
      : allBatches,
    [allBatches, selectedDatasetId],
  );
  const batchCounts = useMemo(() => allBatches.reduce<Record<string, number>>((counts, batch) => {
    counts[batch.datasetId] = (counts[batch.datasetId] ?? 0) + 1;
    return counts;
  }, {}), [allBatches]);
  const selectedDataset = datasets.find((dataset) => dataset.id === selectedDatasetId) ?? null;
  const createDataset = eligibleDatasets.find((dataset) => dataset.id === createDatasetId) ?? null;
  const selectedBatch = batches.find((batch) => batch.id === selectedBatchId) ?? null;
  const activeDataset = selectedDataset
    ?? datasets.find((dataset) => dataset.id === selectedBatch?.datasetId)
    ?? null;
  const annotationSchema = useMemo(() => parseSchema(activeDataset?.annotationSchema), [activeDataset]);
  const createAnnotationSchema = useMemo(() => parseSchema(createDataset?.annotationSchema), [createDataset]);
  const canReviewResult = reviewResult?.status === 'ai_labeled'
    || reviewResult?.status === 'needs_review'
    || reviewResult?.status === 'failed';
  const reviewResultIndex = reviewResult ? results.findIndex((result) => result.id === reviewResult.id) : -1;
  const supportsAutoAdvance = autoAdvanceResultViews.has(resultView);

  useEffect(() => { void loadDatasets(); }, []);

  useEffect(() => {
    setSearchParams(selectedDatasetId ? { datasetId: selectedDatasetId } : {}, { replace: true });
    setSelectedBatchId((current) => {
      const preferredBatchId = preferredBatchIdRef.current;
      if (preferredBatchId && batches.some((batch) => batch.id === preferredBatchId)) {
        preferredBatchIdRef.current = null;
        return preferredBatchId;
      }
      return batches.some((batch) => batch.id === current) ? current : batches[0]?.id || '';
    });
  }, [batches, selectedDatasetId, setSearchParams]);

  useEffect(() => {
    if (!selectedBatchId) {
      setResults([]);
      setResultTotal(0);
      reviewCountsRequestRef.current += 1;
      setReviewCounts(emptyReviewCounts);
      return;
    }
    void loadResults();
  }, [selectedBatchId, resultView]);

  useEffect(() => {
    if (!selectedBatchId) return;
    void loadReviewCounts(selectedBatchId);
  }, [selectedBatchId, selectedBatch?.status]);

  useEffect(() => {
    if (!selectedBatch) return;
    const shouldPoll = selectedBatch.status === 'running' || runningBatchId === selectedBatch.id;
    if (!shouldPoll) return;
    const batchId = selectedBatch.id;
    const datasetId = selectedBatch.datasetId;

    pollingIntervalRef.current = window.setInterval(() => {
      void refreshDatasetBatches(datasetId);
    }, 3000);
    return () => {
      if (pollingIntervalRef.current !== null) {
        window.clearInterval(pollingIntervalRef.current);
        pollingIntervalRef.current = null;
      }
    };
  }, [selectedBatch?.id, selectedBatch?.status, selectedBatch?.datasetId, runningBatchId]);

  async function loadDatasets(preferredDatasetId?: string, preferredBatchId?: string) {
    setLoading(true);
    setError('');
    try {
      const data = await listProviderDatasets();
      setDatasets(data);
      const requested = searchParams.get('datasetId');
      const eligible = data.filter((dataset) => dataset.status === 'in_progress' || dataset.status === 'reviewing');
      const requestedId = preferredDatasetId ?? requested;
      const nextId = eligible.some((dataset) => dataset.id === requestedId) ? requestedId! : '';
      preferredBatchIdRef.current = preferredBatchId ?? null;
      setSelectedDatasetId(nextId);
      await loadAllBatches(eligible);
    } catch (err) {
      setError(errorMessage(err, '数据集加载失败'));
    } finally {
      setLoading(false);
    }
  }

  async function loadAllBatches(sourceDatasets: ProviderDataset[]) {
    setError('');
    try {
      const groups = await Promise.all(sourceDatasets.map((dataset) => listAiBatches(dataset.id)));
      const data = sortBatches(groups.flat());
      setAllBatches(data);
      setRunningBatchId((current) => {
        if (!current) return current;
        const trackedBatch = data.find((batch) => batch.id === current);
        return trackedBatch && ['pending', 'running'].includes(trackedBatch.status) ? current : '';
      });
    } catch (err) {
      setError(errorMessage(err, '批次加载失败'));
      setAllBatches([]);
      setSelectedBatchId('');
    }
  }

  async function refreshDatasetBatches(datasetId: string) {
    setError('');
    try {
      const data = await listAiBatches(datasetId);
      setAllBatches((current) => sortBatches([
        ...current.filter((batch) => batch.datasetId !== datasetId),
        ...data,
      ]));
      setRunningBatchId((current) => {
        if (!current) return current;
        const trackedBatch = data.find((batch) => batch.id === current);
        return trackedBatch && ['pending', 'running'].includes(trackedBatch.status) ? current : '';
      });
    } catch (err) {
      setError(errorMessage(err, '批次加载失败'));
    }
  }

  async function loadResults(): Promise<AiResult[] | null> {
    if (!selectedBatchId) return [];
    setResultsLoading(true);
    setError('');
    setSelectedResultIds([]);
    try {
      const options = resultView === 'mandatory' ? { status: 'needs_review' }
        : resultView === 'sampling' ? { status: 'ai_labeled', reviewMode: 'sampling' }
          : resultView === 'low-risk' ? { status: 'ai_labeled' }
            : { status: resultView };
      const response = await listAiResults(selectedBatchId, options);
      const items = resultView === 'low-risk' ? response.items.filter((item) => !item.isSampled) : response.items;
      setResults(items);
      setResultTotal(resultView === 'low-risk' ? items.length : response.total);
      return items;
    } catch (err) {
      setError(errorMessage(err, '结果加载失败'));
      setResults([]);
      setResultTotal(0);
      return null;
    } finally {
      setResultsLoading(false);
    }
  }

  async function loadReviewCounts(batchId: string) {
    const requestId = ++reviewCountsRequestRef.current;
    try {
      const [mandatory, sampling, allLowRisk] = await Promise.all([
        listAiResults(batchId, { status: 'needs_review', pageSize: 1 }),
        listAiResults(batchId, { status: 'ai_labeled', reviewMode: 'sampling', pageSize: 1 }),
        listAiResults(batchId, { status: 'ai_labeled', pageSize: 1 }),
      ]);
      if (requestId !== reviewCountsRequestRef.current) return;
      setReviewCounts({
        mandatory: mandatory.total,
        sampling: sampling.total,
        'low-risk': Math.max(0, allLowRisk.total - sampling.total),
      });
    } catch {
      if (requestId === reviewCountsRequestRef.current) setReviewCounts(emptyReviewCounts);
    }
  }

  function openCreateDialog() {
    setCreateForm(initialForm);
    setCreateDatasetId(eligibleDatasets.some((dataset) => dataset.id === selectedDatasetId) ? selectedDatasetId : '');
    setCreateDatasetOpen(true);
    setError('');
  }

  function continueCreateDialog() {
    if (!createDataset || remainingItems(createDataset) < 1) return;
    setCreateForm((form) => ({
      ...form,
      maxItems: Math.min(form.maxItems, remainingItems(createDataset)),
      highRiskOptionValues: [],
    }));
    setCreateDatasetOpen(false);
    setCreateOpen(true);
  }

  async function handleCreateBatch(event: React.FormEvent) {
    event.preventDefault();
    if (!createDatasetId) return;
    setSaving(true);
    setError('');
    try {
      const batch = await createAiBatch(createDatasetId, createForm);
      setCreateOpen(false);
      await loadDatasets(createDatasetId, batch.id);
    } catch (err) {
      setError(errorMessage(err, '批次创建失败'));
    } finally {
      setSaving(false);
    }
  }

  function openReview(result: AiResult) {
    setReviewResult(result);
    setReviewComment('');
    setModifyMode(false);
    setModifiedSelection(parseAnnotationSelection(JSON.stringify(result.result || {}), annotationSchema));
    setNotice('');
  }

  function moveReviewResult(offset: -1 | 1) {
    if (reviewResultIndex < 0 || reviewing || modifyMode) return;
    const nextResult = results[reviewResultIndex + offset];
    if (nextResult) openReview(nextResult);
  }

  function handleAutoAdvanceChange(enabled: boolean) {
    setAutoAdvance(enabled);
    window.localStorage.setItem(autoAdvanceStorageKey, String(enabled));
  }

  async function handleReview(action: 'accept' | 'modify_accept' | 'reject_to_human' | 'reject_retry') {
    if (!reviewResult) return;
    const reviewedIndex = Math.max(0, results.findIndex((result) => result.id === reviewResult.id));
    setReviewing(true);
    setError('');
    setNotice('');
    try {
      const acceptedResult = action === 'modify_accept'
        ? JSON.parse(buildAnnotationResult(modifiedSelection, annotationSchema)) as Record<string, unknown>
        : null;
      await reviewAiResult(reviewResult.id, { action, acceptedResult, comment: reviewComment });
      const [refreshedResults] = await Promise.all([
        loadResults(),
        loadReviewCounts(selectedBatchId),
        selectedBatch ? refreshDatasetBatches(selectedBatch.datasetId) : Promise.resolve(),
      ]);
      if (refreshedResults === null) {
        setReviewResult(null);
        return;
      }
      if (autoAdvance && supportsAutoAdvance && refreshedResults.length > 0) {
        openReview(refreshedResults[Math.min(reviewedIndex, refreshedResults.length - 1)]);
      } else {
        setReviewResult(null);
        if (autoAdvance && supportsAutoAdvance && refreshedResults.length === 0) {
          setNotice('当前分类已处理完成');
        }
      }
    } catch (err) {
      setError(errorMessage(err, '审核操作失败'));
    } finally {
      setReviewing(false);
    }
  }

  async function handleBatchAccept() {
    if (!selectedBatchId || selectedResultIds.length === 0) return;
    if (!window.confirm(`确认采用选中的 ${selectedResultIds.length} 条低风险 AI 结果？采用后将进入数据集最终审核流程。`)) return;
    setReviewing(true);
    setError('');
    try {
      await batchAcceptAiResults(selectedBatchId, selectedResultIds);
      await Promise.all([
        loadResults(),
        loadReviewCounts(selectedBatchId),
        selectedBatch ? refreshDatasetBatches(selectedBatch.datasetId) : Promise.resolve(),
      ]);
    } catch (err) {
      setError(errorMessage(err, '批量接受失败'));
    } finally {
      setReviewing(false);
    }
  }

  function toggleResult(id: string) {
    setSelectedResultIds((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  }

  async function copyBatchId(batchId: string) {
    try {
      await navigator.clipboard.writeText(batchId);
      setCopiedBatchId(batchId);
      window.setTimeout(() => setCopiedBatchId((current) => current === batchId ? '' : current), 1500);
    } catch {
      setError('复制失败，请手动选择批次 UUID');
    }
  }

  async function handleRunBatch(batchId: string) {
    const batch = allBatches.find((item) => item.id === batchId);
    if (!batch) return;
    setSelectedBatchId(batchId);
    setRunningBatchId(batchId);
    setError('');
    try {
      await runAiBatch(batchId);
      await refreshDatasetBatches(batch.datasetId);
    } catch (err) {
      setRunningBatchId('');
      setError(errorMessage(err, '批次运行失败'));
    }
  }

  if (loading) return <EmptyState>正在加载大模型标注数据...</EmptyState>;

  return (
    <div>
      <PageToolbar
        actions={
          <>
            <AppButton type="button" variant="secondary" onClick={() => void loadDatasets()}>刷新</AppButton>
            <AppButton type="button" variant="primary" disabled={eligibleDatasets.length === 0} onClick={openCreateDialog}>
              创建批次
            </AppButton>
          </>
        }
      >
        <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-center sm:gap-3">
          <span className="shrink-0">数据集：</span>
          <div className="min-w-0 sm:w-[200px]">
            <DatasetSelectMenu
              datasets={eligibleDatasets}
              value={selectedDatasetId}
              onChange={setSelectedDatasetId}
              ariaLabel="按数据集筛选批次"
              showCounts={false}
              includeAll
              batchCounts={batchCounts}
            />
          </div>
        </div>
      </PageToolbar>

      {error && <AppAlert kind="error" className="mb-4">{error}</AppAlert>}
      {notice && <AppAlert kind="success" className="mb-4">{notice}</AppAlert>}
      {eligibleDatasets.length === 0 ? (
        <EmptyState align="center" spacious>没有可发起大模型标注的数据集</EmptyState>
      ) : (
        <div className="grid min-h-[620px] gap-5 xl:grid-cols-[340px_minmax(0,1fr)]">
          <section className="min-w-0 border-b border-gray-200 pb-5 xl:border-b-0 xl:border-r xl:pb-0 xl:pr-5">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-gray-900">标注批次</h2>
              <span className="text-xs text-gray-500">{batches.length} 个</span>
            </div>
            {batches.length === 0 ? <EmptyState align="center">暂无批次</EmptyState> : (
              <div className="max-h-[560px] space-y-2 overflow-y-auto overscroll-contain pr-1 xl:max-h-[calc(100vh-220px)]">
                {batches.map((batch) => {
                  const selected = batch.id === selectedBatchId;
                  const percent = batch.totalCount ? Math.round(batch.processedCount / batch.totalCount * 100) : 0;
                  return (
                    <div
                      key={batch.id}
                      className={`overflow-hidden rounded border ${selected ? 'border-gray-900 bg-gray-50' : 'border-gray-200 bg-white'}`}
                    >
                      <AppButton
                        type="button"
                        variant="custom"
                        className="w-full p-3 text-left hover:bg-gray-50"
                        onClick={() => setSelectedBatchId(batch.id)}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span
                            className="min-w-0 truncate text-sm font-medium text-gray-900"
                            title={`${batch.datasetName} - ${modelDisplayName(batch.modelName)}`}
                          >
                            {batch.datasetName} - {modelDisplayName(batch.modelName)}
                          </span>
                          <StatusBadge status={batch.status}>{batchStatusLabels[batch.status] || batch.status}</StatusBadge>
                        </div>
                        <div className="mt-1 text-xs text-gray-500">{formatDateTime(batch.createdAt)}</div>
                        <div className="mt-3 h-1.5 overflow-hidden rounded bg-gray-200">
                          <div className="h-full bg-gray-800" style={{ width: `${percent}%` }} />
                        </div>
                        <div className="mt-2 flex justify-between text-xs text-gray-500">
                          <span>{batch.processedCount} / {batch.totalCount}</span><span>{percent}%</span>
                        </div>
                      </AppButton>
                      <div className="flex items-center gap-2 border-t border-gray-200 px-3 py-2">
                        <code className="min-w-0 flex-1 select-all overflow-x-auto whitespace-nowrap font-mono text-[11px] text-gray-600">
                          {batch.id}
                        </code>
                        <button
                          type="button"
                          className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-sm text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-800 focus:outline-none focus:ring-2 focus:ring-gray-200 ${copiedBatchId === batch.id ? 'bg-gray-100 text-gray-900' : ''}`}
                          aria-label={copiedBatchId === batch.id ? '批次 UUID 已复制' : '复制批次 UUID'}
                          title={copiedBatchId === batch.id ? '已复制' : '复制批次 UUID'}
                          onClick={() => void copyBatchId(batch.id)}
                        >
                          {copiedBatchId === batch.id ? (
                            <span className="text-sm font-semibold leading-none" aria-hidden="true">✓</span>
                          ) : (
                            <span className="relative block h-3.5 w-3.5" aria-hidden="true">
                              <span className="absolute left-0 top-0 h-2.5 w-2.5 rounded-[1px] border border-current" />
                              <span className="absolute bottom-0 right-0 h-2.5 w-2.5 rounded-[1px] border border-current bg-white" />
                            </span>
                          )}
                        </button>
                        {batch.status === 'pending' && (
                          <AppButton
                            type="button"
                            variant="primary"
                            size="sm"
                            disabled={runningBatchId === batch.id}
                            onClick={() => setRunConfirmBatch(batch)}
                          >
                            {runningBatchId === batch.id ? '开始中...' : '开始'}
                          </AppButton>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>

          <section className="min-w-0">
            {!selectedBatch ? <EmptyState align="center" spacious>请选择一个批次</EmptyState> : (
              selectedBatch.status === 'pending' ? (
                <div className="flex min-h-[420px] items-center justify-center px-6 text-center xl:min-h-[620px]">
                  <div className="flex flex-col items-center">
                    <AppButton
                      type="button"
                      variant="custom"
                      className="group flex h-16 w-16 items-center justify-center rounded-full border border-gray-300 bg-white shadow-sm ring-8 ring-gray-50 transition-colors hover:border-gray-500 hover:bg-gray-50 focus:outline-none focus:ring-8 focus:ring-gray-100 disabled:cursor-not-allowed disabled:opacity-50"
                      aria-label="开始 AI 标注批次"
                      title="开始"
                      disabled={runningBatchId === selectedBatch.id}
                      onClick={() => setRunConfirmBatch(selectedBatch)}
                    >
                      <span
                        className="ml-1 block h-0 w-0 border-y-[9px] border-l-[14px] border-y-transparent border-l-gray-700 transition-colors group-hover:border-l-gray-900"
                        aria-hidden="true"
                      />
                    </AppButton>
                    <h2 className="mt-7 text-xl font-semibold text-gray-900">本批次还未开始</h2>
                    <div className="mt-3"><StatusBadge status="pending">等待执行</StatusBadge></div>
                  </div>
                </div>
              ) : <>
                <div className="grid grid-cols-2 gap-px overflow-hidden rounded border border-gray-200 bg-gray-200 sm:grid-cols-4">
                  <Metric label="总数" value={selectedBatch.totalCount} />
                  <Metric label="待审核" value={selectedBatch.needsReviewCount} />
                  <Metric label="失败" value={selectedBatch.failedCount} />
                  <Metric label="已采用" value={selectedBatch.acceptedCount} />
                </div>

                <div className="my-4 overflow-x-auto">
                  <SegmentedControl
                    value={resultView}
                    onChange={setResultView}
                    options={[
                      { value: 'mandatory', label: '必须审核', badge: reviewCounts.mandatory },
                      { value: 'sampling', label: '抽检', badge: reviewCounts.sampling },
                      { value: 'low-risk', label: '低风险', badge: reviewCounts['low-risk'] },
                      { value: 'failed', label: '失败' },
                      { value: 'accepted', label: '已采用' },
                    ]}
                  />
                </div>

                <PageToolbar actions={resultView === 'low-risk' ? (
                  <AppButton
                    type="button"
                    variant="primary"
                    disabled={selectedResultIds.length === 0 || reviewing}
                    onClick={() => void handleBatchAccept()}
                  >
                    批量接受（{selectedResultIds.length}）
                  </AppButton>
                ) : undefined}>共 {resultTotal} 条</PageToolbar>

                {resultsLoading ? <EmptyState>正在加载结果...</EmptyState> : results.length === 0 ? (
                  <EmptyState align="center" spacious>当前分类下没有结果</EmptyState>
                ) : (
                  <div className="overflow-x-auto">
                    <AppTable className="min-w-[760px]">
                      <AppTableHead><tr>
                        {resultView === 'low-risk' && <th className="w-12 px-4 py-3" />}
                        <th className="px-4 py-3">数据内容</th>
                        <th className="px-4 py-3">AI 结果</th>
                        <th className="px-4 py-3">置信度</th>
                        <th className="px-4 py-3">状态</th>
                        <th className="px-4 py-3 text-right">操作</th>
                      </tr></AppTableHead>
                      <AppTableBody>{results.map((result) => (
                        <AppTableRow key={result.id}>
                          {resultView === 'low-risk' && <td className="px-4 py-3">
                            <input type="checkbox" checked={selectedResultIds.includes(result.id)} onChange={() => toggleResult(result.id)} />
                          </td>}
                          <td className="max-w-72 px-4 py-3"><div className="line-clamp-2 text-gray-800">{result.content}</div></td>
                          <td className="px-4 py-3"><AnnotationResultViewer result={result.result ? JSON.stringify(result.result) : null} schema={annotationSchema} /></td>
                          <td className="px-4 py-3 text-gray-600">{result.confidence || '-'} {result.confidenceScore || ''}</td>
                          <td className="px-4 py-3"><StatusBadge status={result.status}>{resultStatusLabels[result.status] || result.status}</StatusBadge></td>
                          <td className="px-4 py-3 text-right"><AppButton type="button" size="sm" onClick={() => openReview(result)}>查看</AppButton></td>
                        </AppTableRow>
                      ))}</AppTableBody>
                    </AppTable>
                  </div>
                )}
              </>
            )}
          </section>
        </div>
      )}

      {createDatasetOpen && (
        <AppModal
          title="选择批次数据集"
          width="md"
          onClose={() => setCreateDatasetOpen(false)}
          contentClassName="px-6 py-5"
          footer={<>
            <AppButton type="button" onClick={() => setCreateDatasetOpen(false)}>取消</AppButton>
            <AppButton
              type="button"
              variant="primary"
              disabled={!createDataset || remainingItems(createDataset) < 1}
              onClick={continueCreateDialog}
            >
              下一步
            </AppButton>
          </>}
        >
          <DatasetSelectMenu
            datasets={eligibleDatasets}
            value={createDatasetId}
            onChange={setCreateDatasetId}
            ariaLabel="选择创建批次的数据集"
            placeholder="请选择要处理的数据集"
            disableEmpty
            inlineMenu
          />
        </AppModal>
      )}

      {runConfirmBatch && (
        <AppModal
          title="开始 AI 标注批次"
          subtitle={`${runConfirmBatch.datasetName} - ${modelDisplayName(runConfirmBatch.modelName)}`}
          width="sm"
          onClose={() => setRunConfirmBatch(null)}
          contentClassName="px-6 py-5"
          footer={<>
            <AppButton type="button" onClick={() => setRunConfirmBatch(null)}>取消</AppButton>
            <AppButton
              type="button"
              variant="primary"
              disabled={runningBatchId === runConfirmBatch.id}
              onClick={() => {
                const batchId = runConfirmBatch.id;
                setRunConfirmBatch(null);
                void handleRunBatch(batchId);
              }}
            >
              确认开始
            </AppButton>
          </>}
        >
          <p className="text-sm leading-6 text-gray-700">
            开始后，AI Worker 将领取并处理本批次中的数据。确认开始该批次吗？
          </p>
        </AppModal>
      )}

      {createOpen && createDataset && (
        <form onSubmit={handleCreateBatch}>
          <AppModal
            title="配置大模型标注批次"
            subtitle={`${createDataset.name} · 剩余 ${remainingItems(createDataset)} 条`}
            width="lg"
            onClose={() => setCreateOpen(false)}
            contentClassName="max-h-[calc(100vh-220px)] overflow-y-auto px-6 py-5"
            footer={<>
              <AppButton type="button" onClick={() => setCreateOpen(false)}>取消</AppButton>
              <AppButton type="button" onClick={() => {
                setCreateOpen(false);
                setCreateDatasetOpen(true);
              }}>返回选择</AppButton>
              <AppButton type="submit" variant="primary" disabled={saving}>{saving ? '创建中...' : '创建批次'}</AppButton>
            </>}
          >
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="处理数量"><input className="app-input" type="number" min={1} max={Math.min(5000, remainingItems(createDataset))} value={createForm.maxItems} onChange={(e) => setCreateForm({ ...createForm, maxItems: Number(e.target.value) })} /></Field>
              <Field label="模型"><select className="app-input" value={createForm.modelName} onChange={(e) => setCreateForm({ ...createForm, modelName: e.target.value as CreateBatchInput['modelName'] })}><option value="deepseek-v4-flash">DeepSeek V4 Flash</option><option value="deepseek-v4-pro">DeepSeek V4 Pro</option></select></Field>
              <Field label="置信度阈值"><input className="app-input" type="number" min={0} max={1} step={0.01} value={createForm.confidenceThreshold} onChange={(e) => setCreateForm({ ...createForm, confidenceThreshold: Number(e.target.value) })} /></Field>
              <Field label="抽检比例"><input className="app-input" type="number" min={0} max={1} step={0.01} value={createForm.samplingRatio} onChange={(e) => setCreateForm({ ...createForm, samplingRatio: Number(e.target.value) })} /></Field>
            </div>
            <div className="mt-5">
              <div className="app-label">高风险选项</div>
              <div className="grid gap-2 sm:grid-cols-2">
                {createAnnotationSchema?.options?.map((option) => (
                  <label key={option.value} className="flex items-center gap-2 rounded border border-gray-200 px-3 py-2 text-sm">
                    <input
                      type="checkbox"
                      checked={createForm.highRiskOptionValues.includes(option.value)}
                      onChange={() => setCreateForm((form) => ({
                        ...form,
                        highRiskOptionValues: form.highRiskOptionValues.includes(option.value)
                          ? form.highRiskOptionValues.filter((value) => value !== option.value)
                          : [...form.highRiskOptionValues, option.value],
                      }))}
                    />
                    {option.label}
                  </label>
                ))}
              </div>
            </div>
          </AppModal>
        </form>
      )}

      {reviewResult && (
        <AppModal
          title="AI 标注结果"
          subtitle={`置信度 ${reviewResult.confidence || '-'} ${reviewResult.confidenceScore || ''}`}
          actions={<>
            <span className="min-w-14 text-center text-xs text-gray-500">
              {reviewResultIndex >= 0 ? `${reviewResultIndex + 1} / ${results.length}` : `- / ${results.length}`}
            </span>
            <AppButton
              type="button"
              variant="custom"
              className="flex h-8 w-8 items-center justify-center rounded border border-gray-300 bg-white text-base text-gray-600 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              aria-label="上一条 AI 标注结果"
              title="上一条"
              disabled={reviewResultIndex <= 0 || reviewing || modifyMode}
              onClick={() => moveReviewResult(-1)}
            >
              ←
            </AppButton>
            <AppButton
              type="button"
              variant="custom"
              className="flex h-8 w-8 items-center justify-center rounded border border-gray-300 bg-white text-base text-gray-600 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
              aria-label="下一条 AI 标注结果"
              title="下一条"
              disabled={reviewResultIndex < 0 || reviewResultIndex >= results.length - 1 || reviewing || modifyMode}
              onClick={() => moveReviewResult(1)}
            >
              →
            </AppButton>
          </>}
          width="xl"
          onClose={() => setReviewResult(null)}
          contentClassName="max-h-[calc(100vh-240px)] overflow-y-auto px-6 py-5"
          footer={<>
            {supportsAutoAdvance && (
              <label className="mr-auto flex cursor-pointer items-center gap-2 text-sm text-gray-600">
                <input
                  type="checkbox"
                  checked={autoAdvance}
                  disabled={reviewing}
                  onChange={(event) => handleAutoAdvanceChange(event.target.checked)}
                />
                处理后自动下一条
              </label>
            )}
            <AppButton type="button" disabled={reviewing} onClick={() => setReviewResult(null)}>关闭</AppButton>
            {canReviewResult && reviewResult.status !== 'failed' && !modifyMode && <AppButton type="button" variant="primary" disabled={reviewing} onClick={() => void handleReview('accept')}>接受</AppButton>}
            {canReviewResult && reviewResult.status !== 'failed' && <AppButton type="button" disabled={reviewing} onClick={() => modifyMode ? void handleReview('modify_accept') : setModifyMode(true)}>{modifyMode ? '保存修改并接受' : '修改后接受'}</AppButton>}
            {canReviewResult && <AppButton type="button" variant="danger" disabled={reviewing} onClick={() => void handleReview('reject_to_human')}>转人工</AppButton>}
            {canReviewResult && <AppButton type="button" disabled={reviewing} onClick={() => void handleReview('reject_retry')}>重新标注</AppButton>}
          </>}
        >
          <div className="space-y-5">
            <DataItemViewer item={{ id: reviewResult.itemId, datasetId: reviewResult.datasetId, content: reviewResult.content, contentType: reviewResult.contentType, metadata: JSON.stringify(reviewResult.metadata) }} />
            {modifyMode ? <AnnotationEditor schema={annotationSchema} selection={modifiedSelection} onChange={setModifiedSelection} /> : (
              <div className="rounded border border-gray-200 bg-gray-50 p-4">
                <div className="app-label">标注结果</div>
                <AnnotationResultViewer result={reviewResult.result ? JSON.stringify(reviewResult.result) : null} schema={annotationSchema} />
              </div>
            )}
            <div><div className="app-label">判断理由</div><div className="text-sm leading-6 text-gray-700">{reviewResult.reason || reviewResult.errorMessage || '无'}</div></div>
            <Field label="审核意见"><textarea className="app-input min-h-20 resize-y" value={reviewComment} onChange={(event) => setReviewComment(event.target.value)} /></Field>
          </div>
        </AppModal>
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return <div className="bg-white px-4 py-3"><div className="text-xs text-gray-500">{label}</div><div className="mt-1 text-xl font-semibold text-gray-900">{value}</div></div>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="app-label">{label}</span>{children}</label>;
}

function DatasetSelectMenu({
  datasets,
  value,
  onChange,
  ariaLabel,
  placeholder = '请选择数据集',
  disableEmpty = false,
  inlineMenu = false,
  showCounts = true,
  includeAll = false,
  batchCounts,
}: {
  datasets: ProviderDataset[];
  value: string;
  onChange: (datasetId: string) => void;
  ariaLabel: string;
  placeholder?: string;
  disableEmpty?: boolean;
  inlineMenu?: boolean;
  showCounts?: boolean;
  includeAll?: boolean;
  batchCounts?: Record<string, number>;
}) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const selectedDataset = datasets.find((dataset) => dataset.id === value) ?? null;
  const totalBatchCount = Object.values(batchCounts ?? {}).reduce((total, count) => total + count, 0);

  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: MouseEvent) {
      if (!menuRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  return (
    <div ref={menuRef} className="relative min-w-0">
      <button
        type="button"
        className="flex min-h-11 w-full items-center justify-between gap-3 rounded border border-gray-300 bg-white px-3 py-2 text-left text-sm text-gray-900 shadow-sm transition-colors hover:border-gray-400 focus:border-gray-500 focus:outline-none focus:ring-2 focus:ring-gray-200"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        {selectedDataset ? (
          <>
            <span className="min-w-0 truncate font-medium">{selectedDataset.name}</span>
            {batchCounts ? (
              <span className="shrink-0 text-xs text-gray-500">{batchCounts[selectedDataset.id] ?? 0} 个批次</span>
            ) : showCounts && <span className="shrink-0 text-xs text-gray-500">剩余 {remainingItems(selectedDataset)} 条</span>}
          </>
        ) : includeAll ? (
          <>
            <span className="font-medium text-gray-900">全部</span>
            <span className="shrink-0 text-xs text-gray-500">{totalBatchCount} 个批次</span>
          </>
        ) : <span className="text-gray-500">{placeholder}</span>}
        <span aria-hidden="true" className={`shrink-0 text-gray-500 transition-transform ${open ? 'rotate-180' : ''}`}>▾</span>
      </button>

      {open && (
        <div
          role="listbox"
          aria-label={ariaLabel}
          className={`${inlineMenu ? 'relative mt-2' : 'absolute left-0 right-0 top-full z-40 mt-1'} max-h-80 overflow-y-auto rounded border border-gray-300 bg-white py-1 shadow-lg`}
        >
          {includeAll && (
            <AppButton
              type="button"
              variant="custom"
              role="option"
              aria-selected={!value}
              className={`flex w-full items-center justify-between gap-4 px-3 py-2.5 text-left ${!value ? 'bg-gray-100 hover:bg-gray-100' : 'hover:bg-gray-50'}`}
              onClick={() => {
                onChange('');
                setOpen(false);
              }}
            >
              <span className="text-sm font-medium text-gray-900">全部</span>
              <span className="flex shrink-0 items-center gap-2">
                <span className="text-sm font-medium text-gray-700">{totalBatchCount} 个批次</span>
                <span className="w-4 text-center text-gray-900" aria-hidden="true">{!value ? '✓' : ''}</span>
              </span>
            </AppButton>
          )}
          {datasets.length === 0 ? (
            <div className="px-3 py-4 text-center text-sm text-gray-500">没有可用数据集</div>
          ) : datasets.map((dataset) => {
            const selected = dataset.id === value;
            const remaining = remainingItems(dataset);
            const disabled = disableEmpty && remaining < 1;
            return (
              <button
                key={dataset.id}
                type="button"
                role="option"
                aria-selected={selected}
                disabled={disabled}
                className={`flex w-full items-center justify-between gap-4 px-3 py-2.5 text-left ${
                  disabled ? 'cursor-not-allowed bg-gray-50 text-gray-400' : selected ? 'bg-gray-100 hover:bg-gray-100' : 'hover:bg-gray-50'
                }`}
                onClick={() => {
                  onChange(dataset.id);
                  setOpen(false);
                }}
              >
                <span className="min-w-0">
                  <span className={`block truncate text-sm font-medium ${disabled ? 'text-gray-400' : 'text-gray-900'}`}>{dataset.name}</span>
                  <span className="mt-0.5 block text-xs text-gray-500">
                    {datasetStatusLabels[dataset.status] || dataset.status}
                    {showCounts ? ` · 共 ${dataset.itemCount} 条` : ''}
                  </span>
                </span>
                <span className="flex shrink-0 items-center gap-2">
                  {batchCounts ? (
                    <span className={`text-sm font-medium ${disabled ? 'text-gray-400' : 'text-gray-700'}`}>
                      {batchCounts[dataset.id] ?? 0} 个批次
                    </span>
                  ) : showCounts && <span className={`text-sm font-medium ${disabled ? 'text-gray-400' : 'text-gray-700'}`}>剩余 {remaining} 条</span>}
                  <span className="w-4 text-center text-gray-900" aria-hidden="true">{selected ? '✓' : ''}</span>
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function parseSchema(raw: string | undefined): AnnotationSchema | null {
  if (!raw) return null;
  try { return JSON.parse(raw) as AnnotationSchema; } catch { return null; }
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

function remainingItems(dataset: ProviderDataset) {
  return Math.max(0, dataset.pendingItemCount ?? dataset.itemCount - dataset.completedItemCount);
}

function sortBatches(batches: AiBatch[]) {
  return [...batches].sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt));
}

function modelDisplayName(modelName: string) {
  if (modelName === 'deepseek-v4-flash') return 'DeepSeek V4 Flash';
  if (modelName === 'deepseek-v4-pro') return 'DeepSeek V4 Pro';
  return modelName;
}

function formatDateTime(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
