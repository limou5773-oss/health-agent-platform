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
export interface WearableSource {
  sourceApp: "MI_FITNESS" | "APPLE_HEALTH" | "OTHER";
  deviceBrand: "XIAOMI" | "REDMI" | "APPLE" | "OTHER";
  deviceType: "BAND" | "WATCH" | "OTHER";
  deviceModel: string;
}
export interface SleepRecord extends WearableSource {
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
export interface ImportBatch extends WearableSource {
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
export type StepMode = "CUMULATIVE" | "DELTA";
export interface StepRecord {
  id: number;
  startTime: string;
  endTime: string;
  activityDate: string;
  steps: number;
  mode: StepMode;
  sourceName: string;
  note: string;
  createdAt: string;
}
export interface StepStatistics {
  startDate: string;
  endDate: string;
  sourceName: string | null;
  periodDays: number;
  validDays: number;
  coverageRate: number;
  totalSteps: number;
  averageSteps: number | null;
  dailyGoal: number | null;
  goalDays: number | null;
  daily: {
    date: string;
    steps: number | null;
    sourceName: string | null;
    goalRate: number | null;
  }[];
}
export interface StepImportRow {
  rowNumber: number;
  startTime: string | null;
  endTime: string | null;
  steps: string | null;
  mode: string | null;
  sourceName: string | null;
  note: string | null;
  valid: boolean;
  error: string | null;
}
export interface StepImportBatch {
  id: number;
  status: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  insertedRows: number;
  createdAt: string;
  rows?: StepImportRow[];
}
export interface StepImportPreview extends StepImportBatch {
  rows: StepImportRow[];
}
export interface Workout {
  id: number;
  exerciseType: string;
  startTime: string;
  endTime: string;
  activityDate: string;
  durationMinutes: number;
  sourceName: string;
  note: string;
  planItemId: number | null;
  planId: number | null;
  planItemTitle: string | null;
  planItemCompleted: boolean | null;
  createdAt: string;
}
export interface WorkoutStatistics {
  startDate: string;
  endDate: string;
  sourceName: string | null;
  periodDays: number;
  validDays: number;
  coverageRate: number;
  totalMinutes: number;
  recordCount: number;
  daily: { date: string; minutes: number | null; recordCount: number }[];
}
export interface ActivityPlanItem {
  id: number;
  planId: number;
  title: string;
  planTitle: string;
  date: string;
  completed: boolean;
}
export type MealType = "BREAKFAST" | "LUNCH" | "DINNER" | "SNACK";
export type PortionUnit = "G" | "ML" | "SERVING";
export type CalorieMode = "UNKNOWN" | "MANUAL" | "ESTIMATED";
export interface MealRecord {
  id: number;
  consumedAt: string;
  mealType: MealType;
  foodName: string;
  portionAmount: number | null;
  portionUnit: PortionUnit;
  calorieMode: CalorieMode;
  caloriesKcal: number | null;
  referenceAmount: number | null;
  referenceCaloriesKcal: number | null;
  nutritionSource: string;
  sourceName: string;
  note: string;
  createdAt: string;
  updatedAt: string;
}
export interface WaterRecord {
  id: number;
  consumedAt: string;
  amountMl: number;
  sourceName: string;
  note: string;
  createdAt: string;
  updatedAt: string;
}
export interface MealDailyStatistics {
  date: string;
  recordCount: number;
  knownCaloriesCount: number;
  unknownCaloriesCount: number;
  manualCaloriesCount: number;
  estimatedCaloriesCount: number;
  manualCaloriesKcal: number | null;
  estimatedCaloriesKcal: number | null;
  knownCaloriesKcal: number | null;
}
export interface MealStatistics {
  startDate: string;
  endDate: string;
  sourceName: string | null;
  periodDays: number;
  validDays: number;
  coverageRate: number;
  recordCount: number;
  knownCaloriesCount: number;
  unknownCaloriesCount: number;
  manualCaloriesCount: number;
  estimatedCaloriesCount: number;
  manualCaloriesKcal: number | null;
  estimatedCaloriesKcal: number | null;
  knownCaloriesKcal: number | null;
  dataCutoff: string | null;
  daily: MealDailyStatistics[];
}
export interface WaterStatistics {
  startDate: string;
  endDate: string;
  sourceName: string | null;
  periodDays: number;
  validDays: number;
  coverageRate: number;
  recordCount: number;
  totalMl: number;
  averageMl: number | null;
  dataCutoff: string | null;
  daily: { date: string; amountMl: number | null; recordCount: number }[];
}
export interface WeightRecord {
  id: number;
  measuredAt: string;
  weightKg: number;
  sourceName: string;
  note: string;
  createdAt: string;
  updatedAt: string;
}
export interface WeightStatistics {
  startDate: string;
  endDate: string;
  sourceName: string | null;
  periodDays: number;
  validDays: number;
  coverageRate: number;
  recordCount: number;
  averageKg: number | null;
  first: WeightRecord | null;
  last: WeightRecord | null;
  changeKg: number | null;
  dataCutoff: string | null;
  daily: {
    date: string;
    recordCount: number;
    representative: WeightRecord | null;
  }[];
}
export interface WeightGoal {
  startDate: string;
  targetDate: string;
  targetWeightKg: number;
  sourceName: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface WeightGoalProgress {
  goal: WeightGoal;
  periodDays: number;
  validDays: number;
  coverageRate: number;
  baseline: WeightRecord | null;
  current: WeightRecord | null;
  changeKg: number | null;
  differenceToTargetKg: number | null;
  progressRate: number | null;
  direction: "LOSE" | "GAIN" | "MAINTAIN" | null;
  goalReached: boolean | null;
  dataCutoff: string | null;
}
