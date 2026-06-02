# 数据标注系统业务文档

本文档根据项目流程图和 `docs/data_annotation.sql` 整理，用于说明数据标注系统的角色、业务对象、主流程和数据库状态含义。后续涉及业务流程、接口、页面或数据库变更时，应优先参考本文档。

## 角色说明

系统当前包含 3 类角色：

| 角色 | 数据库值 | 主要职责 |
| --- | --- | --- |
| 数据集提供者 | `provider` | 上传数据集和标注文档，维护标注要求，审核标注结果，决定通过、退回或关闭数据集 |
| 数据标注员 | `annotator` | 选择可标注的数据集，领取或执行标注任务，提交标注结果，处理被退回的任务 |
| 管理员 | `admin` | 管理账号和异常数据，必要时干预数据集或任务状态 |

审核人员已并入数据集提供者。数据集级别审核由 `provider` 负责，记录在 `dataset_reviews` 表中。

用户账号状态由 `users.status` 记录，数据库允许值为 `active`、`disabled`。

## 核心业务对象

| 业务对象 | 数据库表 | 说明 |
| --- | --- | --- |
| 用户 | `users` | 记录提供者、标注员和管理员账号 |
| 数据集 | `datasets` | 提供者上传的数据集主记录，包含标注说明、标注配置结构和整体状态 |
| 数据项 | `data_items` | 数据集中每个待标注样本，可通过 `metadata` 保存来源、文件名、尺寸、语言、导入批次等扩展信息，争议裁决后的最终结果保存在 `final_result` |
| 标注任务单 | `annotation_task_batches` | 标注员每次领取任务生成的统一单号，用于展示任务、退回、开始标注和再次领取判断。支持 `batch_type` 区分标注任务和互查任务 |
| 标注任务项 | `annotation_tasks` | 任务单下每个数据项对应的执行记录，保留独立任务项 ID |
| 标注结果 | `annotations` | 标注员提交的结构化结果和争议标记。原始标注与互查复核分行保存，通过 `annotation_type` 区分，并由 `review_of_annotation_id` 关联 |
| 数据集审核 | `dataset_reviews` | 提供者对数据集标注质量的审核记录 |

数据库当前不在 `annotations` 表中单独保存任务类别字段；标注任务或互查任务由对应任务单 `annotation_task_batches.batch_type` 判定。

## 主业务流程

1. 数据集提供者上传数据集和标注文档。
2. 系统创建 `datasets` 记录，并将导入的样本写入 `data_items`。
3. 数据集准备完成后，提供者将数据集开放给标注员。
4. 数据标注员选择数据集并领取任务，系统生成一张 `annotation_task_batches` 任务单，并为任务单下的数据项生成 `annotation_tasks` 任务项。
   - 标注任务（`batch_type = 'annotation'`）：领取 `pending` 状态的数据项进行首次标注，领取后数据项进入 `assigned` 状态。
   - 互查任务（`batch_type = 'review'`）：领取 `annotated` 或 `disputed` 状态的数据项，由另一位标注员进行复核。
5. 标注员根据任务单进入标注流程，并按照 `annotation_guide` 和 `annotation_schema` 对任务项中的数据项进行标注。
   - 单选无子选项：选中主选项后自动跳转到下一条（间隔 800ms）。
   - 单选带子选项：选中主选项后展开子选项列表，选择子选项后才自动跳转。
   - **多选模式：不自动跳转**，需手动点击"下一条"或"提交任务单"。
6. 标注员提交结果后，系统写入 `annotations`，并更新任务和数据项状态。
   - 标注任务提交：将 `data_items.status` 更新为 `annotated` 或 `disputed`。
   - 互查任务提交：新增或更新 `annotation_type = 'review'` 的标注结果行，`result` 保存互查结果，`review_of_annotation_id` 指向原始标注结果。互查一致性比对同时考虑主选项和子选项值。
7. 如果标注员无法确定结果，可以将标注结果标记为争议或不确定。
8. 系统根据完成比例、争议情况和任务状态判断是否触发复查或数据集审核。
9. 数据集提供者对已标注数据进行抽样审核。
10. 审核通过后，数据集进入完成状态；审核不通过时，提供者退回修改或调整标注要求。

## 流程图对应规则

流程图中的关键判断点可对应为以下规则：

