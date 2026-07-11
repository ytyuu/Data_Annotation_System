# 大模型标注 Worker 设计

本文档单独说明 `apps/ai-worker` 的定位、技术栈、运行方式、参数来源、Prompt 设计、JSON 质量保障和模块划分。

AI Worker 是大模型标注能力中的执行进程。它不管理业务状态，不直接访问数据库，也不暴露给前端调用。它通过后端提供的 AI 执行端接口领取数据、调用大模型、校验输出，并把结果上传回后端。

## 1. 定位与职责边界

建议在 monorepo 中新增独立应用：

```text
apps/
  api/
  web/
  ai-worker/
```

职责边界：

- `apps/api`：管理数据集、AI 批次、AI 结果、审核状态和数据库事务。
- `apps/web`：提供创建 AI 批次、查看进度、审核结果的页面。
- `apps/ai-worker`：负责领取数据、调用大模型、校验输出、上传结果。

AI Worker 不建议承担这些职责：

- 不直接访问数据库。
- 不直接修改 `data_items`、`ai_annotation_batches` 或 `ai_annotation_results`。
- 不暴露给前端调用的 HTTP 接口。
- 不决定最终业务状态流转。
- 不模拟人工标注员领取人工任务单。

推荐关系：

```text
web -> api：创建 AI 标注批次
api -> 数据库：记录 batchId、目标数据集和批次配置
ai-worker -> api：领取某个 batchId 的数据
ai-worker -> 大模型：请求标注
ai-worker -> api：上传结果
```

## 2. 技术栈建议

第一版建议使用 TypeScript / Node.js。

原因：

- 当前项目已经使用 pnpm workspace 和 Turbo，接入成本低。
- AI Worker 主要工作是调用接口、组装 Prompt、解析 JSON、校验结构和上传结果，TypeScript 很适合。
- 可以使用 `zod` 做模型输出校验。
- 可以使用 OpenAI SDK 或 OpenAI-compatible SDK 对接不同大模型服务。

第一版依赖建议保持简单：

```text
typescript
tsx
zod
dotenv
openai
```

Python 更适合后续做离线质量分析、样本聚类、Prompt 对比实验。Go 更适合后续高吞吐、长期运行的稳定 worker。第一版为了快速接入当前项目，优先选择 TypeScript。

## 3. 进程形态

第一版建议做成命令行启动的独立 worker 进程，不建议暴露 HTTP 接口。

目标数据集不直接传给 AI Worker。目标数据集应在创建 AI 批次时传给后端，由后端生成 `batchId`。AI Worker 只处理指定的 `batchId`。

第一版启动方式示例：

```text
pnpm --filter ai-worker dev -- --batch-id xxx --chunk-size 100 --model-batch-size 10 --concurrency 2
```

后续可以扩展常驻模式：

```text
pnpm --filter ai-worker start -- --watch --chunk-size 100
```

常驻模式表示不指定 `batchId`，由 worker 定时向后端查询可执行的 AI 批次。第一版建议先支持指定批次运行，便于调试和验证闭环。

## 4. 参数来源

参数分为业务参数、运行参数和环境变量。

### 4.1 业务参数

业务参数应在创建 AI 批次时提交给后端，并存入 `ai_annotation_batches`：

- `datasetId`
- `maxItems`
- `modelProvider`
- `modelName`
- `promptVersion`
- `confidenceThreshold`
- `samplingRatio`
- `highRiskCategories`
- `itemScope`

这些参数会影响批次范围、审核分流和结果可追溯性，因此应归属于批次。

### 4.2 运行参数

运行参数可以由 AI Worker 启动时传入：

- `batchId`
- `chunkSize`
- `modelBatchSize`
- `concurrency`
- `maxRetries`
- `dryRun`
- `logLevel`

### 4.3 环境变量

环境变量用于放环境地址和敏感配置：

