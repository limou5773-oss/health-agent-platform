export const reportMetrics = {
  SLEEP: ["睡眠时长", "小时"], STEPS: ["步数", "步"],
  WORKOUT: ["运动时长", "分钟"], MEAL: ["饮食热量", "千卡"],
  WATER: ["饮水量", "毫升"], WEIGHT: ["体重", "千克"],
} as const;

export type ReportMetric = keyof typeof reportMetrics;
