const apiBaseUrl = 'http://localhost:7000';
const form = document.getElementById('hello-form');
const nameInput = document.getElementById('name');
const statusNode = document.getElementById('status');
const resultNode = document.getElementById('result');

function setStatus(message, kind) {
  statusNode.textContent = message;
  statusNode.dataset.kind = kind;
}

function setResult(message) {
  resultNode.textContent = message;
}

async function fetchGreeting() {
  const name = nameInput.value.trim() || 'world';
  setStatus('正在请求 API...', 'loading');
  setResult('');

  try {
    const response = await fetch(`${apiBaseUrl}/api/hello?name=${encodeURIComponent(name)}`);
    if (!response.ok) {
      throw new Error(`API 响应异常：${response.status}`);
    }

    const data = await response.json();
    setStatus('API 已连接', 'success');
    setResult(data.message || '没有返回消息');
  } catch (error) {
    setStatus('API 暂时不可用', 'error');
    setResult(error instanceof Error ? error.message : String(error));
  }
}

form.addEventListener('submit', (event) => {
  event.preventDefault();
  fetchGreeting();
});

fetchGreeting();

