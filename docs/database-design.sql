-- 数据标注系统数据库表设计
-- 目标数据库：PostgreSQL
-- 说明：本设计包含 6 张核心业务表，不包含第 7 张表。

-- 启用 UUID 生成函数 gen_random_uuid()
create extension if not exists pgcrypto;

-- 用户账号表
-- role:
--   provider  数据集提供者
--   annotator 数据标注员
--   admin     管理员
create table users (
  id uuid primary key default gen_random_uuid(),
  username varchar(64) not null unique,
  password_hash varchar(255) not null,
  display_name varchar(80) not null,
  role varchar(24) not null check (role in ('provider', 'annotator', 'admin')),
  status varchar(24) not null default 'active' check (status in ('active', 'disabled')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table users is '用户账号表，覆盖数据集提供者、标注员和管理员';
comment on column users.id is '用户 ID';
comment on column users.username is '登录用户名，全局唯一';
comment on column users.password_hash is '密码哈希值，不保存明文密码';
comment on column users.display_name is '用户显示名称';
comment on column users.role is '用户角色：provider、annotator、admin';
comment on column users.status is '账号状态：active、disabled';
comment on column users.created_at is '创建时间';
comment on column users.updated_at is '更新时间';

-- 数据集主表
-- 记录提供者上传的数据集、标注说明、标注配置结构和整体流程状态。
create table datasets (
  id uuid primary key default gen_random_uuid(),
  provider_id uuid not null references users(id),
  name varchar(120) not null,
  description text,
  annotation_guide text,
  annotation_schema jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'draft'
    check (status in ('draft', 'open', 'annotating', 'reviewing', 'revision_required', 'completed', 'closed')),
  target_completion_ratio numeric(5, 2) not null default 50.00,
  item_count integer not null default 0 check (item_count >= 0),
  completed_item_count integer not null default 0 check (completed_item_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (target_completion_ratio > 0 and target_completion_ratio <= 100)
);

comment on table datasets is '数据集主表，记录上传方、标注说明、标注配置结构和整体状态';
comment on column datasets.id is '数据集 ID';
comment on column datasets.provider_id is '数据集提供者用户 ID';
comment on column datasets.name is '数据集名称';
comment on column datasets.description is '数据集描述';
comment on column datasets.annotation_guide is '标注文档或标注说明';
comment on column datasets.annotation_schema is '标注配置结构，定义标注类型、字段、选项、校验规则和结果格式，使用 JSONB 保存';
comment on column datasets.status is '数据集状态：draft、open、annotating、reviewing、revision_required、completed、closed';
comment on column datasets.target_completion_ratio is '触发审核的目标完成比例，默认 50%';
comment on column datasets.item_count is '数据项总数';
comment on column datasets.completed_item_count is '已完成数据项数量';
comment on column datasets.created_at is '创建时间';
comment on column datasets.updated_at is '更新时间';

-- 数据项明细表
-- 每一行代表一个待标注样本，可以是文本、图片地址、音频地址、视频地址或 JSON 内容。
create table data_items (
  id uuid primary key default gen_random_uuid(),
  dataset_id uuid not null references datasets(id) on delete cascade,
  content text not null,
  content_type varchar(32) not null default 'text' check (content_type in ('text', 'image', 'audio', 'video', 'json')),
  metadata jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'pending'
    check (status in ('pending', 'assigned', 'annotated', 'disputed', 'accepted', 'rejected')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table data_items is '数据项明细表，每一行代表一个待标注样本';
comment on column data_items.id is '数据项 ID';
comment on column data_items.dataset_id is '所属数据集 ID';
comment on column data_items.content is '数据内容或资源地址';
comment on column data_items.content_type is '内容类型：text、image、audio、video、json';
comment on column data_items.metadata is '数据项扩展信息，例如来源、文件名、尺寸、语言、导入批次等，不保存标注规则或标注结果';
comment on column data_items.status is '数据项状态：pending、assigned、annotated、disputed、accepted、rejected';
comment on column data_items.created_at is '创建时间';
comment on column data_items.updated_at is '更新时间';

-- 标注任务单表
-- 标注员每次领取任务时生成一张任务单，任务单下包含多条具体任务项。
create table annotation_task_batches (
  id uuid primary key default gen_random_uuid(),
  order_no varchar(40) not null unique,
  dataset_id uuid not null references datasets(id) on delete cascade,
  annotator_id uuid not null references users(id),
  status varchar(32) not null default 'assigned'
    check (status in ('assigned', 'in_progress', 'submitted', 'returned', 'accepted', 'cancelled')),
  total_count integer not null default 0 check (total_count >= 0),
  assigned_at timestamptz not null default now(),
  started_at timestamptz,
  submitted_at timestamptz,
  due_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table annotation_task_batches is '标注任务单表，记录标注员一次领取任务形成的统一任务单';
comment on column annotation_task_batches.id is '任务单 ID';
comment on column annotation_task_batches.order_no is '任务单号，用于前端展示和整单操作';
comment on column annotation_task_batches.dataset_id is '所属数据集 ID';
comment on column annotation_task_batches.annotator_id is '标注员用户 ID';
comment on column annotation_task_batches.status is '任务单状态：assigned、in_progress、submitted、returned、accepted、cancelled';
comment on column annotation_task_batches.total_count is '任务单下任务项数量';
comment on column annotation_task_batches.assigned_at is '领取时间';
comment on column annotation_task_batches.started_at is '开始标注时间';
comment on column annotation_task_batches.submitted_at is '整单提交时间';
comment on column annotation_task_batches.due_at is '任务单截止时间';
comment on column annotation_task_batches.created_at is '创建时间';
comment on column annotation_task_batches.updated_at is '更新时间';

-- 标注任务项表
-- 数据项分配给标注员后生成任务项，用于跟踪单条数据项的标注状态。
create table annotation_tasks (
  id uuid primary key default gen_random_uuid(),
  batch_id uuid not null references annotation_task_batches(id) on delete cascade,
  dataset_id uuid not null references datasets(id) on delete cascade,
  item_id uuid not null references data_items(id) on delete cascade,
  annotator_id uuid not null references users(id),
  status varchar(32) not null default 'assigned'
    check (status in ('assigned', 'in_progress', 'submitted', 'returned', 'accepted', 'cancelled')),
  assigned_at timestamptz not null default now(),
  started_at timestamptz,
  submitted_at timestamptz,
  due_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (item_id, annotator_id)
);

comment on table annotation_tasks is '标注任务项表，记录任务单下每个数据项的执行状态';
comment on column annotation_tasks.id is '任务项 ID';
comment on column annotation_tasks.batch_id is '所属任务单 ID';
comment on column annotation_tasks.dataset_id is '所属数据集 ID';
comment on column annotation_tasks.item_id is '待标注数据项 ID';
comment on column annotation_tasks.annotator_id is '标注员用户 ID';
comment on column annotation_tasks.status is '任务状态：assigned、in_progress、submitted、returned、accepted、cancelled';
comment on column annotation_tasks.assigned_at is '分配时间';
comment on column annotation_tasks.started_at is '开始标注时间';
comment on column annotation_tasks.submitted_at is '提交时间';
comment on column annotation_tasks.due_at is '任务截止时间';
comment on column annotation_tasks.created_at is '创建时间';
comment on column annotation_tasks.updated_at is '更新时间';

-- 标注结果表
-- 保存标注员提交的结构化标注结果，result 使用 JSONB 适配不同数据类型和标签结构。
create table annotations (
  id uuid primary key default gen_random_uuid(),
  task_id uuid not null references annotation_tasks(id) on delete cascade,
  item_id uuid not null references data_items(id) on delete cascade,
  annotator_id uuid not null references users(id),
  result jsonb not null,
  comment text,
  is_disputed boolean not null default false,
  status varchar(32) not null default 'submitted'
    check (status in ('submitted', 'returned', 'accepted', 'rejected')),
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (task_id)
);

comment on table annotations is '标注结果表，保存标注内容、争议标记和提交信息';
comment on column annotations.id is '标注结果 ID';
comment on column annotations.task_id is '所属标注任务 ID';
comment on column annotations.item_id is '被标注的数据项 ID';
comment on column annotations.annotator_id is '提交标注结果的标注员 ID';
comment on column annotations.result is '标注结果，使用 JSONB 保存';
comment on column annotations.comment is '标注员备注';
comment on column annotations.is_disputed is '是否存在争议';
comment on column annotations.status is '标注结果状态：submitted、returned、accepted、rejected';
comment on column annotations.submitted_at is '提交时间';
comment on column annotations.reviewed_at is '审核时间';
comment on column annotations.created_at is '创建时间';
comment on column annotations.updated_at is '更新时间';

-- 数据集审核表
-- 记录数据集级别的审核结果、抽样数量、争议数量和审核意见。
create table dataset_reviews (
  id uuid primary key default gen_random_uuid(),
  dataset_id uuid not null references datasets(id) on delete cascade,
  provider_id uuid not null references users(id),
  status varchar(32) not null
    check (status in ('pending', 'approved', 'revision_required', 'rejected')),
  sampled_item_count integer not null default 0 check (sampled_item_count >= 0),
  disputed_item_count integer not null default 0 check (disputed_item_count >= 0),
  opinion text,
  reviewed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table dataset_reviews is '数据集审核表，由数据集提供方记录抽样审核、退回和完成意见';
comment on column dataset_reviews.id is '审核记录 ID';
comment on column dataset_reviews.dataset_id is '被审核的数据集 ID';
comment on column dataset_reviews.provider_id is '执行审核的数据集提供者用户 ID';
comment on column dataset_reviews.status is '审核状态：pending、approved、revision_required、rejected';
comment on column dataset_reviews.sampled_item_count is '抽样审核的数据项数量';
comment on column dataset_reviews.disputed_item_count is '存在争议的数据项数量';
comment on column dataset_reviews.opinion is '审核意见';
comment on column dataset_reviews.reviewed_at is '审核完成时间';
comment on column dataset_reviews.created_at is '创建时间';
comment on column dataset_reviews.updated_at is '更新时间';

-- 常用查询索引
create index idx_users_role_status on users(role, status);

create index idx_datasets_status_created_at on datasets(status, created_at desc);
create index idx_datasets_provider_status_updated_at on datasets(provider_id, status, updated_at desc);

create index idx_data_items_dataset_status on data_items(dataset_id, status);
create index idx_data_items_metadata_gin on data_items using gin (metadata);
create index idx_data_items_pending_by_dataset on data_items(dataset_id, created_at) where status = 'pending';

create index idx_annotation_task_batches_dataset_status on annotation_task_batches(dataset_id, status);
create index idx_annotation_task_batches_annotator_dataset_status on annotation_task_batches(annotator_id, dataset_id, status);
create index idx_annotation_task_batches_annotator_status_assigned_at on annotation_task_batches(annotator_id, status, assigned_at desc);

create index idx_annotation_tasks_batch_status on annotation_tasks(batch_id, status);
create index idx_annotation_tasks_dataset_status on annotation_tasks(dataset_id, status);
create index idx_annotation_tasks_annotator_dataset_status on annotation_tasks(annotator_id, dataset_id, status);
create index idx_annotation_tasks_annotator_status_assigned_at on annotation_tasks(annotator_id, status, assigned_at desc);

create index idx_annotations_item_status on annotations(item_id, status);
create index idx_annotations_result_gin on annotations using gin (result);
create index idx_annotations_disputed on annotations(item_id) where is_disputed = true;

create index idx_dataset_reviews_dataset_created_at on dataset_reviews(dataset_id, created_at desc);
create index idx_dataset_reviews_provider_status_created_at on dataset_reviews(provider_id, status, created_at desc);