| 流程图节点 | 系统含义 | 相关表字段 |
| --- | --- | --- |
| 上传数据集与标注文档 | 提供者创建数据集和数据项 | `datasets`、`data_items` |
| 选择数据集 | 标注员进入开放数据集 | `datasets.status = 'open'` |
| 对数据进行标注 | 标注员执行任务 | `annotation_tasks.status` |
| 是否直接标记为“不确定” | 标注员认为结果存在争议 | `annotations.is_disputed = true`、`data_items.status = 'disputed'` |
| 标注员互查 | 不同标注员对同一数据项提交结果后进行一致性判断 | `annotations.annotation_type`、`annotations.review_of_annotation_id` |
| 选择任务类别 | 领取时选择标注任务或互查任务 | `annotation_task_batches.batch_type` |
| 是否意见一致 | 判断标注结果是否存在冲突 | `annotations.is_disputed` |
| 数据集标注完成度是否达到 50% | 判断是否触发审核 | `datasets.target_completion_ratio`、`datasets.completed_item_count` |
| 对已标注数据进行抽样审核 | 提供者抽查标注质量 | `dataset_reviews` |
| 由上传该数据集的需求方进行复查 | 提供者处理争议或不确定结果 | `dataset_reviews.provider_id` |
| 由上传该数据集的需求方进行变量调整 | 提供者修改标注要求或标注配置结构 | `datasets.annotation_guide`、`datasets.annotation_schema` |
| 标注选项配置子选项 | 数据集标注结构支持二级子选项 | `datasets.annotation_schema` 中 `options[].hasSubOptions`、`options[].subOptions` |
| 自动跳转下一条 | 单选无子选项/带子选项选中子选项后自动跳转 | 前端 `AnnotatorTaskWorkspaceModal` 自动跳转逻辑 |
| 多选标注 | 多选模式下不自动跳转，需手动提交 | 前端 `AnnotatorTaskWorkspaceModal` 多选模式禁用自动跳转 |
| 数据集标注是否全部完成 | 判断数据集是否结束 | `datasets.status` |

## 状态流转

### 数据集状态

`datasets.status`：

| 状态 | 含义 |
| --- | --- |
| `draft` | 草稿，提供者已创建但未开放 |
| `open` | 已开放，标注员可以选择或领取任务 |
| `annotating` | 标注进行中 |
| `reviewing` | 达到审核条件，等待提供者审核 |
| `revision_required` | 审核后需要调整需求或退回重标 |
| `completed` | 数据集标注和审核完成 |
| `closed` | 人工关闭，不再继续流转 |

建议流转：

```text
draft -> open -> annotating -> reviewing -> completed
                           \-> revision_required -> annotating
                           \-> closed
```

### 数据项状态

`data_items.status`：

| 状态 | 含义 |
| --- | --- |
| `pending` | 待分配或待标注 |
| `assigned` | 已分配给标注员 |
| `annotated` | 已有标注结果 |
| `disputed` | 标注结果存在争议或被标记为不确定 |
| `accepted` | 审核通过 |
| `rejected` | 审核不通过 |

`data_items.content_type` 数据库允许值为 `text`、`image`、`audio`、`video`、`json`。

### 标注任务单状态

`annotation_task_batches.status`：

| 状态 | 含义 |
| --- | --- |
| `assigned` | 任务单已领取，尚未开始 |
| `in_progress` | 任务单已开始标注 |
| `submitted` | 任务单已提交 |
| `returned` | 任务单被退回修改 |
| `accepted` | 任务单结果被采纳 |
| `cancelled` | 任务单取消或由标注员退回 |

`annotation_task_batches.batch_type`：

| 类别 | 含义 |
| --- | --- |
| `annotation` | 标注任务，对 `pending` 数据项进行首次标注 |
| `review` | 互查任务，对 `annotated`/`disputed` 数据项进行复核 |

### 标注任务项状态

`annotation_tasks.status`：

| 状态 | 含义 |
| --- | --- |
| `assigned` | 任务已分配 |
| `in_progress` | 标注员正在处理 |
| `submitted` | 标注员已提交 |
| `returned` | 提供者退回修改 |
| `accepted` | 任务结果被采纳 |
| `cancelled` | 任务取消 |

### 标注结果状态

`annotations.status`：

| 状态 | 含义 |
| --- | --- |
| `submitted` | 标注结果已提交 |
| `returned` | 结果被退回修改 |
| `accepted` | 结果通过审核 |
| `rejected` | 结果被拒绝 |

### 数据集审核状态

`dataset_reviews.status`：

| 状态 | 含义 |
| --- | --- |
| `pending` | 审核记录已创建，等待处理 |
| `approved` | 审核通过 |
| `revision_required` | 需要调整需求或补充标注 |
| `rejected` | 审核拒绝 |

## 权限边界

### 数据集提供者

- 可以创建、编辑、开放、关闭自己上传的数据集。
- 可以维护自己数据集的标注文档和标签结构。
- 可以查看自己数据集下的数据项、任务、标注结果和审核记录。
- 可以对自己数据集的标注结果进行抽样审核。
- 可以退回存在争议或质量不合格的标注任务。

### 数据标注员

