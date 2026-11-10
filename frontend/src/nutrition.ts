import type { CalorieMode, MealType, PortionUnit } from "./types";

export const mealTypes: Record<MealType, string> = {
  BREAKFAST: "早餐",
  LUNCH: "午餐",
  DINNER: "晚餐",
  SNACK: "加餐",
};
export const portionUnits: Record<PortionUnit, string> = {
  G: "克",
  ML: "毫升",
  SERVING: "份",
};
export const calorieModes: Record<CalorieMode, string> = {
  UNKNOWN: "热量未知",
  MANUAL: "手动录入",
  ESTIMATED: "按依据估算",
};
export const nutritionNumber = (value: number, maximumFractionDigits = 2) =>
  value.toLocaleString("zh-CN", { maximumFractionDigits });
export const calorieText = (value: number | null) =>
  value === null ? "未提供" : `${nutritionNumber(value)} kcal`;
export const recordTime = (value: string) => value.replace("T", " ");
