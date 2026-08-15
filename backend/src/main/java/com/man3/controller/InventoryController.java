package com.man3.controller;

import com.man3.entity.InventoryRecord;
import com.man3.entity.InventoryRecordBook;
import com.man3.service.InventoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin
public class InventoryController {

    @Autowired
    private InventoryService inventoryService;

    /**
     * 触发一次入库盘点
     * POST /api/inventory/run
     */
    @PostMapping("/run")
    public Map<String, Object> run() {
        Map<String, Object> resp = new HashMap<>();
        try {
            if (inventoryService.isRunning()) {
                resp.put("code", 409);
                resp.put("message", "盘点正在进行中, 请稍后再试");
                return resp;
            }
            InventoryRecord record = inventoryService.doInventory();
            resp.put("code", 200);
            resp.put("message", "盘点完成");
            resp.put("data", record);
        } catch (Exception e) {
            resp.put("code", 500);
            resp.put("message", "盘点失败: " + e.getMessage());
        }
        return resp;
    }

    /**
     * 盘点记录列表
     * GET /api/inventory/records
     */
    @GetMapping("/records")
    public Map<String, Object> records() {
        Map<String, Object> resp = new HashMap<>();
        List<InventoryRecord> list = inventoryService.listRecords();
        resp.put("code", 200);
        resp.put("message", "ok");
        resp.put("data", list);
        return resp;
    }

    /**
     * 某批次的盘点漫画明细
     * GET /api/inventory/records/{recordId}/books
     */
    @GetMapping("/records/{recordId}/books")
    public Map<String, Object> recordBooks(@PathVariable Long recordId) {
        Map<String, Object> resp = new HashMap<>();
        List<InventoryRecordBook> list = inventoryService.listRecordBooks(recordId);
        resp.put("code", 200);
        resp.put("message", "ok");
        resp.put("data", list);
        return resp;
    }
}
