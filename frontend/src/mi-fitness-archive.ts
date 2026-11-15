import { BlobReader, ERR_BAD_FORMAT, ERR_INVALID_PASSWORD, ZipReader, type FileEntry } from "@zip.js/zip.js";

const CSV_NAME = "hlth_center_fitness_data.csv";
const CSV_LIMIT = 10 * 1024 * 1024;
const ZIP_LIMIT = 100 * 1024 * 1024;

export async function extractMiFitnessCsv(file: File, password: string): Promise<File> {
  if (!file.size || file.size > ZIP_LIMIT) throw new Error("Mi Fitness ZIP 不能为空且不能超过 100 MB。");

  const archive = new ZipReader(new BlobReader(file));
  try {
    let target: FileEntry | undefined;
    let entries = 0;
    for await (const entry of archive.getEntriesGenerator()) {
      if (++entries > 10_000) throw new Error("Mi Fitness ZIP 最多包含 10,000 个条目。");
      if (entry.directory || entry.symlink) continue;
      const name = entry.filename.slice(entry.filename.lastIndexOf("/") + 1);
      if (name !== CSV_NAME && !/^\d{8}_\d+_MiFitness_hlth_center_fitness_data\.csv$/i.test(name)) continue;
      if (target) throw new Error("ZIP 中存在多个健康明细 CSV，请单独选择要导入的 CSV。");
      target = entry;
    }
    if (!target) throw new Error("ZIP 中未找到 Mi Fitness 健康明细 CSV。");
    if (target.encrypted && !password) throw new Error("请填写该 ZIP 的解压密码。");
    if (target.uncompressedSize > CSV_LIMIT) throw new Error("ZIP 中的明细 CSV 不能超过 10 MB。");

    const chunks: BlobPart[] = [];
    let bytes = 0;
    await target.getData(new WritableStream<Uint8Array>({
      write(chunk) {
        bytes += chunk.byteLength;
        if (bytes > CSV_LIMIT) throw new Error("ZIP 中的明细 CSV 不能超过 10 MB。");
        chunks.push(Uint8Array.from(chunk));
      },
    }), { password: password || undefined, checkCrc32: true, checkAuthenticationCode: true });
    return new File(chunks, CSV_NAME, { type: "text/csv" });
  } catch (cause) {
    if (cause instanceof Error && cause.message === ERR_INVALID_PASSWORD)
      throw new Error("ZIP 解压密码不正确。", { cause });
    if (cause instanceof Error && cause.message === ERR_BAD_FORMAT)
      throw new Error("无法读取 ZIP，请检查文件是否完整。", { cause });
    throw cause;
  } finally {
    await archive.close();
  }
}
