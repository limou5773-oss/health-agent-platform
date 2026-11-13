import { reactive } from "vue";
import type { User } from "./types";

export const session = reactive({
  token: sessionStorage.getItem("health-token") || "",
  user: null as User | null,
  message: "",
});
export function logout(message = "") {
  sessionStorage.removeItem("health-token");
  session.token = "";
  session.user = null;
  session.message = message;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
async function request(path: string, options: RequestInit = {}, timeoutMs = 30000) {
  const headers = new Headers(options.headers);
  if (session.token) headers.set("Authorization", `Bearer ${session.token}`);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...options,
      headers,
      signal: controller.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError")
      throw new Error("请求超时，请稍后手动重试。");
    throw new Error("无法连接服务，请检查网络与后端服务。");
  } finally {
    window.clearTimeout(timeout);
  }
  if (!response.ok) {
    const payload = await response
      .json()
      .catch(() => ({
        code: "HTTP_ERROR",
        message: `请求失败（${response.status}）`,
      }));
    if (response.status === 401 && session.token)
      logout("登录已失效，请重新登录。");
    throw new ApiError(response.status, payload.code, payload.message);
  }
  return response;
}
export async function api<T>(path: string, options?: RequestInit, timeoutMs?: number): Promise<T> {
  const response = await request(path, options, timeoutMs);
  return response.status === 204 ? (undefined as T) : response.json();
}
export const json = (method: string, value?: unknown): RequestInit => ({
  method,
  ...(value === undefined ? {} : { body: JSON.stringify(value) }),
});
export async function download(path: string, filename: string) {
  const blob = await (await request(path)).blob();
  saveBlob(blob, filename);
}
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : "操作失败，请重试。";
export function dateString(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export function dateAfter(value: string, days: number) {
  if (!value) return "";
  const date = new Date(`${value}T12:00:00`);
  date.setDate(date.getDate() + days);
  return dateString(date);
}
export function recentRange() {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 6);
  return { startDate: dateString(start), endDate: dateString(end) };
}
export const statusText: Record<string, string> = {
  QUEUED: "排队中",
  RUNNING: "执行中",
  WAITING_INPUT: "等待补充",
  WAITING_CONFIRMATION: "等待确认",
  COMPLETED: "已完成",
  FAILED: "失败",
  CANCELLED: "已取消",
  ACTIVE: "进行中",
  PAUSED: "已暂停",
  ENDED: "已结束",
};
export const kindText: Record<string, string> = {
  ANALYZE: "睡眠分析",
  PLAN: "制定计划",
  REPORT: "睡眠周报",
};
export function safeLink(value: string) {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) ? url.href : undefined;
  } catch {
    return undefined;
  }
}
