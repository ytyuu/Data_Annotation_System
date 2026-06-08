-- 清空所有标注执行数据（任务单、任务项、标注结果、争议裁决、审核记录），并恢复数据项和数据集状态。
--
-- 表关系依据 docs/data_annotation.sql：
-- annotation_task_batches --ON DELETE CASCADE--> annotation_tasks
-- annotation_tasks        --ON DELETE CASCADE--> annotations
--
-- 执行效果：
-- 1. 删除所有 dataset_reviews（数据集审核记录）。
-- 2. 删除所有 annotation_task_batches（级联删除 annotation_tasks 和 annotations）。
-- 3. 将所有数据项状态恢复为 pending，并清空 final_result/finalized_at/finalized_by。
-- 4. 将所有数据集状态恢复为 in_progress（已发布进行中状态），并重置 completed_item_count。
--
-- 注意：此脚本会清空全库标注执行数据，保留 users、datasets、data_items。

begin;

-- 1. 清空数据集审核记录
delete from dataset_reviews;

-- 2. 清空标注任务单（级联删除 annotation_tasks 和 annotations）
delete from annotation_task_batches;
delete from annotation_tasks;
delete from annotations;

-- 3. 恢复数据项状态并清空争议裁决结果
update data_items
set
    status = 'pending',
    final_result = null,
    finalized_at = null,
    finalized_by = null,
    updated_at = now()
where status <> 'pending'
   or final_result is not null
   or finalized_at is not null
   or finalized_by is not null;

-- 4. 恢复数据集状态并重置统计计数
-- 数据集状态从 reviewing/completed 恢复为 in_progress
-- 这样数据集可以继续被标注员领取任务
update datasets
set
    status = 'in_progress',
    completed_item_count = 0,
    updated_at = now()
where status <> 'draft'
   and status <> 'in_progress';

commit;
