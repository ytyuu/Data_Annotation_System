import { FormEvent, useEffect, useState } from 'react';

const apiBaseUrl = 'http://localhost:7000';

type StatusKind = 'loading' | 'success' | 'error';

interface GreetingResponse {
  message: string;
}

function Header() {
  return (
    <header className="das-header">
      <p className="das-label">Data Annotation System</p>
      <h1 className="das-title">前后端放在一个仓库里统一管理</h1>
      <p className="das-desc">
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

  useEffect(() => {
    fetchGreeting('Turbo');
  }, []);

  return (
    <div className="das-page">
      <Header />

      <main className="das-body">
        <form id="hello-form" className="das-form" onSubmit={handleSubmit}>
          <label htmlFor="name" className="das-input-label">
            输入一个名字
          </label>
          <input
            id="name"
            name="name"
            type="text"
            value={name}
            autoComplete="off"
            className="das-input"
            onChange={(event) => setName(event.target.value)}
          />
          <button type="submit" className="das-btn">
            问候一下
          </button>
        </form>

        <div className="das-status" data-kind={statusKind}>
          {status}
        </div>
        <div className="das-result">{result}</div>
      </main>

      <footer className="das-footer">Data Annotation System</footer>
    </div>
  );
}
