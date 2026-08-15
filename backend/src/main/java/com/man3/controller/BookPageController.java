package com.man3.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.man3.entity.BookPage;
import com.man3.mapper.BookPageMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 章节图片(漫画页)查询接口, 供前端漫画阅读器分页加载
 */
@RestController
@RequestMapping("/api/page")
public class BookPageController {

    @Resource
    private BookPageMapper bookPageMapper;

    /**
     * 分页查询某章节的图片(按 page_no 升序, 保证阅读顺序)
     *
     * @param chapterId 章节ID(必填)
     * @param page      页码(从1开始, 默认1)
     * @param pageSize  每页条数(默认10, 最大50)
     */
    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam Long chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;
        if (pageSize > 50) pageSize = 50;

        Page<BookPage> pager = new Page<>(page, pageSize);
        // 按图片序号升序, 保证阅读顺序
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BookPage> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        w.eq(BookPage::getChapterId, chapterId).orderByAsc(BookPage::getPageNo);
        IPage<BookPage> result = bookPageMapper.selectPage(pager, w);

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        data.put("page", result.getCurrent());
        data.put("pageSize", result.getSize());

        Map<String, Object> map = new HashMap<>();
        map.put("code", 0);
        map.put("message", "success");
        map.put("data", data);
        return map;
    }
}
