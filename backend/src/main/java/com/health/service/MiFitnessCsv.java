package com.health.service;

import com.health.api.ApiException;
import com.health.api.MiFitnessSourceRequired;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.web.multipart.MultipartFile;

final class MiFitnessCsv {
  private static final Pattern DEVICE_ID = Pattern.compile("[0-9]{1,32}");

  private MiFitnessCsv() {}

  record Entry(int number, String time, String value, String error) {}
  record Selected(int totalRows, int skippedRows, String sourceName, List<Entry> entries) {}

  static Selected select(MultipartFile file, Set<String> keys, String requestedSid, String metric) {
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ApiException(400, "INVALID_MI_FITNESS_CSV", "Mi Fitness 明细 CSV 不能为空且不能超过10MB");
    var deviceIds = new LinkedHashSet<String>();
    var entries = new ArrayList<Entry>();
    String device = requestedSid;
    int total = 0;
    int skipped = 0;
    boolean exceedsPreviewLimit = false;
    try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(),
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)))) {
      String first = reader.readLine();
      if (first == null || first.isBlank()) throw invalidHeader();
      var header = fields(first.startsWith("\uFEFF") ? first.substring(1) : first).stream()
          .map(name -> name.strip().toLowerCase(Locale.ROOT)).toList();
      int sid = header.indexOf("sid"), key = header.indexOf("key");
      int value = header.indexOf("value"), time = header.indexOf("time");
      if (sid < 0 || key < 0 || value < 0 || time < 0) throw invalidHeader();

      String line;
      int number = 1;
      while ((line = reader.readLine()) != null) {
        number++;
        if (line.isBlank()) continue;
        total++;
        var row = fields(line);
        Entry entry;
        if (row.size() != header.size()) {
          entry = new Entry(number, null, null, "CSV 列数与表头不一致");
        } else {
          if (!keys.contains(row.get(key))) {
            skipped++;
            continue;
          }
          String deviceId = row.get(sid);
          if (!DEVICE_ID.matcher(deviceId).matches()) {
            entry = new Entry(number, null, null, "缺少有效设备来源编号 Sid");
          } else {
            deviceIds.add(deviceId);
            if (device == null) device = deviceId;
            if (!device.equals(deviceId)) {
              skipped++;
              continue;
            }
            entry = new Entry(number, row.get(time), row.get(value), null);
          }
        }
        if (entries.size() == 10000) exceedsPreviewLimit = true;
        else entries.add(entry);
      }
    } catch (IOException e) {
      throw new ApiException(400, "INVALID_MI_FITNESS_CSV", "无法解析 Mi Fitness UTF-8 明细 CSV，请检查导出文件");
    }
    if (requestedSid == null && deviceIds.size() > 1)
      throw new MiFitnessSourceRequired(List.copyOf(deviceIds));
    if (requestedSid != null && !deviceIds.contains(requestedSid))
      throw new ApiException(400, "MI_FITNESS_SOURCE_NOT_FOUND", "文件不含所选来源的" + metric + "数据，请重新选择");
    if (exceedsPreviewLimit)
      throw new ApiException(400, "MI_FITNESS_ROW_LIMIT", "单批最多预览10000条" + metric + "明细");
    return new Selected(total, skipped, device == null ? null : "Mi Fitness · " + device, entries);
  }

  private static List<String> fields(String line) throws IOException {
    // Exports may include an extra pair of quotes around each row.
    List<String> parsed;
    try (var csv = CSVParser.parse(line, CSVFormat.DEFAULT)) {
      parsed = csv.getRecords().getFirst().toList();
    } catch (UncheckedIOException e) {
      if (line.length() < 2 || !line.startsWith("\"") || !line.endsWith("\"")) throw e.getCause();
      parsed = List.of(line.substring(1, line.length() - 1));
    }
    if (parsed.size() != 1 || !parsed.getFirst().contains(",")) return parsed;
    try (var csv = CSVParser.parse(parsed.getFirst(), CSVFormat.DEFAULT)) {
      return csv.getRecords().getFirst().toList();
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static ApiException invalidHeader() {
    return new ApiException(400, "INVALID_MI_FITNESS_HEADER", "请选择含 Sid、Key、Time、Value 的 Mi Fitness 健康明细 CSV");
  }
}
