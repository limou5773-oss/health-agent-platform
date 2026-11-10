import type { WearableSource } from "./types";

export const sourceApps = {
  MI_FITNESS: "小米运动健康（Mi Fitness）",
  APPLE_HEALTH: "苹果健康",
  OTHER: "其他 / 未指定",
};
export const deviceBrands = {
  XIAOMI: "小米",
  REDMI: "红米 / Redmi",
  APPLE: "苹果",
  OTHER: "其他 / 未指定",
};
export const deviceTypes = {
  BAND: "手环",
  WATCH: "手表",
  OTHER: "其他 / 未指定",
};

export function wearableLabel(source: WearableSource): string {
  return (
    [
      source.sourceApp === "OTHER" ? "" : sourceApps[source.sourceApp],
      source.deviceBrand === "OTHER" ? "" : deviceBrands[source.deviceBrand],
      source.deviceType === "OTHER" ? "" : deviceTypes[source.deviceType],
      source.deviceModel,
    ]
      .filter(Boolean)
      .join(" · ") || "未指定设备"
  );
}
