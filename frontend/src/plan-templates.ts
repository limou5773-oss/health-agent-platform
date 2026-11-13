export const templateCategories = { SLEEP: "睡眠", EXERCISE: "运动", WATER: "饮水" } as const;

export type PlanTemplate = {
  id: number;
  version: number;
  category: keyof typeof templateCategories;
  title: string;
  instructions: string;
  days: number;
  enabled: boolean;
  items: { day: number; time: string; title: string; description: string }[];
};
