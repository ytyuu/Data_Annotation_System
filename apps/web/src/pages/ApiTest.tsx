import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AppButton } from '../components/shared/AppButton';

const apiBaseUrl = 'http://localhost:7000';

type StatusKind = 'loading' | 'success' | 'error';

interface GreetingResponse {
  message: string;
}

interface DatabaseHealthResponse {
  status: string;
  database: string;
  latencyMs: number;
  message?: string;
}

function Header() {
  return (
    <header className="app-heading">
      <h1 className="app-title">数据标注系统</h1>
      <p className="app-subtitle">
        这个页面会请求本地 API 服务，方便你在同一个仓库里同时开发前端和后端。
      </p>
    </header>
  );
}

export function ApiTest() {
  const [name, setName] = useState('Turbo');
  const [status, setStatus] = useState('等待连接 API...');
  const [statusKind, setStatusKind] = useState<StatusKind>('loading');
  const [result, setResult] = useState('点击按钮后这里会显示后端返回的消息。');
  const [databaseStatus, setDatabaseStatus] = useState('等待测试数据库连接...');
  const [databaseStatusKind, setDatabaseStatusKind] = useState<StatusKind>('loading');
  const [databaseResult, setDatabaseResult] = useState('点击按钮后这里会显示数据库健康检查结果。');
  const [databaseLoading, setDatabaseLoading] = useState(false);

  async function fetchGreeting(nextName = name): Promise<void> {
    const requestName = nextName.trim() || 'world';
    setStatus('正在请求 API...');
    setStatusKind('loading');
    setResult('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/hello?name=${encodeURIComponent(requestName)}`);
      if (!response.ok) {
        throw new Error(`API 响应异常：${response.status}`);
      }

      const data = (await response.json()) as GreetingResponse;
      setStatus('API 已连接');
      setStatusKind('success');
      setResult(data.message || '没有返回消息');
    } catch (error) {
      setStatus('API 暂时不可用');
      setStatusKind('error');
      setResult(error instanceof Error ? error.message : String(error));
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    fetchGreeting();
  }

  async function fetchDatabaseHealth(): Promise<void> {
    setDatabaseLoading(true);
    setDatabaseStatus('正在请求数据库健康接口...');
    setDatabaseStatusKind('loading');
    setDatabaseResult('');

    try {
      const response = await fetch(`${apiBaseUrl}/api/health/database`);
      const data = (await response.json().catch(() => null)) as DatabaseHealthResponse | null;

      if (!response.ok) {
        throw new Error(data?.message || `数据库健康检查失败：${response.status}`);
      }

      setDatabaseStatus('数据库连接正常');
      setDatabaseStatusKind('success');
      setDatabaseResult(`${data?.database || 'postgresql'} 响应正常，耗时 ${data?.latencyMs ?? 0} ms`);
    } catch (error) {
      setDatabaseStatus('数据库暂时不可用');
      setDatabaseStatusKind('error');
      setDatabaseResult(error instanceof Error ? error.message : String(error));
    } finally {
      setDatabaseLoading(false);
    }
  }

  useEffect(() => {
    fetchGreeting('Turbo');
  }, []);

  return (
    <div className="app-page app-center">
      <div className="app-stack app-stack-md">
        <Header />

        <main className="app-card max-w-md mx-auto">
          <form id="hello-form" onSubmit={handleSubmit}>
            <div className="mb-6">
              <label htmlFor="name" className="app-label">
                输入一个名字
              </label>
              <input
                id="name"
                name="name"
                type="text"
                value={name}
                autoComplete="off"
                className="app-input"
                onChange={(event) => setName(event.target.value)}
              />
            </div>

            <AppButton type="submit" variant="primary" fullWidth>
              问候一下
            </AppButton>
          </form>

          <div className="app-status" data-kind={statusKind}>
            {status}
          </div>
          <div className="app-result">{result}</div>

          <div className="my-8 border-t border-gray-200" />

          <AppButton
            type="button"
            variant="primary"
            fullWidth
            disabled={databaseLoading}
            onClick={fetchDatabaseHealth}
          >
            {databaseLoading ? '测试中...' : '测试数据库'}
          </AppButton>

          <div className="app-status" data-kind={databaseStatusKind}>
            {databaseStatus}
          </div>
          <div className="app-result">{databaseResult}</div>

          <div className="mt-6 text-center">
            <Link to="/" className="app-link">
              ← 返回选择身份
            </Link>
          </div>
        </main>
      </div>
    </div>
  );
}
