package com.reqpilot.controller;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private final DataSource dataSource;

  public HealthController(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @GetMapping({"/actuator/health", "/api/actuator/health"})
  public ResponseEntity<Map<String, Object>> health() {
    try {
      verifyDatabase();
      return ResponseEntity.ok(
          Map.of(
              "status",
              "UP",
              "components",
              Map.of("database", Map.of("status", "UP"))));
    } catch (SQLException exception) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
              Map.of(
                  "status",
                  "DOWN",
                  "components",
                  Map.of("database", Map.of("status", "DOWN"))));
    }
  }

  private void verifyDatabase() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("SELECT 1");
    }
  }
}
