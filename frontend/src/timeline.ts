import type {
  DiaryRecord,
  MealRecord,
  Plan,
  SleepRecord,
  StepRecord,
  WaterRecord,
  WeightRecord,
  Workout,
} from "./types";

interface TimelineDetails {
  SLEEP: Pick<
    SleepRecord,
    | "startTime"
    | "endTime"
    | "sleepDate"
    | "durationMinutes"
    | "sourceApp"
    | "deviceBrand"
    | "deviceType"
    | "deviceModel"
    | "corrected"
    | "note"
  >;
  STEPS: Pick<StepRecord, "startTime" | "endTime" | "steps" | "mode" | "corrected" | "note">;
  WORKOUT: Pick<
    Workout,
    | "exerciseType"
    | "startTime"
    | "endTime"
    | "durationMinutes"
    | "planItemId"
    | "note"
  >;
  MEAL: Omit<
    MealRecord,
    "id" | "consumedAt" | "sourceName" | "createdAt" | "updatedAt"
  >;
  WATER: Pick<WaterRecord, "amountMl" | "note">;
  WEIGHT: Pick<WeightRecord, "weightKg" | "corrected" | "note">;
  HEART_RATE: { bpm: number; note: string; corrected: boolean };
  DIARY: Pick<
    DiaryRecord,
    | "mood"
    | "stressLevel"
    | "bodyFeeling"
    | "events"
    | "tags"
    | "note"
    | "agentAllowed"
  >;
  CHECKIN: {
    planId: number;
    planTitle: string;
    itemTitle: string;
    scheduledDate: string;
    checkedAt: string;
  };
  PLAN_CREATED: {
    planId: number;
    planTitle: string;
    startDate: string;
    endDate: string;
    createdAt: string;
  };
  PLAN_STATUS: {
    planId: number;
    planTitle: string;
    previousStatus: Plan["status"];
    status: Plan["status"];
    changedAt: string;
  };
}

export type TimelineType = keyof TimelineDetails;

export interface TimelineEvent {
  type: TimelineType;
  id: number;
  eventDate: string;
  occurredAt: string;
  sourceName: string;
  summary: string;
}

export type TimelineEventDetail = {
  [Type in TimelineType]: TimelineEvent & {
    type: Type;
    details: TimelineDetails[Type];
  };
}[TimelineType];

export const timelineTypeText: Record<TimelineType, string> = {
  SLEEP: "睡眠",
  STEPS: "步数",
  WORKOUT: "运动",
  MEAL: "饮食",
  WATER: "饮水",
  WEIGHT: "体重",
  HEART_RATE: "心率",
  DIARY: "日记",
  CHECKIN: "计划打卡",
  PLAN_CREATED: "计划创建",
  PLAN_STATUS: "计划状态变更",
};

export function isTimelineType(value: string): value is TimelineType {
  return Object.hasOwn(timelineTypeText, value);
}