```text
API_BASE_URL=http://localhost:8080
AI_WORKER_TOKEN=xxx
LLM_API_KEY=xxx
LLM_BASE_URL=https://api.example.com/v1
LLM_MODEL=xxx
```

参数边界建议：

| 参数 | 放置位置 | 原因 |
|---|---|---|
| `datasetId` | 创建批次 API | 属于业务对象 |
| `maxItems` | 创建批次 API | 决定批次范围 |
| `modelName` | 创建批次 API / env 默认值 | 需要记录本批次使用的模型 |
| `promptVersion` | 创建批次 API | 结果需要可追溯 |
| `confidenceThreshold` | 创建批次 API | 影响审核分流 |
| `samplingRatio` | 创建批次 API | 影响抽检策略 |
| `batchId` | Worker CLI 参数 | 指定处理哪个批次 |
| `chunkSize` | Worker CLI 参数 | 控制每次从后端领取数量 |
| `modelBatchSize` | Worker CLI 参数 | 控制每次提交给模型的评论数量 |
| `concurrency` | Worker CLI 参数 | 控制并发模型请求 |
| `API_BASE_URL` | 环境变量 | 环境相关 |
| `AI_WORKER_TOKEN` | 环境变量 | 鉴权密钥 |
| `LLM_API_KEY` | 环境变量 | 模型密钥 |

## 5. 大模型交互策略

第一版建议采用小批量多条评论一次请求。

推荐默认值：

| 参数 | 默认值 |
|---|---:|
| `chunkSize` | 100 |
| `modelBatchSize` | 10 |
| `concurrency` | 2 |
| `maxRetries` | 2 |

含义：

- AI Worker 每次从后端领取约 100 条数据。
- Worker 内部再拆成每组 10 条评论。
- 每组评论调用一次大模型。
- 多个模型请求可以按 `concurrency` 并发执行。
- 校验通过后，再把结果分段上传给后端。

不建议第一版每条评论单独请求，因为请求次数多、速度慢、成本高。

也不建议第一版一次给模型 50 或 100 条评论，因为容易出现漏标、错位、JSON 格式异常和批内互相影响。

如果模型输出不稳定，可以将 `modelBatchSize` 降到 5。如果后续稳定性足够，再逐步提升到 20。第一版不建议超过 20。

## 6. Prompt 设计

Prompt 建议拆成两层：

- `system prompt`：固定行为边界、标签体系、输出格式和 JSON 约束。
- `user prompt`：当前数据集标注说明、标注 schema、示例和本批评论列表。

Prompt 中除了任务说明、数据集提示和示例，还应包含：

- 角色边界：只做评论标注，不闲聊，不输出额外解释。
- 标签枚举：明确 `normal / digital_swill` 和所有子类别代码。
- 字段规则：`normal` 时 `sub_category` 必须为空，`digital_swill` 时必须填写子类别。
- 优先级规则：同一评论命中多个类别时如何取主类。
- 边界样本规则：普通吐槽、玩梗、少量表情、群体攻击、个人辱骂等如何区分。
- 独立判断要求：每条评论独立判断，不受同批其他评论影响。
- 输出数量要求：输出 items 数量必须和输入数量一致。
- ID 保留要求：每条输出必须保留输入 `id`。
- 不确定性处理：不确定时降低置信度，并设置 `needs_human_review = true`。
- 严格输出限制：只输出 JSON，不输出 Markdown、代码块或 JSON 之外的任何字符。

Prompt 示例骨架：

```text
你是社交媒体评论内容标注器。

你的任务：
对输入的每条评论独立分类，判断其属于 normal 还是 digital_swill。
如果是 digital_swill，需要进一步判断子类别。

标签定义：
main_category:
- normal
- digital_swill

sub_category:
- pornographic
- violent
- inflammatory
- personal_attack
- low_quality
- other_violation

判定规则：
{annotationGuide}

输出要求：
- 只输出 JSON。
- 不输出 Markdown。
- items 数量必须等于输入数量。
- 每个输出必须保留输入 id。
- normal 时 sub_category 必须为空字符串。
- digital_swill 时 sub_category 必须是合法子类别。

输入：
{items}
```

