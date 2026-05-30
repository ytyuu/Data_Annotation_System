import { FormEvent, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';

const roleLabels: Record<string, string> = {
  provider: '数据集提供者',
  annotator: '数据标注员',
};

export function Login() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const role = searchParams.get('role') || '';

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (!role || !roleLabels[role]) {
    return (
      <div className="app-page app-center">
        <div className="text-center">
          <p className="text-gray-500">请先选择用户身份</p>
          <Link to="/" className="app-link-primary mt-4 inline-block">
            返回选择身份
          </Link>
        </div>
      </div>
    );
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');

    if (!username.trim() || !password.trim()) {
      setError('请输入用户名和密码');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch('http://localhost:7000/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, role }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new Error(data?.message || `登录失败 (${res.status})`);
      }

      const data = await res.json();
      localStorage.setItem('token', data.token);
      navigate('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : '登录失败，请重试');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="app-page app-center">
      <div className="app-stack app-stack-md">
        <div className="app-heading">
          <h1 className="app-title">数据标注系统</h1>
          <p className="app-subtitle">
            以 <span className="font-medium text-gray-700">{roleLabels[role]}</span> 身份登录
          </p>
        </div>

        <form onSubmit={handleSubmit} className="app-card max-w-md mx-auto">
          {error && <div className="app-alert-error">{error}</div>}

          <div className="app-field">
            <label htmlFor="username" className="app-label">
              用户名
            </label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="app-input"
              placeholder="请输入用户名"
              autoComplete="username"
            />
          </div>

          <div className="mb-6">
            <label htmlFor="password" className="app-label">
              密码
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="app-input"
              placeholder="请输入密码"
              autoComplete="current-password"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="app-button-primary"
          >
            {loading ? '登录中...' : '登 录'}
          </button>

          <div className="mt-6 text-center">
            <Link to={`/register?role=${role}`} className="app-link-primary text-sm">
              注册账号
            </Link>
          </div>

          <div className="mt-3 text-center">
            <Link to="/" className="app-link">
              ← 返回选择身份
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}
