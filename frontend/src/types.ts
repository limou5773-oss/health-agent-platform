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
  aiSleep: boolean;
  aiActivity: boolean;
  aiWeight: boolean;
  aiHeartRate: boolean;
  aiDiary: boolean;
}
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}
export interface HeartRateSample {
  id: number;
  measuredAt: string;
  bpm: number;
  sourceName: string;
  note: string;
  corrected: boolean;
}
export interface HeartRateStatistics {
  periodDays: number;
  validDays: number;
  recordCount: number;
  averageBpm: number | null;
  dataCutoff: string | null;
  daily: { date: string; recordCount: number; averageBpm: number | null }[];
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
  sources?: string[] | null;
  periodDays: number;
  validDays: number;
  coverageRate: number;
  averageHours: number | null;
  totalHours: number;
  recordCount: number;
  dataCutoff: string | null;
  completion?: { scheduled: number; completed: number };
  completionRate?: number | null;
  scheduleVariation?: {
    comparedDays: number;
    averageStartChangeMinutes: number;
    averageEndChangeMinutes: number;
  } | null;
  daily: {
    date: string;
    hours: number | null;
    recordCount: number;
    firstStartTime?: string | null;
    lastEndTime?: string | null;
  }[];
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
  firstStartTime: string | null;
  lastEndTime: string | null;
}
export interface Article {
  id: number;
  category: keyof typeof import("./knowledge").knowledgeCategories;
  favorite: boolean;
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
export interface CombinedStatistics {
  startDate: string;
  endDate: string;
  periodDays: number;
  metrics: {
    key: "SLEEP" | "STEPS" | "WORKOUT" | "WEIGHT" | "HEART_RATE";
    unit: string;
    basis: string;
    validDays: number | null;
    recordCount: number | null;
    sources: string[];
    daily: { date: string; value: number | null }[];
    issue: string | null;
  }[];
  diaryIds: number[];
}

export interface TaskDetail {
  id: string;
  parentTaskId: string | null;
  feedback: "HELPFUL" | "NOT_HELPFUL" | null;
  templateId: number | null;
  templateVersion: number | null;
  kind: "ANALYZE" | "PLAN" | "REPORT" | "COMBINED";
  goal: string;
  status: TaskStatus;
  startDate: string;
  endDate: string;
  planStartDate: string | null;
  planEndDate: string | null;
  sourceName: string | null;
  includeDiary: boolean;
  answer: string | null;
  question: string | null;
  error: string | null;
  retryable: boolean;
  statistics: SleepStatistics | CombinedStatistics | null;
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
  version: number;
  title: string;
  reason: string;
  startDate: string;
  endDate: string;
  status: "ACTIVE" | "PAUSED" | "ENDED";
  createdAt: string;
  progress?: {
    today: string;
    dueItems: number;
    completedItems: number;
    completionRate: number | null;
    currentStreak: number;
    longestStreak: number;
  };
  items?: (DraftItem & {
    id: number;
    reminderTime: string | null;
    completed: boolean;
    checkedAt: string | null;
    missedReason: string;
  })[];
}
export interface HealthSnapshot {
  periodDays: number;
  metrics: {
    key: import("./report-metrics").ReportMetric;
    basis: string;
    validDays: number;
    value: number | null;
    sources: string[];
    recordCount?: number | null;
    manualCaloriesCount: number;
    estimatedCaloriesCount: number;
    unknownCaloriesCount: number;
  }[];
  completion: { scheduled: number; completed: number };
  completionRate: number | null;
}
interface ReportMetadata {
  id: number;
  taskId: string | null;
  reportVersion: number;
  comparisonStartDate?: string | null;
  comparisonEndDate?: string | null;
  title: string;
  startDate: string;
  endDate: string;
  createdAt: string;
  sourceDeleted: boolean;
  sourceChange: "CORRECTED" | "DELETED" | "CHECKIN_REVOKED" | "DIARY_CHANGED" | null;
  includeDiary: boolean;
  content?: string;
  references?: {
    id: number;
    title: string;
    sourceName: string;
    sourceUrl: string;
    updatedAt?: string | null;
  }[];
}
export interface FocusedSnapshot {
  metric: import("./report-metrics").ReportMetric;
  sourceName: string | null;
  current: FocusedPeriod;
  comparison: FocusedPeriod | null;
  difference: number | null;
}
interface FocusedPeriod {
  statistics: {
    startDate: string; endDate: string; validDays: number; periodDays: number;
    average: number | null; missingDates: string[]; recordCount?: number | null;
  };
  sources: string[];
  completion: { scheduled: number; completed: number };
  completionRate: number | null;
}
export type Report = ReportMetadata & (
  | { reportType: "SLEEP"; statistics?: SleepStatistics | null }
  | { reportType: "HEALTH_WEEK" | "HEALTH_MONTH"; statistics?: HealthSnapshot | null }
  | { reportType: "HEALTH_FOCUSED" | "HEALTH_COMPARISON"; statistics?: FocusedSnapshot | null }
);
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
  corrected: boolean;
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
  dataCutoff: string | null;
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
  importFormat: "CSV" | "APPLE_HEALTH";
  totalRows: number;
  skippedRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  insertedRows: number;
  createdAt: string;
  rows?: StepImportRow[];
}
export interface StepImportPreview extends StepImportBatch {
  rows: StepImportRow[];
  firstStartTime: string | null;
  lastEndTime: string | null;
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
  corrected: boolean;
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
export interface DiaryRecord {
  id: number;
  recordedAt: string;
  mood: string;
  stressLevel: number | null;
  bodyFeeling: string;
  events: string;
  tags: string;
  note: string;
  sourceName: string;
  agentAllowed: boolean;
  createdAt: string;
  updatedAt: string;
}
