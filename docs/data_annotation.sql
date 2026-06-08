/*
 Navicat Premium Dump SQL

 Source Server         : 我的PGSQL
 Source Server Type    : PostgreSQL
 Source Server Version : 140023 (140023)
 Source Host           : 115.29.239.114:5432
 Source Catalog        : data_annotation
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 140023 (140023)
 File Encoding         : 65001

 Date: 01/06/2026 11:14:08
*/


-- ----------------------------
-- Table structure for annotation_task_batches
-- ----------------------------
DROP TABLE IF EXISTS "public"."annotation_task_batches";
CREATE TABLE "public"."annotation_task_batches" (
                                                    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
                                                    "order_no" varchar(40) COLLATE "pg_catalog"."default" NOT NULL,
                                                    "dataset_id" uuid NOT NULL,
                                                    "annotator_id" uuid NOT NULL,
                                                    "status" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'assigned'::character varying,
                                                    "total_count" int4 NOT NULL DEFAULT 0,
                                                    "assigned_at" timestamptz(6) NOT NULL DEFAULT now(),
                                                    "started_at" timestamptz(6),
                                                    "submitted_at" timestamptz(6),
                                                    "due_at" timestamptz(6),
                                                    "created_at" timestamptz(6) NOT NULL DEFAULT now(),
                                                    "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
                                                    "batch_type" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'annotation'::character varying
)
;
COMMENT ON COLUMN "public"."annotation_task_batches"."batch_type" IS '任务单类别：annotation 标注任务，review 互查任务';

-- ----------------------------
-- Table structure for annotation_tasks
-- ----------------------------
DROP TABLE IF EXISTS "public"."annotation_tasks";
CREATE TABLE "public"."annotation_tasks" (
                                             "id" uuid NOT NULL DEFAULT gen_random_uuid(),
                                             "dataset_id" uuid NOT NULL,
                                             "item_id" uuid NOT NULL,
                                             "annotator_id" uuid NOT NULL,
                                             "status" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'assigned'::character varying,
                                             "assigned_at" timestamptz(6) NOT NULL DEFAULT now(),
                                             "started_at" timestamptz(6),
                                             "submitted_at" timestamptz(6),
                                             "due_at" timestamptz(6),
                                             "created_at" timestamptz(6) NOT NULL DEFAULT now(),
                                             "updated_at" timestamptz(6) NOT NULL DEFAULT now(),
                                             "batch_id" uuid NOT NULL
)
;
COMMENT ON COLUMN "public"."annotation_tasks"."id" IS '任务 ID';
COMMENT ON COLUMN "public"."annotation_tasks"."dataset_id" IS '所属数据集 ID';
COMMENT ON COLUMN "public"."annotation_tasks"."item_id" IS '待标注数据项 ID';
COMMENT ON COLUMN "public"."annotation_tasks"."annotator_id" IS '标注员用户 ID';
COMMENT ON COLUMN "public"."annotation_tasks"."status" IS '任务状态：assigned、in_progress、submitted、returned、accepted、cancelled';
COMMENT ON COLUMN "public"."annotation_tasks"."assigned_at" IS '分配时间';
COMMENT ON COLUMN "public"."annotation_tasks"."started_at" IS '开始标注时间';
COMMENT ON COLUMN "public"."annotation_tasks"."submitted_at" IS '提交时间';
COMMENT ON COLUMN "public"."annotation_tasks"."due_at" IS '任务截止时间';
COMMENT ON COLUMN "public"."annotation_tasks"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."annotation_tasks"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."annotation_tasks" IS '标注任务分配表，记录数据项分配给标注员后的执行状态';

