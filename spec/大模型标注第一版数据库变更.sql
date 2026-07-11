-- 大模型标注第一版数据库变更
-- 适用数据库：PostgreSQL 14+
-- 执行责任：由项目维护者在开发数据库中执行并验证；Codex 不连接或查看数据库。

BEGIN;

-- 1. 扩展数据项状态，AI 处理中的数据不进入人工领取池。
ALTER TABLE "public"."data_items"
    DROP CONSTRAINT IF EXISTS "data_items_status_check";

ALTER TABLE "public"."data_items"
    ADD CONSTRAINT "data_items_status_check"
    CHECK (
        status::text = ANY (
            ARRAY[
                'pending'::character varying,
                'assigned'::character varying,
                'ai_processing'::character varying,
                'annotated'::character varying,
                'disputed'::character varying,
                'accepted'::character varying,
                'rejected'::character varying
            ]::text[]
        )
    );

COMMENT ON COLUMN "public"."data_items"."status" IS
    '数据项状态：pending、assigned、ai_processing、annotated、disputed、accepted、rejected';

-- 2. AI 标注批次。
CREATE TABLE "public"."ai_annotation_batches" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "dataset_id" uuid NOT NULL,
    "provider_id" uuid NOT NULL,
    "status" varchar(32) NOT NULL DEFAULT 'pending',
    "model_provider" varchar(64) NOT NULL DEFAULT 'deepseek',
    "model_name" varchar(128) NOT NULL,
    "prompt_version" varchar(64) NOT NULL,
    "annotation_schema_snapshot" jsonb NOT NULL,
    "annotation_guide_snapshot" text,
    "config" jsonb NOT NULL DEFAULT '{}'::jsonb,
    "total_count" int4 NOT NULL DEFAULT 0,
    "processed_count" int4 NOT NULL DEFAULT 0,
    "success_count" int4 NOT NULL DEFAULT 0,
    "failed_count" int4 NOT NULL DEFAULT 0,
    "needs_review_count" int4 NOT NULL DEFAULT 0,
    "accepted_count" int4 NOT NULL DEFAULT 0,
    "rejected_count" int4 NOT NULL DEFAULT 0,
    "model_request_count" int4 NOT NULL DEFAULT 0,
    "prompt_tokens" int8 NOT NULL DEFAULT 0,
    "completion_tokens" int8 NOT NULL DEFAULT 0,
    "error_message" text,
    "started_at" timestamptz(6),
    "finished_at" timestamptz(6),
    "cancelled_at" timestamptz(6),
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "ai_annotation_batches_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ai_annotation_batches_dataset_id_fkey"
        FOREIGN KEY ("dataset_id") REFERENCES "public"."datasets" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_batches_provider_id_fkey"
        FOREIGN KEY ("provider_id") REFERENCES "public"."users" ("id"),
    CONSTRAINT "ai_annotation_batches_status_check"
        CHECK (status::text = ANY (ARRAY['pending', 'running', 'completed', 'failed', 'cancelled']::text[])),
    CONSTRAINT "ai_annotation_batches_counts_check"
        CHECK (
            total_count >= 0
            AND processed_count >= 0
            AND success_count >= 0
            AND failed_count >= 0
            AND needs_review_count >= 0
            AND accepted_count >= 0
            AND rejected_count >= 0
            AND model_request_count >= 0
            AND prompt_tokens >= 0
            AND completion_tokens >= 0
        )
);

CREATE INDEX "idx_ai_annotation_batches_dataset_status_created_at"
    ON "public"."ai_annotation_batches" ("dataset_id", "status", "created_at" DESC);

CREATE INDEX "idx_ai_annotation_batches_provider_status_created_at"
    ON "public"."ai_annotation_batches" ("provider_id", "status", "created_at" DESC);

COMMENT ON TABLE "public"."ai_annotation_batches" IS '大模型批量标注任务及执行统计';
COMMENT ON COLUMN "public"."ai_annotation_batches"."annotation_schema_snapshot" IS '创建批次时的数据集标注结构快照';
COMMENT ON COLUMN "public"."ai_annotation_batches"."annotation_guide_snapshot" IS '创建批次时的数据集标注说明快照';
COMMENT ON COLUMN "public"."ai_annotation_batches"."config" IS '置信度阈值、抽检率、高风险标签、最大尝试次数和 metadata 白名单';

