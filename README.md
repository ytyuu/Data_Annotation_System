# 数据标注系统 Monorepo

基于 Turbo 构建的 monorepo，支持文本分类标注，含二级子选项、互查复核、争议处理等完整流程。

## 目录结构

```
apps/
  api/                    - 后端 API（Kotlin + Javalin + Exposed）
    src/main/kotlin/      - Kotlin 源码
    pom.xml               - Maven 构建配置
  web/                    - 前端应用（React + TypeScript + Tailwind CSS + Vite）
    src/                  - 源码入口
docs/                     - 业务文档和数据库设计
scripts/                  - 工具脚本（生成测试数据等）
```

## 环境要求

- [mvnd](https://github.com/apache/maven-mvnd)（Maven Daemon，用于后端编译）
- JDK 25
- pnpm 10.29.2
- Node.js

## 快速启动

```powershell
# 安装所有依赖（根目录和各 apps）
pnpm install

# 启动开发服务器（通过 Turbo 并行启动 API 和前端）
pnpm dev
```

API 开发模式使用 `mvnd` 编译，保存 `apps/api/src/main/kotlin` 下的 Kotlin 源码后会自动重新编译并重启后端。

## 端口

- API: `http://localhost:7000`
- Web: `http://localhost:3000`

## 核心功能

### 角色与权限

- **数据提供者（Provider）**：创建数据集、配置标注结构、导入数据、审核标注结果、处理争议
- **数据标注员（Annotator）**：领取任务、标注数据、提交结果

### 标注结构

支持**二级子选项**标注：

- 主选项：可配置为单选/多选
- 子选项：每个主选项可独立设置是否有子选项、子选项单选/多选
- 标注结果 JSON 包含主选项和子选项信息：`{ "value": "emotion", "subValues": { "emotion": ["positive"] } }`

### 任务流程

1. **创建数据集**：配置名称、描述、标注说明、标注选项（含子选项）、目标完成比例
2. **导入数据**：支持文本数据批量导入（每行一条）
3. **发布数据集**：标注员可在"可标注数据集"页面领取任务
4. **标注任务**：
   - 单选无子选项：选中后自动跳转下一条（间隔 800ms）
   - 单选带子选项：选中主选项后展开子选项，选择子选项后自动跳转
   - **多选模式：不自动跳转**，需手动切换或提交
5. **互查复核**：原始标注完成后，其他标注员可对同一数据进行互查
6. **争议处理**：原始标注与互查结果不一致时，由提供者裁决最终标注结果

### 数据集概览

提供者页面支持查看数据集数据项状态分布（圆环图），包括：待处理、已分配、已标注、有争议、已通过等状态的统计。

### 导出规则

数据集需达到目标完成比例后才能导出，未达标时导出按钮禁用并显示 hover 提示。

## API 接口

### 公开接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/` | 服务状态 |
| `GET` | `/api/health` | 健康检查 |
| `GET` | `/api/health/database` | 数据库健康检查 |
| `POST` | `/api/register` | 用户注册 |
| `POST` | `/api/login` | 用户登录并签发 JWT |
| `GET` | `/api/me` | 当前登录用户信息 |

### 提供者接口（需 provider 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET/POST/PUT/DELETE` | `/api/provider/datasets` | 数据集 CRUD |
| `POST` | `/api/provider/datasets/{id}/publish` | 发布数据集 |
| `GET/POST` | `/api/provider/datasets/{id}/items` | 数据项查询/导入 |
| `DELETE` | `/api/provider/datasets/{id}/items/{itemId}` | 删除数据项 |
| `GET` | `/api/provider/datasets/{id}/disputed-items` | 争议数据项列表 |
| `POST` | `/api/provider/datasets/{id}/items/{itemId}/resolve-dispute` | 处理争议 |

### 标注员接口（需 annotator 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/annotator/datasets` | 可领取的开放数据集 |
| `POST` | `/api/annotator/datasets/{id}/claim` | 领取标注/互查任务 |
| `GET` | `/api/annotator/tasks?status=...` | 任务单列表（支持多状态筛选，逗号分隔） |
| `GET` | `/api/annotator/task-batches/{id}/tasks` | 任务单详情 |
| `GET` | `/api/annotator/task-batches/{id}/workspace` | 标注工作台数据 |
| `POST` | `/api/annotator/task-batches/{id}/start` | 开始任务单 |
| `POST` | `/api/annotator/task-batches/{id}/return` | 退回任务单 |
| `POST` | `/api/annotator/task-batches/{id}/submit` | 提交标注结果 |

## 常用命令

```powershell
# 构建所有应用（通过 Turbo 并行构建）
pnpm build

# 运行所有应用的测试
pnpm test

# 运行代码检查
pnpm lint
```

## 单独操作后端

```powershell
cd apps/api

# 编译
mvnd compile

# 运行
mvnd exec:java

# 指定端口运行
mvnd exec:java -Dexec.args=8080
```

## 生成测试数据

```powershell
# 生成 10 条单选测试数据
node scripts/generate-test-dataset.mjs --count=10 --option-count=4 --question-type=single

# 生成 100 条多选测试数据
node scripts/generate-test-dataset.mjs --count=100 --option-count=5 --question-type=multiple

```

## 说明

各应用的入口在 `apps/` 目录下。根目录的 `pnpm dev` 命令通过 Turbo 并行运行 API 和前端工作区的开发脚本。
