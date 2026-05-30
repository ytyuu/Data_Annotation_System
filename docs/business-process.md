# 数据标注系统业务文档

本文档根据项目流程图和 `docs/database-design.sql` 整理，用于说明数据标注系统的角色、业务对象、主流程和数据库状态含义。后续涉及业务流程、接口、页面或数据库变更时，应优先参考本文档。

## 角色说明

系统当前包含 3 类角色：

| 角色 | 数据库值 | 主要职责 |
| --- | --- | --- |
| 数据集提供者 | `provider` | 上传数据集和标注文档，维护标注要求，审核标注结果，决定通过、退回或关闭数据集 |
| 数据标注员 | `annotator` | 选择可标注的数据集，领取或执行标注任务，提交标注结果，处理被退回的任务 |
| 管理员 | `admin` | 管理账号和异常数据，必要时干预数据集或任务状态 |

审核人员已并入数据集提供者。数据集级别审核由 `provider` 负责，记录在 `dataset_reviews` 表中。

## 核心业务对象

| 业务对象 | 数据库表 | 说明 |
| --- | --- | --- |
| 用户 | `users` | 记录提供者、标注员和管理员账号 |
| 数据集 | `datasets` | 提供者上传的数据集主记录，包含标注说明、标注配置结构和整体状态 |
| 数据项 | `data_items` | 数据集中每个待标注样本，可通过 `metadata` 保存来源、文件名、尺寸、语言、导入批次等扩展信息 |
| 标注任务 | `annotation_tasks` | 数据项分配给标注员后的执行记录 |
| 标注结果 | `annotations` | 标注员提交的结构化结果和争议标记 |
| 数据集审核 | `dataset_reviews` | 提供者对数据集标注质量的审核记录 |

## 主业务流程

1. 数据集提供者上传数据集和标注文档。
2. 系统创建 `datasets` 记录，并将导入的样本写入 `data_items`。
3. 数据集准备完成后，提供者将数据集开放给标注员。
4. 数据标注员选择数据集，系统为标注员生成或分配 `annotation_tasks`。
5. 标注员根据 `annotation_guide` 和 `annotation_schema` 对数据项进行标注。
6. 标注员提交结果后，系统写入 `annotations`，并更新任务和数据项状态。
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
| 标注员互查 | 不同标注员对同一数据项提交结果后进行一致性判断 | `annotations` |
| 是否意见一致 | 判断标注结果是否存在冲突 | `annotations.is_disputed` |
| 数据集标注完成度是否达到 50% | 判断是否触发审核 | `datasets.target_completion_ratio`、`datasets.completed_item_count` |
| 对已标注数据进行抽样审核 | 提供者抽查标注质量 | `dataset_reviews` |
| 由上传该数据集的需求方进行复查 | 提供者处理争议或不确定结果 | `dataset_reviews.provider_id` |
| 由上传该数据集的需求方进行变量调整 | 提供者修改标注要求或标注配置结构 | `datasets.annotation_guide`、`datasets.annotation_schema` |
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

### 标注任务状态

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
- 一个标注任务最多对应一条当前标注结果。
- 当争议数量较多、存在不确定结果或完成比例达到阈值时，系统应触发提供者审核。
- 默认完成比例阈值来自 `datasets.target_completion_ratio`，当前 SQL 默认值为 `50.00`。
- 标注要求调整后，数据集可从 `revision_required` 回到 `annotating`。

## 标注配置与数据扩展信息

`datasets.annotation_schema` 用于定义“怎么标注”，属于数据集级别配置。它可以描述标注类型、字段、选项、控件、必填规则、校验规则和结果格式。

示例：

```json
{
  "version": 1,
  "type": "form",
  "fields": [
    {
      "key": "sentiment",
      "label": "情感倾向",
      "component": "radio",
      "required": true,
      "options": [
        { "value": "positive", "label": "正向" },
        { "value": "neutral", "label": "中性" },
        { "value": "negative", "label": "负向" }
      ]
    }
  ]
}
```

`annotations.result` 保存实际标注结果，应与 `annotation_schema` 中定义的字段对应。

示例：

```json
{
  "sentiment": "positive"
}
```

`data_items.metadata` 用于描述单条原始数据的扩展信息，不用于保存标注配置或标注结果。

示例：

```json
{
  "source": "customer_review",
  "language": "zh-CN",
  "import_batch": "batch_20260530"
}
```