## 7. JSON 与质量保障

不能只依赖 Prompt 保证合规 JSON。推荐使用三层保障：

```text
Prompt 约束
  + 模型结构化输出能力
  + Worker 侧校验和重试
```

如果模型服务支持 JSON Schema、structured output 或 response format，应优先使用。

Worker 侧必须校验：

- JSON 是否可解析。
- 根结构是否包含 `items` 数组。
- `items` 数量是否等于输入数量。
- 每个 `id` 是否能匹配输入。
- 是否存在重复 `id` 或遗漏 `id`。
- `main_category` 是否在合法枚举中。
- `sub_category` 是否在合法枚举中。
- `normal` 时 `sub_category` 是否为空。
- `digital_swill` 时 `sub_category` 是否必填。
- `confidence_score` 是否在 0 到 1 之间。
- `reason` 是否非空。

校验失败时建议：

1. 使用同一小批次重试一次。
2. 如果仍失败，降低 `modelBatchSize` 后重试。
3. 如果仍失败，改为逐条重试。
4. 仍失败的数据上传为 `failed`，并写入 `errorMessage`。

模型原始输出应保存到 `rawOutput`，便于后续排查 Prompt、模型和解析问题。

## 8. 模块划分

第一版不需要引入复杂 Agent 框架，但建议在代码上拆成清晰模块。

推荐模块：

- 运行控制模块：负责启动参数、批次执行、并发控制、失败重试。
- 后端交互模块：负责调用后端接口领取数据、上传结果和处理鉴权。
- 上下文管理模块：负责管理数据集标注说明、标注 schema、示例、Prompt 版本和批次配置。
- Prompt 构建模块：负责生成 `system prompt` 和 `user prompt`。
- 大模型交互模块：负责调用模型 API，并封装 OpenAI-compatible 适配。
- JSON 质量保障模块：负责 JSON 解析、schema 校验和业务规则校验。
- 结果组装模块：负责把模型输出标准化为后端上传格式。
- 日志模块：负责记录批次进度、失败原因、重试次数和关键耗时。

建议目录：

```text
apps/ai-worker/
  src/
    index.ts
    config.ts
    backend/
      api-client.ts
      types.ts
    context/
      context-manager.ts
      prompt-builder.ts
      examples.ts
    llm/
      llm-client.ts
      structured-output.ts
    quality/
      json-guard.ts
      business-validator.ts
      retry-policy.ts
    runner/
      batch-runner.ts
      model-batch-runner.ts
```

模块职责边界：

- 后端交互模块不关心 Prompt 和模型细节。
- 上下文管理模块不直接调用大模型。
- 大模型交互模块不写数据库、不决定业务状态。
- 质量保障模块不访问后端，只负责把输出变成可信结果或失败原因。
- 运行控制模块负责串联各模块。

## 9. 第一版建议范围

第一版建议先做指定批次执行：

```text
pnpm --filter ai-worker dev -- --batch-id xxx
```

第一版应完成：

- 支持读取配置和 CLI 参数。
- 支持使用 `batchId` 从后端领取数据。
- 支持 `chunkSize` 和 `modelBatchSize`。
- 支持调用 OpenAI-compatible 大模型接口。
- 支持结构化输出或 JSON 输出约束。
- 支持 Worker 侧 JSON 校验和业务校验。
- 支持失败重试、降批量、逐条重试。
- 支持将成功和失败结果上传回后端。

第一版暂不建议做：

- 常驻自动调度多个批次。
- Worker 自己暴露 HTTP 管理接口。
- Worker 直接访问数据库。
- Worker 自己决定数据项最终业务状态。
- 引入复杂 Agent 框架。
