package com.reqpilot.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.Proxy;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

  @Test
  void healthReturnsUpWhenDatabaseConnectionWorks() throws Exception {
    DataSource dataSource = workingDataSource();

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(dataSource)).build();

    mockMvc
        .perform(get("/api/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components.database.status").value("UP"));
  }

  @Test
  void healthReturnsDownWhenDatabaseConnectionFails() throws Exception {
    DataSource dataSource = failingDataSource();

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(dataSource)).build();

    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"))
        .andExpect(jsonPath("$.components.database.status").value("DOWN"));
  }

  private DataSource workingDataSource() {
    return proxy(
        DataSource.class,
        (proxy, method, args) -> {
          if (method.getName().equals("getConnection")) {
            return workingConnection();
          }
          return defaultValue(method.getReturnType());
        });
  }

  private DataSource failingDataSource() {
    return proxy(
        DataSource.class,
        (proxy, method, args) -> {
          if (method.getName().equals("getConnection")) {
            throw new SQLException("database unavailable");
          }
          return defaultValue(method.getReturnType());
        });
  }

  private Connection workingConnection() {
    return proxy(
        Connection.class,
        (proxy, method, args) -> {
          if (method.getName().equals("createStatement")) {
            return workingStatement();
          }
          return defaultValue(method.getReturnType());
        });
  }

  private Statement workingStatement() {
    return proxy(
        Statement.class,
        (proxy, method, args) -> {
          if (method.getName().equals("execute")) {
            return true;
          }
          return defaultValue(method.getReturnType());
        });
  }

  @SuppressWarnings("unchecked")
  private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private Object defaultValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class || type == long.class || type == short.class || type == byte.class) {
      return 0;
    }
    if (type == float.class || type == double.class) {
      return 0.0;
    }
    return null;
  }
}
