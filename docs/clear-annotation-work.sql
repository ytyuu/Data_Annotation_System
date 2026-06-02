-- 清空所有标注任务单、任务项和标注结果，并恢复数据项状态。
--
-- 表关系依据 docs/data_annotation.sql：
-- annotation_task_batches --ON DELETE CASCADE--> annotation_tasks
-- annotation_tasks        --ON DELETE CASCADE--> annotations
--
-- 执行效果：
-- 1. 删除所有 annotation_task_batches。
-- 2. 通过外键级联删除所有 annotation_tasks 和 annotations。
-- 3. 将所有数据项状态恢复为 pending。
-- 4. 将数据集 completed_item_count 重置为 0。
--
-- 注意：此脚本会清空全库标注执行数据，保留 users、datasets、data_items。

begin;

update data_items
set
    status = 'pending',
    updated_at = now()
where status <> 'pending';

delete from annotation_task_batches;
delete from annotation_tasks;
delete from annotations;

update datasets
set
    completed_item_count = 0,
    updated_at = now()
where completed_item_count <> 0;

commit;
