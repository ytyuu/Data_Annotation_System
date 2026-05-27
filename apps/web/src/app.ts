const apiBaseUrl: string = 'http://localhost:7000';

const form = document.getElementById('hello-form') as HTMLFormElement;
const nameInput = document.getElementById('name') as HTMLInputElement;
const statusNode = document.getElementById('status') as HTMLDivElement;
const resultNode = document.getElementById('result') as HTMLDivElement;

function setStatus(message: string, kind: 'loading' | 'success' | 'error'): void {
  statusNode.textContent = message;
  statusNode.dataset.kind = kind;
}

function setResult(message: string): void {
  resultNode.textContent = message;
}

interface GreetingResponse {
  message: string;
}

async function fetchGreeting(): Promise<void> {
  const name = nameInput.value.trim() || 'world';
  setStatus('正在请求 API...', 'loading');
  setResult('');

  try {
    const response = await fetch(`${apiBaseUrl}/api/hello?name=${encodeURIComponent(name)}`);
    if (!response.ok) {
      throw new Error(`API 响应异常：${response.status}`);
    }

    const data = (await response.json()) as GreetingResponse;
    setStatus('API 已连接', 'success');
    setResult(data.message || '没有返回消息');
  } catch (error) {
    setStatus('API 暂时不可用', 'error');
    setResult(error instanceof Error ? error.message : String(error));
  }
}

form.addEventListener('submit', (event: Event) => {
  event.preventDefault();
  fetchGreeting();
});

fetchGreeting();
