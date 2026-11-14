package com.health.service.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.health.mapper.PersonalExportMapper;
import com.health.mapper.PersonalExportMapper.Section;
import com.health.service.PersonalExportService;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalExportServiceImpl implements PersonalExportService {
  private final PersonalExportMapper mapper;
  private final ObjectMapper json;

  public PersonalExportServiceImpl(PersonalExportMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public void export(Long userId, OutputStream output) throws IOException {
    try (var generator = json.getFactory().createGenerator(output)) {
      generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
      generator.disable(JsonGenerator.Feature.AUTO_CLOSE_JSON_CONTENT);
      var writer = json.writer().without(SerializationFeature.FLUSH_AFTER_WRITE_VALUE);
      var reader = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
      generator.writeStartObject();
      generator.writeStringField("exportedAt", Instant.now().toString());
      generator.writeNumberField("formatVersion", 1);
      for (Section section : Section.values()) {
        generator.writeArrayFieldStart(section.name().toLowerCase(Locale.ROOT));
        try (var rows = mapper.rows(userId, section)) {
          for (var row : rows) {
            for (var field : row.entrySet()) {
              if (field.getKey().endsWith("_json") && field.getValue() instanceof String raw)
                field.setValue(reader.readTree(raw));
            }
            writer.writeValue(generator, row);
          }
        }
        generator.writeEndArray();
      }
      generator.writeEndObject();
    }
  }
}
