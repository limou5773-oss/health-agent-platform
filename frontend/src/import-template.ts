import { api, saveBlob } from "./api";

export type ImportKind = "SLEEP" | "STEPS";
export type ImportTemplate = { kind: ImportKind; headers: string[]; instructions: string };

export function downloadImportTemplate(template: ImportTemplate) {
  const file = new Blob(["\uFEFF", template.headers.join(","), "\r\n"], { type: "text/csv;charset=utf-8" });
  saveBlob(file, template.kind === "SLEEP" ? "睡眠记录模板.csv" : "步数记录模板.csv");
}

export function getImportTemplate(kind: ImportKind) {
  return api<ImportTemplate>(`/import-templates/${kind}`);
}
