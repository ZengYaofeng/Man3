package com.man3.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.man3.api.dto.PageResult;
import com.man3.entity.IngestLog;
import com.man3.service.BatchIngestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final BatchIngestService batchIngestService;

    public IngestController(BatchIngestService batchIngestService) {
        this.batchIngestService = batchIngestService;
    }

    @GetMapping("/preview")
    public Map<String, Object> preview(@RequestParam(defaultValue = "crawlTime") String sortField,
                                       @RequestParam(defaultValue = "asc") String sortDir) {
        return success(batchIngestService.preview(sortField, sortDir));
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestParam(defaultValue = "crawlTime") String sortField,
                                     @RequestParam(defaultValue = "asc") String sortDir) {
        try {
            return success(batchIngestService.start(sortField, sortDir));
        } catch (IllegalStateException e) {
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("code", 409);
            response.put("message", e.getMessage());
            return response;
        }
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel() {
        try {
            return success(batchIngestService.cancel());
        } catch (IllegalStateException e) {
            Map<String, Object> response = new HashMap<String, Object>();
            response.put("code", 409);
            response.put("message", e.getMessage());
            return response;
        }
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return success(batchIngestService.getStatus());
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize,
                                    @RequestParam(required = false) String batchNo) {
        IPage<IngestLog> result = batchIngestService.pageLogs(batchNo, page, pageSize);
        return success(PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords()));
    }

    private static Map<String, Object> success(Object data) {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("code", 0);
        response.put("message", "success");
        response.put("data", data);
        return response;
    }
}