-- ----------------------------
-- Table structure for annotations
-- ----------------------------
DROP TABLE IF EXISTS "public"."annotations";
CREATE TABLE "public"."annotations" (
                                        "id" uuid NOT NULL DEFAULT gen_random_uuid(),
                                        "task_id" uuid NOT NULL,
                                        "item_id" uuid NOT NULL,
                                        "annotator_id" uuid NOT NULL,
                                        "result" jsonb NOT NULL,
                                        "annotation_type" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'annotation'::character varying,
                                        "review_of_annotation_id" uuid,
                                        "comment" text COLLATE "pg_catalog"."default",
                                        "is_disputed" bool NOT NULL DEFAULT false,
                                        "status" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'submitted'::character varying,
                                        "adoption_status" int2 NOT NULL DEFAULT 0,
                                        "adopted_at" timestamptz(6),
                                        "adopted_by" uuid,
                                        "adoption_comment" text COLLATE "pg_catalog"."default",
                                        "submitted_at" timestamptz(6) NOT NULL DEFAULT now(),
                                        "reviewed_at" timestamptz(6),
                                        "created_at" timestamptz(6) NOT NULL DEFAULT now(),
                                        "updated_at" timestamptz(6) NOT NULL DEFAULT now()
)
;
COMMENT ON COLUMN "public"."annotations"."id" IS '标注结果 ID';
COMMENT ON COLUMN "public"."annotations"."task_id" IS '所属标注任务 ID';
COMMENT ON COLUMN "public"."annotations"."item_id" IS '被标注的数据项 ID';
COMMENT ON COLUMN "public"."annotations"."annotator_id" IS '提交标注结果的标注员 ID';
COMMENT ON COLUMN "public"."annotations"."result" IS '当前记录提交人的标注结果，使用 JSONB 保存';
COMMENT ON COLUMN "public"."annotations"."annotation_type" IS '标注结果类别：annotation 原始标注，review 互查复核';
COMMENT ON COLUMN "public"."annotations"."review_of_annotation_id" IS '互查复核对应的原始标注结果 ID，仅 annotation_type=review 时使用';
COMMENT ON COLUMN "public"."annotations"."comment" IS '标注员备注';
COMMENT ON COLUMN "public"."annotations"."is_disputed" IS '是否存在争议';
COMMENT ON COLUMN "public"."annotations"."status" IS '标注结果状态：submitted、returned、accepted、rejected';
COMMENT ON COLUMN "public"."annotations"."adoption_status" IS '采纳状态：0 未处理、1 已采纳、2 已拒绝';
COMMENT ON COLUMN "public"."annotations"."adopted_at" IS '采纳时间';
COMMENT ON COLUMN "public"."annotations"."adopted_by" IS '执行采纳的提供方用户 ID';
COMMENT ON COLUMN "public"."annotations"."adoption_comment" IS '采纳/拒绝说明';
COMMENT ON COLUMN "public"."annotations"."submitted_at" IS '提交时间';
COMMENT ON COLUMN "public"."annotations"."reviewed_at" IS '审核时间';
COMMENT ON COLUMN "public"."annotations"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."annotations"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."annotations" IS '标注结果表，保存标注内容、争议标记和提交信息';

-- ----------------------------
-- Table structure for data_items
-- ----------------------------
DROP TABLE IF EXISTS "public"."data_items";
CREATE TABLE "public"."data_items" (
                                       "id" uuid NOT NULL DEFAULT gen_random_uuid(),
                                       "dataset_id" uuid NOT NULL,
                                       "content" text COLLATE "pg_catalog"."default" NOT NULL,
                                       "content_type" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'text'::character varying,
                                       "metadata" jsonb NOT NULL DEFAULT '{}'::jsonb,
                                       "final_result" jsonb,
                                       "finalized_at" timestamptz(6),
                                       "finalized_by" uuid,
                                       "status" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'pending'::character varying,
                                       "created_at" timestamptz(6) NOT NULL DEFAULT now(),
                                       "updated_at" timestamptz(6) NOT NULL DEFAULT now()
)
;
COMMENT ON COLUMN "public"."data_items"."id" IS '数据项 ID';
COMMENT ON COLUMN "public"."data_items"."dataset_id" IS '所属数据集 ID';
COMMENT ON COLUMN "public"."data_items"."content" IS '数据内容或资源地址';
COMMENT ON COLUMN "public"."data_items"."content_type" IS '内容类型：text、image、audio、video、json';
COMMENT ON COLUMN "public"."data_items"."metadata" IS '数据项扩展信息，例如来源、文件名、尺寸、语言、导入批次等，不保存标注规则';
COMMENT ON COLUMN "public"."data_items"."final_result" IS '争议裁决后的最终标注结果';
COMMENT ON COLUMN "public"."data_items"."finalized_at" IS '最终结果确认时间';
COMMENT ON COLUMN "public"."data_items"."finalized_by" IS '确认最终结果的提供方用户 ID';
COMMENT ON COLUMN "public"."data_items"."status" IS '数据项状态：pending、assigned、annotated、disputed、accepted、rejected';
COMMENT ON COLUMN "public"."data_items"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."data_items"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."data_items" IS '数据项明细表，每一行代表一个待标注样本';

