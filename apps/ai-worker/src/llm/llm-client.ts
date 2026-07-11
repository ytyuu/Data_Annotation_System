import OpenAI from 'openai';

export interface LlmCompletion {
  output: unknown;
  promptTokens: number;
  completionTokens: number;
}

export interface LlmClientConfig {
  baseUrl: string;
  apiKey: string;
  temperature: number;
  maxTokens: number;
  timeoutMs: number;
  thinkingEnabled: boolean;
}

export class LlmClient {
  private readonly client: OpenAI;

  constructor(private readonly config: LlmClientConfig) {
    this.client = new OpenAI({ apiKey: config.apiKey, baseURL: config.baseUrl, timeout: config.timeoutMs });
  }

  async complete(model: string, systemPrompt: string, userPrompt: string): Promise<LlmCompletion> {
    const body = {
      model,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userPrompt },
      ],
      response_format: { type: 'json_object' },
      stream: false,
      temperature: this.config.temperature,
      max_tokens: this.config.maxTokens,
      thinking: { type: this.config.thinkingEnabled ? 'enabled' : 'disabled' },
    };
    const response = await this.client.chat.completions.create(body as never);
    const choice = response.choices[0];
    if (!choice) throw new LlmResponseError('模型未返回 choice');
    if (choice.finish_reason === 'length') throw new LlmResponseError('模型输出因长度限制被截断');
    const content = choice.message.content?.trim();
    if (!content) throw new LlmResponseError('模型返回空内容');
    let output: unknown;
    try {
      output = JSON.parse(content);
    } catch {
      throw new LlmResponseError('模型返回内容不是合法 JSON');
    }
    return {
      output,
      promptTokens: response.usage?.prompt_tokens ?? 0,
      completionTokens: response.usage?.completion_tokens ?? 0,
    };
  }
}

export class LlmResponseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'LlmResponseError';
  }
}
