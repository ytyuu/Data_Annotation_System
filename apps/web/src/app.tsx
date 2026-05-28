import { FormEvent, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';

const apiBaseUrl = 'http://localhost:7000';

type StatusKind = 'loading' | 'success' | 'error';

interface GreetingResponse {
  message: string;
}

function App() {
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
    <main className="mx-auto w-[min(960px,calc(100vw-32px))] py-16">
      <section className="mb-6">
        <p className="mb-2 text-sm font-bold uppercase tracking-[0.12em] text-indigo-600">Turbo Monorepo Demo</p>
        <h1 className="m-0 text-4xl font-bold leading-tight sm:text-5xl lg:text-6xl">前后端放在一个仓库里统一管理</h1>
        <p className="mt-4 max-w-2xl text-base leading-7 text-slate-600 sm:text-lg">
          这个页面会直接请求本地 API 服务，方便你在同一个仓库里同时开发前端和后端。
        </p>
      </section>

      <section className="rounded-2xl border border-slate-200/80 bg-white/90 p-6 shadow-[0_18px_60px_rgba(15,23,42,0.08)]">
        <form id="hello-form" className="grid gap-3" onSubmit={handleSubmit}>
          <label htmlFor="name" className="font-semibold">
            输入一个名字
          </label>
          <div className="flex flex-wrap gap-3">
            <input
              id="name"
              name="name"
              type="text"
              value={name}
              autoComplete="off"
              className="min-w-0 flex-1 basis-64 rounded-xl border border-slate-300 px-4 py-3.5 text-base outline-none transition focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100"
              onChange={(event) => setName(event.target.value)}
            />
            <button
              type="submit"
              className="rounded-xl bg-indigo-600 px-5 py-3.5 font-bold text-white transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200"
            >
              问候一下
            </button>
          </div>
        </form>

        <div
          className="mt-5 text-sm text-slate-600 data-[kind=error]:text-red-700 data-[kind=loading]:text-amber-700 data-[kind=success]:text-green-700"
          data-kind={statusKind}
        >
          {status}
        </div>
        <div className="mt-2 text-lg font-semibold">{result}</div>
      </section>
    </main>
  );
}

const rootNode = document.getElementById('root');

if (!rootNode) {
  throw new Error('Missing #root element');
}

createRoot(rootNode).render(<App />);