- 可以查看开放的数据集。
- 可以领取或执行分配给自己的标注任务。
- 可以提交自己的标注结果。
- 可以将无法确定的数据项标记为争议。
- 只能修改自己的未最终通过的标注结果。

### 管理员

- 可以管理用户账号状态。
- 可以查看系统范围内的数据集、任务和审核状态。
- 可以处理异常状态数据。
- 不作为常规业务审核方，日常审核由数据集提供者完成。

## 业务约束

- 一个数据集必须属于一个数据集提供者。
- 一个数据集可以包含多个数据项。
- 一个数据项可以分配给多个标注员，以支持互查和一致性判断。
- 同一个标注员对同一个数据项最多只能有一个任务项，通过 `annotation_tasks` 表的 `unique(item_id, annotator_id)` 约束实现。
- 一个标注任务最多对应一条标注结果，通过 `annotations.task_id` 唯一约束实现。
- 互查任务要求标注员未参与过该数据项，业务上应基于 `annotation_tasks` 的历史记录排除已参与的数据项。
- 每个任务单必须有唯一单号，通过 `annotation_task_batches.order_no` 唯一约束实现。
- 当争议数量较多、存在不确定结果或完成比例达到阈值时，系统应触发提供者审核。
- 默认完成比例阈值来自 `datasets.target_completion_ratio`，当前 SQL 默认值为 `50.00`。
- `datasets.target_completion_ratio` 数据库限制为大于 0 且不超过 100。
- `datasets.item_count`、`datasets.completed_item_count`、`annotation_task_batches.total_count`、`dataset_reviews.sampled_item_count` 和 `dataset_reviews.disputed_item_count` 均为非负数。
- 标注要求调整后，数据集可从 `revision_required` 回到 `annotating`。
- 同一数据集下，同一标注员不能同时持有同类型的活跃任务单（例如不能同时有两个 `annotation` 类型的 `assigned`/`in_progress` 任务单），但可以同时持有一个标注任务单和一个互查任务单。该规则属于业务层约束，当前 SQL 仅提供相关查询索引。
- 删除数据集时，数据库会级联删除其数据项、任务单、任务项、标注结果和审核记录；删除任务单时会级联删除其任务项；删除任务项时会级联删除对应标注结果。

## 标注配置与数据扩展信息

`datasets.annotation_schema` 用于定义”怎么标注”，属于数据集级别配置。它可以描述标注类型、字段、选项、控件、必填规则、校验规则和结果格式。

### 基础分类标注示例（无子选项）

```json
{
  "version": 1,
  "type": "classification",
  "selectionMode": "single",
  "options": [
    { "value": "positive", "label": "正向" },
    { "value": "neutral", "label": "中性" },
    { "value": "negative", "label": "负向" }
  ]
}
```

对应标注结果：

```json
{ "value": "positive" }
```

### 带子选项的二级分类标注示例

每个主选项可独立设置是否有子选项、子选项的单选/多选模式：

```json
{
  "version": 1,
  "type": "classification",
  "selectionMode": "single",
  "options": [
    {
      "value": "emotion",
      "label": "情感",
      "hasSubOptions": true,
      "subSelectionMode": "single",
      "subOptions": [
        { "value": "positive", "label": "正面" },
        { "value": "neutral", "label": "中性" },
        { "value": "negative", "label": "负面" }
      ]
    },
    {
      "value": "topic",
      "label": "主题",
      "hasSubOptions": true,
      "subSelectionMode": "multiple",
      "subOptions": [
        { "value": "product", "label": "产品" },
        { "value": "service", "label": "服务" },
        { "value": "logistics", "label": "物流" }
      ]
    },
    {
      "value": "other",
      "label": "其他",
      "hasSubOptions": false
    }
  ]
}
```

对应标注结果（含子选项）：

```json
{
  "value": "emotion",
  "subValues": { "emotion": ["positive"] }
}
```

多选主选项带子选项的结果：

```json
{
  "values": ["topic"],
  "subValues": { "topic": ["product", "service"] }
}
```

### 标注结果存储规则

- 无子选项：单选 `{ "value": "xxx" }`，多选 `{ "values": ["xxx", "yyy"] }`
- 有子选项：在原有格式基础上增加 `subValues` 字段，格式为 `Record<string, string[]>`
- `subValues` 的 key 为主选项 value，value 为子选项 value 的数组（单选子选项时数组长度为 1）

`data_items.metadata` 用于描述单条原始数据的扩展信息，不用于保存标注配置。争议裁决后的最终结果保存到 `data_items.final_result`，并记录 `finalized_at`/`finalized_by`。

示例：

```json
{
  "source": "customer_review",
  "language": "zh-CN",
  "import_batch": "batch_20260530"
}
```
