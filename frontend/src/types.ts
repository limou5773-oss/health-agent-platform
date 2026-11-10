export interface User {
  id: number;
  username: string;
  nickname: string;
  role: "USER" | "ADMIN";
}
export interface Profile {
  nickname: string;
  heightCm: number | null;
  weightKg: number | null;
  timezone: string;
  goal: string;
  preferences: string;
  aiConsent: boolean;
}
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}
export interface SleepRecord {
  id: number;
  startTime: string;
  endTime: string;
  sleepDate: string;
  durationMinutes: number;
  sourceName: string;
  note: string;
  corrected: boolean;
  createdAt: string;
}
export interface SleepStatistics {
  startDate: string;
  endDate: string;
  sourceName: string | null;
  periodDays: number;
  validDays: number;
  coverageRate: number;
  averageHours: number | null;
  totalHours: number;
  recordCount: number;
  dataCutoff: string | null;
  daily: { date: string; hours: number | null; recordCount: number }[];
}
export interface ImportRow {
  rowNumber: number;
  startTime: string | null;
  endTime: string | null;
  sleepDate: string | null;
  sourceName: string | null;
  note: string | null;
  valid: boolean;
  error: string | null;
}
export interface ImportBatch {
  id: number;
  format: "CSV" | "APPLE_HEALTH";
  status: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  skippedRows: number;
  unsupportedRows: number;
  insertedRows?: number;
  createdAt?: string;
}
export interface ImportPreview extends ImportBatch {
  rows: ImportRow[];
}
export interface Article {
  id: number;
  title: string;
  content: string;
  sourceName: string;
  sourceUrl: string;
  updatedAt: string;
  published: boolean;
}
export interface DraftItem {
  date: string;
  time: string;
  title: string;
  description: string;
}
export interface PlanDraft {
  version: number;
  title: string;
  reason: string;
  startDate: string;
  endDate: string;
  items: DraftItem[];
}
export type TaskStatus =
  | "QUEUED"
  | "RUNNING"
  | "WAITING_INPUT"
  | "WAITING_CONFIRMATION"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";
export interface TaskDetail {
  id: string;
  kind: "ANALYZE" | "PLAN" | "REPORT";
  goal: string;
  status: TaskStatus;
  startDate: string;
  endDate: string;
  planStartDate: string | null;
  planEndDate: string | null;
  sourceName: string | null;
  answer: string | null;
  question: string | null;
  error: string | null;
  retryable: boolean;
  statistics: SleepStatistics | null;
  draft: PlanDraft | null;
  planId: number | null;
  reportId: number | null;
  steps: {
    id: number;
    name: string;
    status: string;
    inputSummary: string;
    outputSummary: string;
    createdAt: string;
  }[];
  createdAt: string;
}
export interface Plan {
  id: number;
  title: string;
  reason: string;
  startDate: string;
  endDate: string;
  status: "ACTIVE" | "PAUSED" | "ENDED";
  createdAt: string;
  items?: (DraftItem & {
    id: number;
    completed: boolean;
    checkedAt: string | null;
  })[];
}
export interface Report {
  id: number;
  title: string;
  startDate: string;
  endDate: string;
  createdAt: string;
  sourceDeleted: boolean;
  content?: string;
  statistics?: SleepStatistics | null;
  references?: {
    id: number;
    title: string;
    sourceName: string;
    sourceUrl: string;
  }[];
}
export interface AdminUser extends User {
  enabled: boolean;
  createdAt: string;
}
export interface AdminTask {
  id: string;
  status: TaskStatus;
  kind: string;
  startDate: string;
  endDate: string;
  createdAt?: string;
  durationMs?: number | null;
  error?: string | null;
}