-- ----------------------------
-- Table structure for dataset_reviews
-- ----------------------------
DROP TABLE IF EXISTS "public"."dataset_reviews";
CREATE TABLE "public"."dataset_reviews" (
                                            "id" uuid NOT NULL DEFAULT gen_random_uuid(),
                                            "dataset_id" uuid NOT NULL,
                                            "provider_id" uuid NOT NULL,
                                            "status" varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
                                            "sampled_item_count" int4 NOT NULL DEFAULT 0,
                                            "disputed_item_count" int4 NOT NULL DEFAULT 0,
                                            "opinion" text COLLATE "pg_catalog"."default",
                                            "reviewed_at" timestamptz(6),
                                            "created_at" timestamptz(6) NOT NULL DEFAULT now(),
                                            "updated_at" timestamptz(6) NOT NULL DEFAULT now()
)
;
COMMENT ON COLUMN "public"."dataset_reviews"."id" IS '审核记录 ID';
COMMENT ON COLUMN "public"."dataset_reviews"."dataset_id" IS '被审核的数据集 ID';
COMMENT ON COLUMN "public"."dataset_reviews"."provider_id" IS '执行审核的数据集提供者用户 ID';
COMMENT ON COLUMN "public"."dataset_reviews"."status" IS '审核状态：pending、approved、revision_required、rejected';
COMMENT ON COLUMN "public"."dataset_reviews"."sampled_item_count" IS '抽样审核的数据项数量';
COMMENT ON COLUMN "public"."dataset_reviews"."disputed_item_count" IS '存在争议的数据项数量';
COMMENT ON COLUMN "public"."dataset_reviews"."opinion" IS '审核意见';
COMMENT ON COLUMN "public"."dataset_reviews"."reviewed_at" IS '审核完成时间';
COMMENT ON COLUMN "public"."dataset_reviews"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."dataset_reviews"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."dataset_reviews" IS '数据集审核表，由数据集提供方记录抽样审核、退回和完成意见';

