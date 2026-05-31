import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

const apiBaseUrl = 'http://localhost:7000';

const roleLabels: Record<string, string> = {
  provider: '数据集提供者',
  annotator: '数据标注员',
  admin: '管理员',
};

const datasetStatusLabels: Record<string, string> = {
  draft: '草稿',
  open: '已开放',
  annotating: '标注中',
  reviewing: '审核中',
  revision_required: '需调整',
  completed: '已完成',
  closed: '已关闭',
};

interface CurrentUser {
  id: string;
  username: string;
  displayName: string;
  role: string;
  status: string;
}

type LoadState = 'loading' | 'ready' | 'error';

interface MenuItem {
  key: string;
  label: string;
}

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

interface DatasetForm {
  name: string;
  description: string;
  annotationGuide: string;
  selectionMode: 'single' | 'multiple';
  options: AnnotationOption[];
  targetCompletionRatio: string;
}

interface AnnotationOption {
  id: string;
  label: string;
}

const initialDatasetForm: DatasetForm = {
  name: '',
  description: '',
  annotationGuide: '',
  selectionMode: 'single',
  options: [
    { id: 'option-1', label: '' },
    { id: 'option-2', label: '' },
  ],
  targetCompletionRatio: '50',
};

export function Dashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [state, setState] = useState<LoadState>('loading');
  const [error, setError] = useState('');
  const [activeMenu, setActiveMenu] = useState('');
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(false);
  const [datasetsError, setDatasetsError] = useState('');
  const [datasetForm, setDatasetForm] = useState<DatasetForm>(initialDatasetForm);
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState('');

  const menus = useMemo<MenuItem[]>(() => {
    if (!user) {
      return [];
    }

    if (user.role === 'provider') {
      return [
        { key: 'datasets', label: '数据集管理' },
        { key: 'create-dataset', label: '创建数据集' },
        { key: 'reviews', label: '标注审核' },
        { key: 'disputes', label: '争议处理' },
      ];
    }

    if (user.role === 'annotator') {
      return [
        { key: 'open-datasets', label: '可标注数据集' },
        { key: 'my-tasks', label: '我的任务' },
        { key: 'returned-tasks', label: '退回任务' },
        { key: 'submissions', label: '提交记录' },
      ];
    }

    return [
      { key: 'overview', label: '系统概览' },
      { key: 'users', label: '账号管理' },
      { key: 'datasets', label: '数据集状态' },
      { key: 'tasks', label: '任务状态' },
    ];
  }, [user]);

  useEffect(() => {
    if (menus.length > 0 && !menus.some((item) => item.key === activeMenu)) {
      setActiveMenu(menus[0].key);
    }
  }, [activeMenu, menus]);

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    async function loadCurrentUser() {
      try {
        const response = await fetch(`${apiBaseUrl}/api/me`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        const data = await response.json().catch(() => null);

        if (!response.ok) {
          throw new Error(data?.message || `登录状态无效 (${response.status})`);
        }

        setUser(data as CurrentUser);
        setState('ready');
      } catch (err) {
        localStorage.removeItem('token');
        setError(err instanceof Error ? err.message : '登录状态无效，请重新登录');
        setState('error');
      }
    }

    loadCurrentUser();
  }, [navigate]);

  useEffect(() => {
    if (state !== 'ready' || user?.role !== 'provider') {
      return;
    }

    loadProviderDatasets();
  }, [state, user?.role]);

  function handleLogout() {
    localStorage.removeItem('token');
    navigate('/', { replace: true });
  }

  async function loadProviderDatasets() {
    const token = localStorage.getItem('token');
    if (!token) {
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
        { id: `option-${Date.now()}`, label: '' },
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

  function buildAnnotationSchema() {
    const normalizedOptions = datasetForm.options
      .map((option) => option.label.trim())
      .filter((label) => label.length > 0)
      .map((label, index) => ({
        value: `option_${index + 1}`,
        label,
      }));

    return {
      version: 1,
      type: 'classification',
      selectionMode: datasetForm.selectionMode,
      options: normalizedOptions,
    };
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

  async function handleCreateDataset(event: React.FormEvent<HTMLFormElement>) {
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

    const token = localStorage.getItem('token');
    if (!token) {
      navigate('/', { replace: true });
      return;
    }

    setCreateLoading(true);
    try {
      const response = await fetch(`${apiBaseUrl}/api/provider/datasets`, {
        method: 'POST',
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
      });
      const data = await response.json().catch(() => null);

      if (!response.ok) {
        throw new Error(data?.message || `创建失败 (${response.status})`);
      }

      setDatasetForm(initialDatasetForm);
      await loadProviderDatasets();
      setActiveMenu('datasets');
    } catch (err) {
      setCreateError(err instanceof Error ? err.message : '创建失败，请重试');
    } finally {
      setCreateLoading(false);
    }
  }

  function renderProviderContent() {
    if (activeMenu === 'create-dataset') {
      return (
        <form onSubmit={handleCreateDataset} className="max-w-3xl">
          {createError && <div className="app-alert-error">{createError}</div>}

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
            <div className="inline-flex rounded border border-gray-300 bg-white p-1">
              <button
                type="button"
                className={`rounded px-4 py-2 text-sm font-medium ${
                  datasetForm.selectionMode === 'single'
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-600 hover:bg-gray-50'
                }`}
                onClick={() => updateSelectionMode('single')}
              >
                单选
              </button>
              <button
                type="button"
                className={`rounded px-4 py-2 text-sm font-medium ${
                  datasetForm.selectionMode === 'multiple'
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-600 hover:bg-gray-50'
                }`}
                onClick={() => updateSelectionMode('multiple')}
              >
                多选
              </button>
            </div>
          </div>

          <div className="app-field">
            <div className="mb-2 flex items-center justify-between">
              <label className="app-label mb-0">标注选项</label>
              <button
                type="button"
                className="rounded border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
                onClick={addAnnotationOption}
              >
                新增选项
              </button>
            </div>
            <div className="space-y-2">
              {datasetForm.options.map((option, index) => (
                <div key={option.id} className="flex items-center gap-2">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded border border-gray-200 bg-gray-50 text-sm text-gray-500">
                    {index + 1}
                  </div>
                  <input
                    type="text"
                    value={option.label}
                    onChange={(event) => updateAnnotationOption(option.id, event.target.value)}
                    className="app-input"
                    placeholder="请输入选项名称"
                  />
                  <button
                    type="button"
                    disabled={datasetForm.options.length <= 2}
                    className="h-10 shrink-0 rounded border border-gray-300 px-3 text-sm font-medium text-gray-600 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
                    onClick={() => removeAnnotationOption(option.id)}
                  >
                    删除
                  </button>
                </div>
              ))}
            </div>
          </div>

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

          <div className="mt-8 flex gap-3">
            <button
              type="submit"
              disabled={createLoading}
              className="rounded bg-blue-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {createLoading ? '创建中...' : '创建数据集'}
            </button>
            <button
              type="button"
              className="rounded border border-gray-300 px-5 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
              onClick={() => setActiveMenu('datasets')}
            >
              返回列表
            </button>
          </div>
        </form>
      );
    }

    if (activeMenu === 'datasets') {
      return (
        <div>
          <div className="mb-4 flex items-center justify-between">
            <div className="text-sm text-gray-500">
              共 {datasets.length} 个数据集
            </div>
            <button
              type="button"
              className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
              onClick={() => setActiveMenu('create-dataset')}
            >
              创建数据集
            </button>
          </div>

          {datasetsError && <div className="app-alert-error">{datasetsError}</div>}

          {datasetsLoading ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
              正在加载数据集...
            </div>
          ) : datasets.length === 0 ? (
            <div className="rounded border border-gray-200 bg-gray-50 p-8 text-center">
              <div className="text-sm text-gray-500">还没有数据集</div>
              <button
                type="button"
                className="mt-4 rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
                onClick={() => setActiveMenu('create-dataset')}
              >
                创建第一个数据集
              </button>
            </div>
          ) : (
            <div className="overflow-hidden rounded border border-gray-200">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs font-medium text-gray-500">
                  <tr>
                    <th className="px-4 py-3">名称</th>
                    <th className="px-4 py-3">状态</th>
                    <th className="px-4 py-3">标注方式</th>
                    <th className="px-4 py-3">数据项</th>
                    <th className="px-4 py-3">审核阈值</th>
                    <th className="px-4 py-3">更新时间</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 bg-white">
                  {datasets.map((dataset) => (
                    <tr key={dataset.id}>
                      <td className="px-4 py-3">
                        <div className="font-medium text-gray-900">{dataset.name}</div>
                        {dataset.description && (
                          <div className="mt-1 line-clamp-1 text-xs text-gray-500">
                            {dataset.description}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <span className="rounded bg-gray-100 px-2 py-1 text-xs text-gray-700">
                          {datasetStatusLabels[dataset.status] || dataset.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {getDatasetSchemaSummary(dataset)}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {dataset.completedItemCount}/{dataset.itemCount}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {dataset.targetCompletionRatio}%
                      </td>
                      <td className="px-4 py-3 text-gray-500">
                        {new Date(dataset.updatedAt).toLocaleString()}
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

    return (
      <div className="rounded border border-gray-200 bg-gray-50 p-6 text-sm text-gray-500">
        当前功能会在数据集创建和导入数据项后继续接入。
      </div>
    );
  }

  function renderMainContent() {
    if (user?.role === 'provider') {
      return renderProviderContent();
    }

    return <div className="min-h-[520px]" />;
  }

  if (state === 'loading') {
    return (
      <div className="app-page app-center">
        <div className="app-card max-w-md text-center text-sm text-gray-500">
          正在加载用户信息...
        </div>
      </div>
    );
  }

  if (state === 'error') {
    return (
      <div className="app-page app-center">
        <div className="app-card max-w-md text-center">
          <div className="app-alert-error">{error}</div>
          <Link to="/" className="app-link-primary text-sm">
            返回选择身份
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900">
      <header className="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-6">
        <div className="flex items-center gap-6">
          <div className="text-base font-semibold text-gray-900">数据标注系统</div>
          <nav className="flex items-center gap-4 text-sm text-gray-500">
            <Link to="/dashboard" className="font-medium text-blue-600">
              工作台
            </Link>
            <Link to="/api-test" className="hover:text-gray-700">
              API 测试
            </Link>
          </nav>
        </div>

        <div className="flex items-center gap-4">
          <div className="text-right">
            <div className="text-sm font-medium text-gray-900">{user?.displayName}</div>
            <div className="text-xs text-gray-500">{roleLabels[user?.role || ''] || user?.role}</div>
          </div>
          <button type="button" className="app-link" onClick={handleLogout}>
            退出
          </button>
        </div>
      </header>

      <div className="flex min-h-[calc(100vh-56px)]">
        <aside className="w-56 border-r border-gray-200 bg-white px-4 py-5">
          <div className="mb-4 px-2 text-xs font-medium text-gray-400">功能菜单</div>
          <nav className="space-y-1">
            {menus.map((item) => (
              <button
                key={item.key}
                type="button"
                className={`w-full rounded px-3 py-2 text-left text-sm font-medium transition-colors ${
                  activeMenu === item.key
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                }`}
                onClick={() => setActiveMenu(item.key)}
              >
                {item.label}
              </button>
            ))}
          </nav>
        </aside>

        <main className="flex-1 p-6">
          <section className="min-h-full rounded border-2 border-gray-200 bg-white p-6">
            <div className="mb-4 border-b border-gray-200 pb-4">
              <h1 className="text-xl font-semibold text-gray-900">
                {menus.find((item) => item.key === activeMenu)?.label || '业务页面'}
              </h1>
            </div>

            {renderMainContent()}
          </section>
        </main>
      </div>
    </div>
  );
}
