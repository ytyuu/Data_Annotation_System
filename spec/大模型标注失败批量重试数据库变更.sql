-- 大模型标注失败批量重试数据库变更
-- 适用数据库：PostgreSQL 14+
-- 执行责任：由项目维护者在开发数据库中执行并验证；Codex 不连接或查看数据库。
-- 执行说明：请在部署依赖新字段的应用代码前执行。本脚本支持重复执行（幂等）。
-- 权限要求：执行用户必须是 ai_annotation_batches、ai_annotation_results、
--           datasets、data_items、users 表的 owner，或具有 superuser 权限。
--           常见做法：以 postgres 超级用户执行，或在 psql 中 \set ON_ERROR_STOP on 后执行。

-- 前置权限检查：若当前用户不是目标表的 owner 则主动报错终止。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relname = 'ai_annotation_batches'
          AND c.relowner = (SELECT usesysid FROM pg_catalog.pg_user WHERE usename = current_user)
    ) THEN
        RAISE EXCEPTION '当前用户 % 不是表 ai_annotation_batches 的 owner，请使用表 owner 或 superuser 执行此脚本', current_user;
    END IF;
END $$;

BEGIN;

-- 1. 扩展 AI 标注批次的当前失败信息和业务重试次数。
DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_batches"
        ADD COLUMN "failure_code" varchar(64);
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_batches.failure_code 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_batches"
        ADD COLUMN "failure_stage" varchar(32);
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_batches.failure_stage 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_batches"
        ADD COLUMN "failed_at" timestamptz(6);
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_batches.failed_at 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_batches"
        ADD COLUMN "retry_count" int4 NOT NULL DEFAULT 0;
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_batches.retry_count 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_batches"
        ADD CONSTRAINT "ai_annotation_batches_retry_count_check"
            CHECK (retry_count >= 0);
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE '约束 ai_annotation_batches_retry_count_check 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_batches"
        ADD CONSTRAINT "ai_annotation_batches_failure_stage_check"
            CHECK (
                failure_stage IS NULL
                OR failure_stage::text = ANY (
                    ARRAY[
                        'model_request',
                        'model_output',
                        'lease',
                        'worker_runtime',
                        'backend_request',
                        'batch_dispatch',
                        'unknown'
                    ]::text[]
                )
            );
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE '约束 ai_annotation_batches_failure_stage_check 已存在，跳过'; END $$;

COMMENT ON COLUMN "public"."ai_annotation_batches"."failure_code" IS '当前最后一次批次失败的稳定代码';
COMMENT ON COLUMN "public"."ai_annotation_batches"."failure_stage" IS '当前最后一次批次失败阶段';
COMMENT ON COLUMN "public"."ai_annotation_batches"."failed_at" IS '当前最后一次批次失败时间';
COMMENT ON COLUMN "public"."ai_annotation_batches"."retry_count" IS '批次被提供方恢复执行的业务重试次数';

-- 2. 扩展 AI 单条结果的当前失败信息和业务重试次数。
DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_results"
        ADD COLUMN "failure_code" varchar(64);
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_results.failure_code 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_results"
        ADD COLUMN "failure_stage" varchar(32);
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_results.failure_stage 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_results"
        ADD COLUMN "failed_at" timestamptz(6);
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_results.failed_at 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_results"
        ADD COLUMN "retryable" bool NOT NULL DEFAULT false;
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_results.retryable 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_results"
        ADD COLUMN "retry_count" int4 NOT NULL DEFAULT 0;
EXCEPTION WHEN duplicate_column THEN RAISE NOTICE '列 ai_annotation_results.retry_count 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_results"
        ADD CONSTRAINT "ai_annotation_results_retry_count_check"
            CHECK (retry_count >= 0);
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE '约束 ai_annotation_results_retry_count_check 已存在，跳过'; END $$;

DO $$ BEGIN
    ALTER TABLE "public"."ai_annotation_results"
        ADD CONSTRAINT "ai_annotation_results_failure_stage_check"
            CHECK (
                failure_stage IS NULL
                OR failure_stage::text = ANY (
                    ARRAY[
                        'model_request',
                        'model_output',
                        'lease',
                        'worker_runtime',
                        'backend_request',
                        'batch_dispatch',
                        'unknown'
                    ]::text[]
                )
            );
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE '约束 ai_annotation_results_failure_stage_check 已存在，跳过'; END $$;

