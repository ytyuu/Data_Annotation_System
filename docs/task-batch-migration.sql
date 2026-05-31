-- 将现有 annotation_tasks 升级为“任务单 + 任务项”结构。
-- 适用于已有开发库；全新建库可直接使用 docs/database-design.sql。

create table if not exists annotation_task_batches (
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

alter table annotation_tasks add column if not exists batch_id uuid;

insert into annotation_task_batches (
  id,
  order_no,
  dataset_id,
  annotator_id,
  status,
  total_count,
  assigned_at,
  started_at,
  submitted_at,
  due_at,
  created_at,
  updated_at
)
select
  gen_random_uuid(),
  'TASK-MIG-' || to_char(min(t.assigned_at), 'YYYYMMDDHH24MISS') || '-' || upper(left(md5(t.dataset_id::text || t.annotator_id::text), 8)),
  t.dataset_id,
  t.annotator_id,
  case
    when bool_or(t.status = 'in_progress') then 'in_progress'
    when bool_and(t.status = 'submitted') then 'submitted'
    when bool_and(t.status = 'cancelled') then 'cancelled'
    when bool_and(t.status = 'accepted') then 'accepted'
    when bool_and(t.status = 'returned') then 'returned'
    else 'assigned'
  end,
  count(*)::integer,
  min(t.assigned_at),
  min(t.started_at),
  max(t.submitted_at),
  min(t.due_at),
  min(t.created_at),
  max(t.updated_at)
from annotation_tasks t
where t.batch_id is null
group by t.dataset_id, t.annotator_id
on conflict (order_no) do nothing;

update annotation_tasks t
set batch_id = b.id
from annotation_task_batches b
where t.batch_id is null
  and t.dataset_id = b.dataset_id
  and t.annotator_id = b.annotator_id;

alter table annotation_tasks alter column batch_id set not null;

alter table annotation_tasks
  drop constraint if exists annotation_tasks_batch_id_fkey,
  add constraint annotation_tasks_batch_id_fkey
    foreign key (batch_id) references annotation_task_batches(id) on delete cascade;

create index if not exists idx_annotation_task_batches_dataset_status
  on annotation_task_batches(dataset_id, status);

create index if not exists idx_annotation_task_batches_annotator_dataset_status
  on annotation_task_batches(annotator_id, dataset_id, status);

create index if not exists idx_annotation_task_batches_annotator_status_assigned_at
  on annotation_task_batches(annotator_id, status, assigned_at desc);

create index if not exists idx_annotation_tasks_batch_status
  on annotation_tasks(batch_id, status);
