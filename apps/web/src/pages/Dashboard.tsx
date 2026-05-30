import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

const roleLabels: Record<string, string> = {
  provider: '数据集提供者',
  annotator: '数据标注员',
  admin: '管理员',
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

export function Dashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [state, setState] = useState<LoadState>('loading');
  const [error, setError] = useState('');
  const [activeMenu, setActiveMenu] = useState('');

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
        const response = await fetch('http://localhost:7000/api/me', {
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

  function handleLogout() {
    localStorage.removeItem('token');
    navigate('/', { replace: true });
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

            <div className="min-h-[520px]" />
          </section>
        </main>
      </div>
    </div>
  );
}