CREATE INDEX IF NOT EXISTS "idx_ai_annotation_results_batch_retryable_failed"
    ON "public"."ai_annotation_results" ("batch_id", "retryable", "created_at")
    WHERE status::text = 'failed'::text;

COMMENT ON COLUMN "public"."ai_annotation_results"."failure_code" IS '当前最后一次结果失败的稳定代码';
COMMENT ON COLUMN "public"."ai_annotation_results"."failure_stage" IS '当前最后一次结果失败阶段';
COMMENT ON COLUMN "public"."ai_annotation_results"."failed_at" IS '当前最后一次结果失败时间';
COMMENT ON COLUMN "public"."ai_annotation_results"."retryable" IS '当前失败是否允许提供方发起业务重试';
COMMENT ON COLUMN "public"."ai_annotation_results"."retry_count" IS '提供方已发起的业务重试轮次';

-- 3. 批量重试请求。该表先于失败历史创建，供失败历史引用请求 ID。
CREATE TABLE IF NOT EXISTS "public"."ai_annotation_retry_requests" (
    "id" uuid NOT NULL,
    "batch_id" uuid NOT NULL,
    "provider_id" uuid NOT NULL,
    "scope" varchar(24) NOT NULL,
    "requested_count" int4 NOT NULL DEFAULT 0,
    "retried_count" int4 NOT NULL DEFAULT 0,
    "skipped_count" int4 NOT NULL DEFAULT 0,
    "status" varchar(24) NOT NULL DEFAULT 'prepared',
    "comment" text,
    "error_message" text,
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "ai_annotation_retry_requests_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ai_annotation_retry_requests_batch_id_fkey"
        FOREIGN KEY ("batch_id") REFERENCES "public"."ai_annotation_batches" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_retry_requests_provider_id_fkey"
        FOREIGN KEY ("provider_id") REFERENCES "public"."users" ("id"),
    CONSTRAINT "ai_annotation_retry_requests_scope_check"
        CHECK (scope::text = ANY (ARRAY['selected', 'all_failed']::text[])),
    CONSTRAINT "ai_annotation_retry_requests_status_check"
        CHECK (status::text = ANY (ARRAY['prepared', 'dispatched', 'dispatch_failed']::text[])),
    CONSTRAINT "ai_annotation_retry_requests_counts_check"
        CHECK (
            requested_count >= 0
            AND retried_count >= 0
            AND skipped_count >= 0
            AND retried_count + skipped_count <= requested_count
        )
);

