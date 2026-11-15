package com.health.service;

import com.health.model.Models.ImportBatch;
import com.health.model.Models.ImportRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SleepImportIntervals {
  private SleepImportIntervals() {}

  static void normalizeEpisodes(List<Interval> intervals, ZoneId zone, ImportBatch batch) {
    Map<String, List<Interval>> sources = new HashMap<>();
    for (Interval interval : intervals) {
      sources.computeIfAbsent(interval.source(), ignored -> new ArrayList<>()).add(interval);
    }
    for (List<Interval> source : sources.values()) {
      source.sort(Comparator.comparing(Interval::start).thenComparing(Interval::end));
      int componentStart = 0;
      Instant componentEnd = source.getFirst().end();
      for (int i = 1; i <= source.size(); i++) {
        if (i < source.size() && !source.get(i).start().isAfter(componentEnd)) {
          if (source.get(i).end().isAfter(componentEnd)) componentEnd = source.get(i).end();
          continue;
        }
        LocalDate sleepDate = componentEnd.atZone(zone).toLocalDate();
        for (int j = componentStart; j < i; j++) {
          ImportRow asleep = source.get(j).row();
          if (asleep != null) asleep.sleepDate = sleepDate;
        }
        if (i < source.size()) {
          componentStart = i;
          componentEnd = source.get(i).end();
        }
      }
      Map<LocalDate, List<Interval>> days = new HashMap<>();
      for (Interval interval : source) {
        if (interval.row() != null) {
          days.computeIfAbsent(interval.row().sleepDate, ignored -> new ArrayList<>()).add(interval);
        }
      }
      for (List<Interval> day : days.values()) {
        Instant start = day.getFirst().start();
        Instant end = start;
        for (Interval interval : day) {
          if (interval.end().isAfter(end)) end = interval.end();
        }
        if (zone.getRules().getOffset(start).equals(zone.getRules().getOffset(end))) continue;
        // The existing statistics merge local timestamps for each source and sleep date.
        // Exclude the whole affected group so separate stages cannot overlap after a clock reset.
        for (Interval interval : day) {
          ImportRow row = interval.row();
          row.valid = false;
          row.error = "该来源在此睡眠日期跨越夏令时或UTC偏移切换，当前不支持；请将档案时区改为UTC后重新导入";
          batch.validRows--;
          batch.unsupportedRows++;
        }
      }
    }
  }

  record Interval(Instant start, Instant end, String source, ImportRow row) {}
}