-- ----------------------------
-- Table structure for datasets
-- ----------------------------
DROP TABLE IF EXISTS "public"."datasets";
CREATE TABLE "public"."datasets" (
                                     "id" uuid NOT NULL DEFAULT gen_random_uuid(),
                                     "provider_id" uuid NOT NULL,
                                     "name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
                                     "description" text COLLATE "pg_catalog"."default",
                                     "annotation_guide" text COLLATE "pg_catalog"."default",
                                     "annotation_schema" jsonb NOT NULL DEFAULT '{}'::jsonb,
                                     "status" varchar(32) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'draft'::character varying,
                                     "target_completion_ratio" numeric(5,2) NOT NULL DEFAULT 50.00,
                                     "item_count" int4 NOT NULL DEFAULT 0,
                                     "completed_item_count" int4 NOT NULL DEFAULT 0,
                                     "created_at" timestamptz(6) NOT NULL DEFAULT now(),
                                     "updated_at" timestamptz(6) NOT NULL DEFAULT now()
)
;
COMMENT ON COLUMN "public"."datasets"."id" IS '数据集 ID';
COMMENT ON COLUMN "public"."datasets"."provider_id" IS '数据集提供者用户 ID';
COMMENT ON COLUMN "public"."datasets"."name" IS '数据集名称';
COMMENT ON COLUMN "public"."datasets"."description" IS '数据集描述';
COMMENT ON COLUMN "public"."datasets"."annotation_guide" IS '标注文档或标注说明';
COMMENT ON COLUMN "public"."datasets"."annotation_schema" IS '标注配置结构，定义标注类型、字段、选项、校验规则和结果格式，使用 JSONB 保存';
COMMENT ON COLUMN "public"."datasets"."status" IS '数据集状态：draft、in_progress、reviewing、completed';
COMMENT ON COLUMN "public"."datasets"."target_completion_ratio" IS '触发审核的目标完成比例，默认 50%';
COMMENT ON COLUMN "public"."datasets"."item_count" IS '数据项总数';
COMMENT ON COLUMN "public"."datasets"."completed_item_count" IS '已完成数据项数量';
COMMENT ON COLUMN "public"."datasets"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."datasets"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."datasets" IS '数据集主表，记录上传方、标注说明、标注配置结构和整体状态';

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS "public"."users";
CREATE TABLE "public"."users" (
                                  "id" uuid NOT NULL DEFAULT gen_random_uuid(),
                                  "username" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
                                  "password_hash" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
                                  "display_name" varchar(80) COLLATE "pg_catalog"."default" NOT NULL,
                                  "role" varchar(24) COLLATE "pg_catalog"."default" NOT NULL,
                                  "status" varchar(24) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'active'::character varying,
                                  "created_at" timestamptz(6) NOT NULL DEFAULT now(),
                                  "updated_at" timestamptz(6) NOT NULL DEFAULT now()
)
;
COMMENT ON COLUMN "public"."users"."id" IS '用户 ID';
COMMENT ON COLUMN "public"."users"."username" IS '登录用户名，全局唯一';
COMMENT ON COLUMN "public"."users"."password_hash" IS '密码哈希值，不保存明文密码';
COMMENT ON COLUMN "public"."users"."display_name" IS '用户显示名称';
COMMENT ON COLUMN "public"."users"."role" IS '用户角色：provider、annotator、admin';
COMMENT ON COLUMN "public"."users"."status" IS '账号状态：active、disabled';
COMMENT ON COLUMN "public"."users"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."users"."updated_at" IS '更新时间';
COMMENT ON TABLE "public"."users" IS '用户账号表，覆盖数据集提供者、标注员和管理员';