CREATE INDEX IF NOT EXISTS "idx_ai_annotation_retry_requests_batch_created_at"
    ON "public"."ai_annotation_retry_requests" ("batch_id", "created_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_ai_annotation_retry_requests_provider_created_at"
    ON "public"."ai_annotation_retry_requests" ("provider_id", "created_at" DESC);

COMMENT ON TABLE "public"."ai_annotation_retry_requests" IS '提供方批量重试失败 AI 标注结果的幂等请求与派发记录';
COMMENT ON COLUMN "public"."ai_annotation_retry_requests"."id" IS '前端生成的幂等请求 UUID';
COMMENT ON COLUMN "public"."ai_annotation_retry_requests"."scope" IS '重试范围：selected 或 all_failed';
COMMENT ON COLUMN "public"."ai_annotation_retry_requests"."status" IS '重试请求状态：prepared、dispatched、dispatch_failed';
COMMENT ON COLUMN "public"."ai_annotation_retry_requests"."comment" IS '提供方填写的重试说明';
COMMENT ON COLUMN "public"."ai_annotation_retry_requests"."error_message" IS 'Worker 派发失败原因';

-- 4. 追加式失败历史。失败内容不可覆盖，只允许后续补写解决信息。
CREATE TABLE IF NOT EXISTS "public"."ai_annotation_failure_records" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "batch_id" uuid NOT NULL,
    "result_id" uuid,
    "dataset_id" uuid NOT NULL,
    "item_id" uuid,
    "scope" varchar(16) NOT NULL,
    "failure_stage" varchar(32) NOT NULL,
    "failure_code" varchar(64) NOT NULL,
    "error_message" text NOT NULL,
    "retryable" bool NOT NULL DEFAULT false,
    "attempt_count" int4 NOT NULL DEFAULT 0,
    "retry_count" int4 NOT NULL DEFAULT 0,
    "raw_output" jsonb,
    "details" jsonb NOT NULL DEFAULT '{}'::jsonb,
    "retry_request_id" uuid,
    "retried_by" uuid,
    "retried_at" timestamptz(6),
    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
    CONSTRAINT "ai_annotation_failure_records_pkey" PRIMARY KEY ("id"),
    CONSTRAINT "ai_annotation_failure_records_batch_id_fkey"
        FOREIGN KEY ("batch_id") REFERENCES "public"."ai_annotation_batches" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_failure_records_result_id_fkey"
        FOREIGN KEY ("result_id") REFERENCES "public"."ai_annotation_results" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_failure_records_dataset_id_fkey"
        FOREIGN KEY ("dataset_id") REFERENCES "public"."datasets" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_failure_records_item_id_fkey"
        FOREIGN KEY ("item_id") REFERENCES "public"."data_items" ("id") ON DELETE CASCADE,
    CONSTRAINT "ai_annotation_failure_records_retry_request_id_fkey"
        FOREIGN KEY ("retry_request_id") REFERENCES "public"."ai_annotation_retry_requests" ("id"),
    CONSTRAINT "ai_annotation_failure_records_retried_by_fkey"
        FOREIGN KEY ("retried_by") REFERENCES "public"."users" ("id"),
    CONSTRAINT "ai_annotation_failure_records_scope_check"
        CHECK (scope::text = ANY (ARRAY['result', 'batch']::text[])),
    CONSTRAINT "ai_annotation_failure_records_failure_stage_check"
        CHECK (
            failure_stage::text = ANY (
                ARRAY[
                    'model_request',
                    'model_output',
                    'lease',
                    'worker_runtime',
                    'backend_request',
                    'batch_dispatch',
                    'unknown'
                ]::text[]
            )
        ),
    CONSTRAINT "ai_annotation_failure_records_counts_check"
        CHECK (attempt_count >= 0 AND retry_count >= 0),
    CONSTRAINT "ai_annotation_failure_records_target_check"
        CHECK (
            (scope = 'result' AND result_id IS NOT NULL AND item_id IS NOT NULL)
            OR (scope = 'batch' AND result_id IS NULL AND item_id IS NULL)
        ),
    CONSTRAINT "ai_annotation_failure_records_retry_resolution_check"
        CHECK (
            (retry_request_id IS NULL AND retried_by IS NULL AND retried_at IS NULL)
            OR (retry_request_id IS NOT NULL AND retried_by IS NOT NULL AND retried_at IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS "idx_ai_annotation_failure_records_batch_created_at"
    ON "public"."ai_annotation_failure_records" ("batch_id", "created_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_ai_annotation_failure_records_result_created_at"
    ON "public"."ai_annotation_failure_records" ("result_id", "created_at" DESC);

CREATE INDEX IF NOT EXISTS "idx_ai_annotation_failure_records_batch_retryable_retried_at"
    ON "public"."ai_annotation_failure_records" ("batch_id", "retryable", "retried_at");

CREATE INDEX IF NOT EXISTS "idx_ai_annotation_failure_records_retry_request_id"
    ON "public"."ai_annotation_failure_records" ("retry_request_id");

COMMENT ON TABLE "public"."ai_annotation_failure_records" IS '大模型标注结果级与批次级终态失败历史';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."scope" IS '失败作用域：result 或 batch';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."failure_stage" IS '失败阶段';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."failure_code" IS '稳定失败代码';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."retryable" IS '失败发生时是否允许业务重试';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."attempt_count" IS '失败发生时的 Worker 领取次数快照';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."retry_count" IS '失败发生时的业务重试轮次快照';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."raw_output" IS '输出校验失败时的模型原始输出快照';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."details" IS '仅允许保存 HTTP 状态、模型、chunkNo、requestId 和错误类型等脱敏信息';
COMMENT ON COLUMN "public"."ai_annotation_failure_records"."retry_request_id" IS '解决本次失败的批量重试请求';

-- 5. 将已有失败补充为 legacy_failure，保留原 error_message 并默认允许业务重试。
UPDATE "public"."ai_annotation_batches"
SET "failure_code" = 'legacy_failure',
    "failure_stage" = 'unknown',
    "failed_at" = COALESCE("finished_at", "updated_at", now())
WHERE "status" = 'failed'
  AND "failure_code" IS NULL;

UPDATE "public"."ai_annotation_results"
SET "failure_code" = 'legacy_failure',
    "failure_stage" = 'unknown',
    "failed_at" = COALESCE("updated_at", now()),
    "retryable" = true
WHERE "status" = 'failed'
  AND "failure_code" IS NULL;

INSERT INTO "public"."ai_annotation_failure_records" (
    "batch_id",
    "dataset_id",
    "scope",
    "failure_stage",
    "failure_code",
    "error_message",
    "retryable",
    "attempt_count",
    "retry_count",
    "details",
    "created_at"
)
SELECT
    batch."id",
    batch."dataset_id",
    'batch',
    'unknown',
    'legacy_failure',
    COALESCE(NULLIF(BTRIM(batch."error_message"), ''), '历史批次失败，未记录具体原因'),
    true,
    0,
    batch."retry_count",
    jsonb_build_object('source', 'legacy_migration'),
    COALESCE(batch."failed_at", batch."updated_at", now())
FROM "public"."ai_annotation_batches" batch
WHERE batch."status" = 'failed'
  AND NOT EXISTS (
      SELECT 1 FROM "public"."ai_annotation_failure_records" fr
      WHERE fr."batch_id" = batch."id"
        AND fr."scope" = 'batch'
        AND fr."failure_code" = 'legacy_failure'
  );

INSERT INTO "public"."ai_annotation_failure_records" (
    "batch_id",
    "result_id",
    "dataset_id",
    "item_id",
    "scope",
    "failure_stage",
    "failure_code",
    "error_message",
    "retryable",
    "attempt_count",
    "retry_count",
    "raw_output",
    "details",
    "created_at"
)
SELECT
    result."batch_id",
    result."id",
    result."dataset_id",
    result."item_id",
    'result',
    'unknown',
    'legacy_failure',
    COALESCE(NULLIF(BTRIM(result."error_message"), ''), '历史结果失败，未记录具体原因'),
    true,
    result."attempt_count",
    result."retry_count",
    result."raw_output",
    jsonb_build_object(
        'source', 'legacy_migration',
        'chunkNo', result."chunk_no",
        'requestId', result."request_id"
    ),
    COALESCE(result."failed_at", result."updated_at", now())
FROM "public"."ai_annotation_results" result
WHERE result."status" = 'failed'
  AND NOT EXISTS (
      SELECT 1 FROM "public"."ai_annotation_failure_records" fr
      WHERE fr."result_id" = result."id"
        AND fr."scope" = 'result'
        AND fr."failure_code" = 'legacy_failure'
  );

COMMIT;

-- 回滚参考：执行后会永久删除失败历史和重试请求，请先确认不需要保留审计数据。
-- 回滚前请确认当前用户是相关表的 owner 或具有 superuser 权限。
-- BEGIN;
-- DROP TABLE IF EXISTS "public"."ai_annotation_failure_records";
-- DROP TABLE IF EXISTS "public"."ai_annotation_retry_requests";
-- DROP INDEX IF EXISTS "public"."idx_ai_annotation_results_batch_retryable_failed";
-- ALTER TABLE "public"."ai_annotation_results"
--     DROP CONSTRAINT IF EXISTS "ai_annotation_results_failure_stage_check",
--     DROP CONSTRAINT IF EXISTS "ai_annotation_results_retry_count_check",
--     DROP COLUMN IF EXISTS "failure_code",
--     DROP COLUMN IF EXISTS "failure_stage",
--     DROP COLUMN IF EXISTS "failed_at",
--     DROP COLUMN IF EXISTS "retryable",
--     DROP COLUMN IF EXISTS "retry_count";
-- ALTER TABLE "public"."ai_annotation_batches"
--     DROP CONSTRAINT IF EXISTS "ai_annotation_batches_failure_stage_check",
--     DROP CONSTRAINT IF EXISTS "ai_annotation_batches_retry_count_check",
--     DROP COLUMN IF EXISTS "failure_code",
--     DROP COLUMN IF EXISTS "failure_stage",
--     DROP COLUMN IF EXISTS "failed_at",
--     DROP COLUMN IF EXISTS "retry_count";
-- COMMIT;

