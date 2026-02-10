package com.cbkj.infrastructure.sample.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DatabaseController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/tables")
    public Map<String, Object> getTables() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = SCHEMA() AND TABLE_TYPE = 'BASE TABLE'",
                String.class
            );
            
            result.put("tables", tables);
            result.put("count", tables.size());
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    @GetMapping("/migration-history")
    public Map<String, Object> getMigrationHistory() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, script, checksum, " +
                "installed_by, installed_on, execution_time, success " +
                "FROM flyway_digital_history ORDER BY installed_rank"
            );
            
            result.put("history", history);
            result.put("count", history.size());
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "flyway-digital-spring-boot-sample");
        return status;
    }
}