-- ----------------------------
-- Function structure for armor
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."armor"(bytea);
CREATE FUNCTION "public"."armor"(bytea)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pg_armor'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for armor
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."armor"(bytea, _text, _text);
CREATE FUNCTION "public"."armor"(bytea, _text, _text)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pg_armor'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for crypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."crypt"(text, text);
CREATE FUNCTION "public"."crypt"(text, text)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pg_crypt'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for dearmor
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."dearmor"(text);
CREATE FUNCTION "public"."dearmor"(text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_dearmor'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for decrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."decrypt"(bytea, bytea, text);
CREATE FUNCTION "public"."decrypt"(bytea, bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_decrypt'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for decrypt_iv
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."decrypt_iv"(bytea, bytea, bytea, text);
CREATE FUNCTION "public"."decrypt_iv"(bytea, bytea, bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_decrypt_iv'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for digest
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."digest"(bytea, text);
CREATE FUNCTION "public"."digest"(bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_digest'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for digest
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."digest"(text, text);
CREATE FUNCTION "public"."digest"(text, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_digest'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for encrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."encrypt"(bytea, bytea, text);
CREATE FUNCTION "public"."encrypt"(bytea, bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_encrypt'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for encrypt_iv
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."encrypt_iv"(bytea, bytea, bytea, text);
CREATE FUNCTION "public"."encrypt_iv"(bytea, bytea, bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_encrypt_iv'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gen_random_bytes
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gen_random_bytes"(int4);
CREATE FUNCTION "public"."gen_random_bytes"(int4)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_random_bytes'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gen_random_uuid
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gen_random_uuid"();
CREATE FUNCTION "public"."gen_random_uuid"()
    RETURNS "pg_catalog"."uuid" AS '$libdir/pgcrypto', 'pg_random_uuid'
  LANGUAGE c VOLATILE
  COST 1;

-- ----------------------------
-- Function structure for gen_salt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gen_salt"(text, int4);
CREATE FUNCTION "public"."gen_salt"(text, int4)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pg_gen_salt_rounds'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gen_salt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gen_salt"(text);
CREATE FUNCTION "public"."gen_salt"(text)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pg_gen_salt'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for hmac
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."hmac"(text, text, text);
CREATE FUNCTION "public"."hmac"(text, text, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_hmac'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for hmac
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."hmac"(bytea, bytea, text);
CREATE FUNCTION "public"."hmac"(bytea, bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pg_hmac'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_armor_headers
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_armor_headers"(text, OUT "key" text, OUT "value" text);
CREATE FUNCTION "public"."pgp_armor_headers"(IN text, OUT "key" text, OUT "value" text)
    RETURNS SETOF "pg_catalog"."record" AS '$libdir/pgcrypto', 'pgp_armor_headers'
  LANGUAGE c IMMUTABLE STRICT
  COST 1
  ROWS 1000;

-- ----------------------------
-- Function structure for pgp_key_id
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_key_id"(bytea);
CREATE FUNCTION "public"."pgp_key_id"(bytea)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pgp_key_id_w'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_decrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_decrypt"(bytea, bytea, text);
CREATE FUNCTION "public"."pgp_pub_decrypt"(bytea, bytea, text)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pgp_pub_decrypt_text'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_decrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_decrypt"(bytea, bytea);
CREATE FUNCTION "public"."pgp_pub_decrypt"(bytea, bytea)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pgp_pub_decrypt_text'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_decrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_decrypt"(bytea, bytea, text, text);
CREATE FUNCTION "public"."pgp_pub_decrypt"(bytea, bytea, text, text)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pgp_pub_decrypt_text'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_decrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_decrypt_bytea"(bytea, bytea, text);
CREATE FUNCTION "public"."pgp_pub_decrypt_bytea"(bytea, bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_pub_decrypt_bytea'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_decrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_decrypt_bytea"(bytea, bytea, text, text);
CREATE FUNCTION "public"."pgp_pub_decrypt_bytea"(bytea, bytea, text, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_pub_decrypt_bytea'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_decrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_decrypt_bytea"(bytea, bytea);
CREATE FUNCTION "public"."pgp_pub_decrypt_bytea"(bytea, bytea)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_pub_decrypt_bytea'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_encrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_encrypt"(text, bytea);
CREATE FUNCTION "public"."pgp_pub_encrypt"(text, bytea)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_pub_encrypt_text'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_encrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_encrypt"(text, bytea, text);
CREATE FUNCTION "public"."pgp_pub_encrypt"(text, bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_pub_encrypt_text'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_encrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_encrypt_bytea"(bytea, bytea, text);
CREATE FUNCTION "public"."pgp_pub_encrypt_bytea"(bytea, bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_pub_encrypt_bytea'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_pub_encrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_pub_encrypt_bytea"(bytea, bytea);
CREATE FUNCTION "public"."pgp_pub_encrypt_bytea"(bytea, bytea)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_pub_encrypt_bytea'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_sym_decrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_sym_decrypt"(bytea, text);
CREATE FUNCTION "public"."pgp_sym_decrypt"(bytea, text)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pgp_sym_decrypt_text'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_sym_decrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_sym_decrypt"(bytea, text, text);
CREATE FUNCTION "public"."pgp_sym_decrypt"(bytea, text, text)
    RETURNS "pg_catalog"."text" AS '$libdir/pgcrypto', 'pgp_sym_decrypt_text'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_sym_decrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_sym_decrypt_bytea"(bytea, text, text);
CREATE FUNCTION "public"."pgp_sym_decrypt_bytea"(bytea, text, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_sym_decrypt_bytea'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_sym_decrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_sym_decrypt_bytea"(bytea, text);
CREATE FUNCTION "public"."pgp_sym_decrypt_bytea"(bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_sym_decrypt_bytea'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_sym_encrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_sym_encrypt"(text, text, text);
CREATE FUNCTION "public"."pgp_sym_encrypt"(text, text, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_sym_encrypt_text'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_sym_encrypt
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_sym_encrypt"(text, text);
CREATE FUNCTION "public"."pgp_sym_encrypt"(text, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_sym_encrypt_text'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_sym_encrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_sym_encrypt_bytea"(bytea, text, text);
CREATE FUNCTION "public"."pgp_sym_encrypt_bytea"(bytea, text, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_sym_encrypt_bytea'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pgp_sym_encrypt_bytea
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pgp_sym_encrypt_bytea"(bytea, text);
CREATE FUNCTION "public"."pgp_sym_encrypt_bytea"(bytea, text)
    RETURNS "pg_catalog"."bytea" AS '$libdir/pgcrypto', 'pgp_sym_encrypt_bytea'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Indexes structure for table annotation_task_batches
-- ----------------------------
CREATE INDEX "idx_annotation_task_batches_annotator_dataset_status" ON "public"."annotation_task_batches" USING btree (
    "annotator_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "dataset_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_annotation_task_batches_annotator_status_assigned_at" ON "public"."annotation_task_batches" USING btree (
    "annotator_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "assigned_at" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
    );
CREATE INDEX "idx_annotation_task_batches_dataset_status" ON "public"."annotation_task_batches" USING btree (
    "dataset_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

-- ----------------------------
-- Uniques structure for table annotation_task_batches
-- ----------------------------
ALTER TABLE "public"."annotation_task_batches" ADD CONSTRAINT "annotation_task_batches_order_no_key" UNIQUE ("order_no");

-- ----------------------------
-- Checks structure for table annotation_task_batches
-- ----------------------------
ALTER TABLE "public"."annotation_task_batches" ADD CONSTRAINT "annotation_task_batches_status_check" CHECK (status::text = ANY (ARRAY['assigned'::character varying, 'in_progress'::character varying, 'submitted'::character varying, 'returned'::character varying, 'accepted'::character varying, 'cancelled'::character varying]::text[]));
ALTER TABLE "public"."annotation_task_batches" ADD CONSTRAINT "annotation_task_batches_total_count_check" CHECK (total_count >= 0);

-- ----------------------------
-- Primary Key structure for table annotation_task_batches
-- ----------------------------
ALTER TABLE "public"."annotation_task_batches" ADD CONSTRAINT "annotation_task_batches_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table annotation_tasks
-- ----------------------------
CREATE INDEX "idx_annotation_tasks_annotator_dataset_status" ON "public"."annotation_tasks" USING btree (
    "annotator_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "dataset_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_annotation_tasks_annotator_status_assigned_at" ON "public"."annotation_tasks" USING btree (
    "annotator_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "assigned_at" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
    );
CREATE INDEX "idx_annotation_tasks_batch_status" ON "public"."annotation_tasks" USING btree (
    "batch_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_annotation_tasks_dataset_status" ON "public"."annotation_tasks" USING btree (
    "dataset_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

-- ----------------------------
-- Uniques structure for table annotation_tasks
-- ----------------------------
ALTER TABLE "public"."annotation_tasks" ADD CONSTRAINT "annotation_tasks_item_id_annotator_id_key" UNIQUE ("item_id", "annotator_id");

-- ----------------------------
-- Checks structure for table annotation_tasks
-- ----------------------------
ALTER TABLE "public"."annotation_tasks" ADD CONSTRAINT "annotation_tasks_status_check" CHECK (status::text = ANY (ARRAY['assigned'::character varying, 'in_progress'::character varying, 'submitted'::character varying, 'returned'::character varying, 'accepted'::character varying, 'cancelled'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table annotation_tasks
-- ----------------------------
ALTER TABLE "public"."annotation_tasks" ADD CONSTRAINT "annotation_tasks_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table annotations
-- ----------------------------
CREATE INDEX "idx_annotations_disputed" ON "public"."annotations" USING btree (
    "item_id" "pg_catalog"."uuid_ops" ASC NULLS LAST
    ) WHERE is_disputed = true;
CREATE INDEX "idx_annotations_item_status" ON "public"."annotations" USING btree (
    "item_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_annotations_item_type" ON "public"."annotations" USING btree (
    "item_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "annotation_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_annotations_review_of" ON "public"."annotations" USING btree (
    "review_of_annotation_id" "pg_catalog"."uuid_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_annotations_result_gin" ON "public"."annotations" USING gin (
    "result" "pg_catalog"."jsonb_ops"
    );

-- ----------------------------
-- Uniques structure for table annotations
-- ----------------------------
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_task_id_key" UNIQUE ("task_id");

-- ----------------------------
-- Checks structure for table annotations
-- ----------------------------
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_status_check" CHECK (status::text = ANY (ARRAY['submitted'::character varying, 'returned'::character varying, 'accepted'::character varying, 'rejected'::character varying]::text[]));
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_annotation_type_check" CHECK (annotation_type::text = ANY (ARRAY['annotation'::character varying, 'review'::character varying]::text[]));
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_adoption_status_check" CHECK (adoption_status = ANY (ARRAY[0, 1, 2]));

-- ----------------------------
-- Primary Key structure for table annotations
-- ----------------------------
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table data_items
-- ----------------------------
CREATE INDEX "idx_data_items_dataset_status" ON "public"."data_items" USING btree (
    "dataset_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );
CREATE INDEX "idx_data_items_metadata_gin" ON "public"."data_items" USING gin (
    "metadata" "pg_catalog"."jsonb_ops"
    );
CREATE INDEX "idx_data_items_pending_by_dataset" ON "public"."data_items" USING btree (
    "dataset_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "created_at" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
    ) WHERE status::text = 'pending'::text;

-- ----------------------------
-- Checks structure for table data_items
-- ----------------------------
ALTER TABLE "public"."data_items" ADD CONSTRAINT "data_items_content_type_check" CHECK (content_type::text = ANY (ARRAY['text'::character varying, 'image'::character varying, 'audio'::character varying, 'video'::character varying, 'json'::character varying]::text[]));
ALTER TABLE "public"."data_items" ADD CONSTRAINT "data_items_status_check" CHECK (status::text = ANY (ARRAY['pending'::character varying, 'assigned'::character varying, 'annotated'::character varying, 'disputed'::character varying, 'accepted'::character varying, 'rejected'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table data_items
-- ----------------------------
ALTER TABLE "public"."data_items" ADD CONSTRAINT "data_items_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table dataset_reviews
-- ----------------------------
CREATE INDEX "idx_dataset_reviews_dataset_created_at" ON "public"."dataset_reviews" USING btree (
    "dataset_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "created_at" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
    );
CREATE INDEX "idx_dataset_reviews_provider_status_created_at" ON "public"."dataset_reviews" USING btree (
    "provider_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "created_at" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
    );

-- ----------------------------
-- Checks structure for table dataset_reviews
-- ----------------------------
ALTER TABLE "public"."dataset_reviews" ADD CONSTRAINT "dataset_reviews_status_check" CHECK (status::text = ANY (ARRAY['pending'::character varying, 'approved'::character varying, 'revision_required'::character varying, 'rejected'::character varying]::text[]));
ALTER TABLE "public"."dataset_reviews" ADD CONSTRAINT "dataset_reviews_sampled_item_count_check" CHECK (sampled_item_count >= 0);
ALTER TABLE "public"."dataset_reviews" ADD CONSTRAINT "dataset_reviews_disputed_item_count_check" CHECK (disputed_item_count >= 0);

-- ----------------------------
-- Primary Key structure for table dataset_reviews
-- ----------------------------
ALTER TABLE "public"."dataset_reviews" ADD CONSTRAINT "dataset_reviews_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table datasets
-- ----------------------------
CREATE INDEX "idx_datasets_provider_status_updated_at" ON "public"."datasets" USING btree (
    "provider_id" "pg_catalog"."uuid_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "updated_at" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
    );
CREATE INDEX "idx_datasets_status_created_at" ON "public"."datasets" USING btree (
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "created_at" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
    );

-- ----------------------------
-- Checks structure for table datasets
-- ----------------------------
ALTER TABLE "public"."datasets" ADD CONSTRAINT "datasets_status_check" CHECK (status::text = ANY (ARRAY['draft'::character varying, 'in_progress'::character varying, 'reviewing'::character varying, 'completed'::character varying]::text[]));
ALTER TABLE "public"."datasets" ADD CONSTRAINT "datasets_item_count_check" CHECK (item_count >= 0);
ALTER TABLE "public"."datasets" ADD CONSTRAINT "datasets_completed_item_count_check" CHECK (completed_item_count >= 0);
ALTER TABLE "public"."datasets" ADD CONSTRAINT "datasets_target_completion_ratio_check" CHECK (target_completion_ratio > 0::numeric AND target_completion_ratio <= 100::numeric);

-- ----------------------------
-- Primary Key structure for table datasets
-- ----------------------------
ALTER TABLE "public"."datasets" ADD CONSTRAINT "datasets_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table users
-- ----------------------------
CREATE INDEX "idx_users_role_status" ON "public"."users" USING btree (
    "role" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
    "status" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
    );

-- ----------------------------
-- Uniques structure for table users
-- ----------------------------
ALTER TABLE "public"."users" ADD CONSTRAINT "users_username_key" UNIQUE ("username");

-- ----------------------------
-- Checks structure for table users
-- ----------------------------
ALTER TABLE "public"."users" ADD CONSTRAINT "users_role_check" CHECK (role::text = ANY (ARRAY['provider'::character varying, 'annotator'::character varying, 'admin'::character varying]::text[]));
ALTER TABLE "public"."users" ADD CONSTRAINT "users_status_check" CHECK (status::text = ANY (ARRAY['active'::character varying, 'disabled'::character varying]::text[]));

-- ----------------------------
-- Primary Key structure for table users
-- ----------------------------
ALTER TABLE "public"."users" ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Foreign Keys structure for table annotation_task_batches
-- ----------------------------
ALTER TABLE "public"."annotation_task_batches" ADD CONSTRAINT "annotation_task_batches_annotator_id_fkey" FOREIGN KEY ("annotator_id") REFERENCES "public"."users" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE "public"."annotation_task_batches" ADD CONSTRAINT "annotation_task_batches_dataset_id_fkey" FOREIGN KEY ("dataset_id") REFERENCES "public"."datasets" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table annotation_tasks
-- ----------------------------
ALTER TABLE "public"."annotation_tasks" ADD CONSTRAINT "annotation_tasks_annotator_id_fkey" FOREIGN KEY ("annotator_id") REFERENCES "public"."users" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE "public"."annotation_tasks" ADD CONSTRAINT "annotation_tasks_batch_id_fkey" FOREIGN KEY ("batch_id") REFERENCES "public"."annotation_task_batches" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;
ALTER TABLE "public"."annotation_tasks" ADD CONSTRAINT "annotation_tasks_dataset_id_fkey" FOREIGN KEY ("dataset_id") REFERENCES "public"."datasets" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;
ALTER TABLE "public"."annotation_tasks" ADD CONSTRAINT "annotation_tasks_item_id_fkey" FOREIGN KEY ("item_id") REFERENCES "public"."data_items" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table annotations
-- ----------------------------
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_annotator_id_fkey" FOREIGN KEY ("annotator_id") REFERENCES "public"."users" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_item_id_fkey" FOREIGN KEY ("item_id") REFERENCES "public"."data_items" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_review_of_annotation_id_fkey" FOREIGN KEY ("review_of_annotation_id") REFERENCES "public"."annotations" ("id") ON DELETE SET NULL ON UPDATE NO ACTION;
ALTER TABLE "public"."annotations" ADD CONSTRAINT "annotations_task_id_fkey" FOREIGN KEY ("task_id") REFERENCES "public"."annotation_tasks" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table data_items
-- ----------------------------
ALTER TABLE "public"."data_items" ADD CONSTRAINT "data_items_dataset_id_fkey" FOREIGN KEY ("dataset_id") REFERENCES "public"."datasets" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table dataset_reviews
-- ----------------------------
ALTER TABLE "public"."dataset_reviews" ADD CONSTRAINT "dataset_reviews_dataset_id_fkey" FOREIGN KEY ("dataset_id") REFERENCES "public"."datasets" ("id") ON DELETE CASCADE ON UPDATE NO ACTION;
ALTER TABLE "public"."dataset_reviews" ADD CONSTRAINT "dataset_reviews_provider_id_fkey" FOREIGN KEY ("provider_id") REFERENCES "public"."users" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table datasets
-- ----------------------------
ALTER TABLE "public"."datasets" ADD CONSTRAINT "datasets_provider_id_fkey" FOREIGN KEY ("provider_id") REFERENCES "public"."users" ("id") ON DELETE NO ACTION ON UPDATE NO ACTION;
