import { api, saveBlob } from "./api";

export const importKindText = { SLEEP: "睡眠", STEPS: "步数", WEIGHT: "体重", HEART_RATE: "心率" } as const;
export type ImportKind = keyof typeof importKindText;
export type ImportTemplate = { kind: ImportKind; headers: string[]; instructions: string };

export function downloadImportTemplate(template: ImportTemplate) {
  const file = new Blob(["\uFEFF", template.headers.join(","), "\r\n"], { type: "text/csv;charset=utf-8" });
  saveBlob(file, `${importKindText[template.kind]}记录模板.csv`);
}

export function getImportTemplate(kind: ImportKind) {
  return api<ImportTemplate>(`/import-templates/${kind}`);
}
