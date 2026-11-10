export const weightNumber = (value: number, maximumFractionDigits = 3) =>
  value.toLocaleString("zh-CN", { maximumFractionDigits });

export const weightText = (value: number | null) =>
  value === null ? "未计算" : `${weightNumber(value)} kg`;

export const signedWeight = (value: number | null) =>
  value === null
    ? "未计算"
    : `${value > 0 ? "+" : ""}${weightNumber(value)} kg`;

export const weightTime = (value: string) => value.replace("T", " ");
