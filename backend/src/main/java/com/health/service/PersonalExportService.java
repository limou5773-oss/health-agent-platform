package com.health.service;

import java.io.IOException;
import java.io.OutputStream;

public interface PersonalExportService {
  void export(Long userId, OutputStream output) throws IOException;
}
