import { useEffect, useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AppAlert } from '../../components/shared/AppAlert';
import { AppButton } from '../../components/shared/AppButton';

const apiBaseUrl = 'http://localhost:7000';

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

type RoleMenuItem = {
  key: string;
  label: string;
  path: string;
};

interface RoleLayoutProps {
  role: 'provider' | 'annotator';
  menus: RoleMenuItem[];
  workbenchPath: string;
  redirectOnRoleMismatch: string;
}

export function RoleLayout({ role, menus, workbenchPath, redirectOnRoleMismatch }: RoleLayoutProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [state, setState] = useState<LoadState>('loading');
  const [error, setError] = useState('');

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
    if (state !== 'ready' || !user) {
      return;
    }

    if (user.role !== role) {
      navigate(redirectOnRoleMismatch, { replace: true });
    }
  }, [navigate, redirectOnRoleMismatch, role, state, user]);

  function handleLogout() {
    localStorage.removeItem('token');
    navigate('/', { replace: true });
  }

  const activeMenu = useMemo(() => {
    return menus.find((menu) => location.pathname.startsWith(menu.path));
  }, [location.pathname, menus]);

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
          <AppAlert kind="error" className="mb-6 text-left">{error}</AppAlert>
          <Link to="/" className="app-link-primary text-sm">
            返回选择身份
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100 text-gray-900">
      <header className="sticky top-0 z-20 flex min-h-14 flex-wrap items-center justify-between gap-3 border-b border-gray-300 bg-white px-3 py-2 shadow-sm lg:h-14 lg:flex-nowrap lg:px-6 lg:py-0">
        <div className="flex min-w-0 flex-wrap items-center gap-3 lg:gap-6">
          <div className="flex items-center gap-2 text-base font-semibold text-gray-900">
            <span className="h-3 w-3 rounded-sm bg-gray-900" />
            数据标注系统
          </div>
          <nav className="hidden items-center gap-4 text-sm text-gray-500 sm:flex">
            <Link to={workbenchPath} className="font-medium text-gray-900">
              工作台
            </Link>
            <Link to="/api-test" className="hover:text-gray-700">
              API 测试
            </Link>
          </nav>
        </div>

        <div className="flex items-center gap-3 lg:gap-4">
          <div className="hidden text-right sm:block">
            <div className="text-sm font-medium text-gray-900">{user?.displayName}</div>
            <div className="text-xs text-gray-500">{roleLabels[user?.role || ''] || user?.role}</div>
          </div>
          <AppButton type="button" variant="custom" className="app-link" onClick={handleLogout}>
            退出
          </AppButton>
        </div>
      </header>

      <div className="flex min-h-[calc(100vh-56px)] flex-col lg:flex-row">
        <aside className="w-full border-b border-gray-300 bg-gray-50 px-3 py-2 shadow-sm lg:w-52 lg:border-b-0 lg:border-r lg:px-3 lg:py-4">
          <div className="mb-5 hidden border-b border-gray-200 px-2 pb-3 text-xs font-medium text-gray-500 lg:block">
            <span>功能菜单</span>
          </div>
          <nav className="flex gap-2 overflow-x-auto lg:block lg:space-y-1.5">
            {menus.map((item) => (
              <Link
                key={item.key}
                to={item.path}
                className={`relative block shrink-0 whitespace-nowrap rounded border px-3 py-2 text-left text-sm font-medium transition-colors ${
                  activeMenu?.key === item.key
                    ? 'border-gray-300 bg-white text-gray-950 shadow-sm'
                    : 'border-transparent text-gray-600 hover:border-gray-300 hover:bg-white hover:text-gray-900'
                }`}
              >
                {activeMenu?.key === item.key && (
                  <span className="absolute bottom-2 left-0 top-2 w-1 rounded-r bg-gray-900" />
                )}
                {item.label}
              </Link>
            ))}
          </nav>
        </aside>

        <main className="min-w-0 flex-1 p-2 sm:p-3 lg:p-3">
          <section className="min-h-full overflow-hidden rounded border border-gray-300 bg-white shadow-sm">
            <div className="border-b border-gray-300 bg-gray-50 px-4 py-3 sm:px-5 sm:py-4">
              <div>
                <div className="text-xs font-medium text-gray-500">工作台</div>
                <h1 className="mt-1 text-xl font-semibold text-gray-900">
                  {activeMenu?.label || '业务页面'}
                </h1>
              </div>
            </div>

            <div className="p-3 sm:p-4 lg:p-4">
              <Outlet />
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}
