import { z } from 'zod';

export const annotationOptionSchema = z.object({
  value: z.string().min(1),
  label: z.string().min(1),
  hasSubOptions: z.boolean().optional(),
  subSelectionMode: z.enum(['single', 'multiple']).optional(),
  subOptions: z.array(z.object({ value: z.string().min(1), label: z.string().min(1) })).optional(),
});

export const annotationSchemaSchema = z.object({
  version: z.number().optional(),
  type: z.literal('classification'),
  selectionMode: z.enum(['single', 'multiple']),
  options: z.array(annotationOptionSchema).min(2),
});

export const workItemSchema = z.object({
  resultId: z.uuid(),
  itemId: z.uuid(),
  roundNo: z.number().int().positive(),
  content: z.string(),
  contentType: z.literal('text'),
  metadata: z.record(z.string(), z.unknown()),
});

export const workResponseSchema = z.object({
  batchId: z.uuid(),
  datasetId: z.uuid(),
  modelName: z.enum(['deepseek-v4-flash', 'deepseek-v4-pro']),
  promptVersion: z.string(),
  annotationGuide: z.string().nullable(),
  annotationSchema: annotationSchemaSchema,
  config: z.record(z.string(), z.unknown()),
  items: z.array(workItemSchema),
});

export type AnnotationSchema = z.infer<typeof annotationSchemaSchema>;
export type WorkItem = z.infer<typeof workItemSchema>;
export type WorkResponse = z.infer<typeof workResponseSchema>;

export interface UploadResultItem {
  resultId: string;
  itemId: string;
  roundNo: number;
  result?: unknown;
  confidence?: 'high' | 'medium' | 'low';
  confidenceScore?: number;
  reason?: string;
  needsHumanReview?: boolean;
  rawOutput?: unknown;
  errorMessage?: string;
}

export interface UploadResultsRequest {
  requestId: string;
  chunkNo: number;
  modelRequestCount: number;
  promptTokens: number;
  completionTokens: number;
  items: UploadResultItem[];
}