-- 3. AI 单条标注结果。
CREATE TABLE "public"."ai_annotation_results" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "batch_id" uuid NOT NULL,
    "dataset_id" uuid NOT NULL,
    "item_id" uuid NOT NULL,
    "round_no" int4 NOT NULL DEFAULT 1,
    "status" varchar(32) NOT NULL DEFAULT 'pending',
    "result" jsonb,
    "accepted_result" jsonb,
    "result_hash" varchar(64),
    "confidence" varchar(16),
    "confidence_score" numeric(5,4),
    "reason" text,
    "needs_human_review" bool NOT NULL DEFAULT false,
    "is_sampled" bool NOT NULL DEFAULT false,
    "risk_flags" jsonb NOT NULL DEFAULT '[]'::jsonb,
    "raw_output" jsonb,
    "error_message" text,
    "attempt_count" int4 NOT NULL DEFAULT 0,
    "chunk_no" int4,
    "request_id" varchar(80),
    "leased_at" timestamptz(6),
    "lease_expires_at" timestamptz(6),
    "reviewed_by" uuid,
    "reviewed_at" timestamptz(6),
    "review_action" varchar(32),
    "review_comment" text,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "ai_annotation_results_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ai_annotation_results_batch_item_round_key" UNIQUE ("batch_id", "item_id", "round_no"),
    CONSTRAINT "ai_annotation_results_batch_id_fkey"
        FOREIGN KEY ("batch_id") REFERENCES "public"."ai_annotation_batches" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_results_dataset_id_fkey"
        FOREIGN KEY ("dataset_id") REFERENCES "public"."datasets" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_results_item_id_fkey"
        FOREIGN KEY ("item_id") REFERENCES "public"."data_items" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_results_reviewed_by_fkey"
        FOREIGN KEY ("reviewed_by") REFERENCES "public"."users" ("id") ON DELETE SET NULL,
    CONSTRAINT "ai_annotation_results_status_check"
        CHECK (
            status::text = ANY (
                ARRAY['pending', 'processing', 'ai_labeled', 'needs_review', 'accepted', 'rejected', 'failed']::text[]
            )
        ),
    CONSTRAINT "ai_annotation_results_confidence_check"
        CHECK (confidence IS NULL OR confidence::text = ANY (ARRAY['high', 'medium', 'low']::text[])),
    CONSTRAINT "ai_annotation_results_confidence_score_check"
        CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1)),
    CONSTRAINT "ai_annotation_results_attempt_count_check"
        CHECK (attempt_count >= 0),
    CONSTRAINT "ai_annotation_results_review_action_check"
        CHECK (
            review_action IS NULL
            OR review_action::text = ANY (
                ARRAY['accept', 'modify_accept', 'reject_to_human', 'reject_retry']::text[]
            )
        )
);

CREATE INDEX "idx_ai_annotation_results_batch_status_created_at"
    ON "public"."ai_annotation_results" ("batch_id", "status", "created_at");

CREATE INDEX "idx_ai_annotation_results_dataset_status"
    ON "public"."ai_annotation_results" ("dataset_id", "status");

CREATE INDEX "idx_ai_annotation_results_status_lease_expires_at"
    ON "public"."ai_annotation_results" ("status", "lease_expires_at");

CREATE INDEX "idx_ai_annotation_results_batch_request_id"
    ON "public"."ai_annotation_results" ("batch_id", "request_id");

CREATE INDEX "idx_ai_annotation_results_batch_sampled_status"
    ON "public"."ai_annotation_results" ("batch_id", "is_sampled", "status");

COMMENT ON TABLE "public"."ai_annotation_results" IS '大模型单条标注执行、分流和提供方审核记录';
COMMENT ON COLUMN "public"."ai_annotation_results"."result" IS '与人工标注 value、values、subValues 结构一致的标准化结果';
COMMENT ON COLUMN "public"."ai_annotation_results"."accepted_result" IS '提供方修改后接受的结果';
COMMENT ON COLUMN "public"."ai_annotation_results"."raw_output" IS '当前数据项对应的模型原始输出片段';
COMMENT ON COLUMN "public"."ai_annotation_results"."lease_expires_at" IS 'Worker 领取租约到期时间，超时后允许重新领取';

COMMIT;

-- 回滚参考：必须先确认不存在需要保留的 AI 批次和 ai_processing 数据。
-- BEGIN;
-- DROP TABLE IF EXISTS "public"."ai_annotation_results";
-- DROP TABLE IF EXISTS "public"."ai_annotation_batches";
-- ALTER TABLE "public"."data_items" DROP CONSTRAINT IF EXISTS "data_items_status_check";
-- ALTER TABLE "public"."data_items"
--     ADD CONSTRAINT "data_items_status_check"
--     CHECK (status::text = ANY (ARRAY['pending', 'assigned', 'annotated', 'disputed', 'accepted', 'rejected']::text[]));
-- COMMIT;
