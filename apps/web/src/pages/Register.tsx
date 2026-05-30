import { FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

const roleLabels: Record<string, string> = {
  provider: '数据集提供者',
  annotator: '数据标注员',
};

interface RegisterResponse {
  message: string;
  user: {
    username: string;
    role: string;
  };
}

export function Register() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const role = searchParams.get('role') || '';

  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState('');
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

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setMessage('');

    if (!username.trim() || !displayName.trim() || !password.trim()) {
      setError('请完整填写注册信息');
      return;
    }

    if (password !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }

    setLoading(true);
    try {
      const response = await fetch('http://localhost:7000/api/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username,
          displayName,
          password,
          role,
        }),
      });

      const data = (await response.json().catch(() => null)) as RegisterResponse | { message?: string } | null;

      if (!response.ok) {
        throw new Error(data?.message || `注册失败 (${response.status})`);
      }

      setMessage(data?.message || '注册成功');
      window.setTimeout(() => {
        navigate(`/login?role=${role}`);
      }, 800);
    } catch (err) {
      setError(err instanceof Error ? err.message : '注册失败，请重试');
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
            注册 <span className="font-medium text-gray-700">{roleLabels[role]}</span> 账号
          </p>
        </div>

        <form onSubmit={handleSubmit} className="app-card max-w-md mx-auto">
          {error && <div className="app-alert-error">{error}</div>}
          {message && (
            <div className="mb-6 rounded border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">
              {message}
            </div>
          )}

          <div className="app-field">
            <label htmlFor="username" className="app-label">
              用户名
            </label>
            <input
              id="username"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className="app-input"
              placeholder="请输入用户名"
              autoComplete="username"
            />
          </div>

          <div className="app-field">
            <label htmlFor="displayName" className="app-label">
              显示名称
            </label>
            <input
              id="displayName"
              type="text"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              className="app-input"
              placeholder="请输入显示名称"
              autoComplete="name"
            />
          </div>

          <div className="app-field">
            <label htmlFor="password" className="app-label">
              密码
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="app-input"
              placeholder="至少 6 个字符"
              autoComplete="new-password"
            />
          </div>

          <div className="mb-6">
            <label htmlFor="confirmPassword" className="app-label">
              确认密码
            </label>
            <input
              id="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              className="app-input"
              placeholder="请再次输入密码"
              autoComplete="new-password"
            />
          </div>

          <button type="submit" disabled={loading} className="app-button-primary">
            {loading ? '注册中...' : '注册'}
          </button>

          <div className="mt-6 text-center">
            <Link to={`/login?role=${role}`} className="app-link-primary text-sm">
              已有账号，去登录
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
