package com.man3.controller;

import com.man3.entity.SystemStat;
import com.man3.service.SystemStatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system-stats")
public class SystemStatController {
    private final SystemStatService systemStatService;

    public SystemStatController(SystemStatService systemStatService) {
        this.systemStatService = systemStatService;
    }

    @GetMapping
    public Map<String, Object> current() {
        return response(systemStatService.snapshot());
    }

    @PostMapping("/reconcile")
    public Map<String, Object> reconcile() {
        return response(systemStatService.reconcile());
    }

    private Map<String, Object> response(SystemStat stat) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("data", stat);
        return result;
    }
}
