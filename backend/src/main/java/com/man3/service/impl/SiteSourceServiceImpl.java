package com.man3.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.man3.api.dto.SiteSourceSaveDTO;
import com.man3.entity.SiteSource;
import com.man3.mapper.SiteSourceMapper;
import com.man3.service.SiteSourceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 漫画来源站点服务实现
 */
@Service
public class SiteSourceServiceImpl implements SiteSourceService {

    private final SiteSourceMapper siteSourceMapper;

    public SiteSourceServiceImpl(SiteSourceMapper siteSourceMapper) {
        this.siteSourceMapper = siteSourceMapper;
    }

    @Override
    public IPage<SiteSource> pageQuery(int page, int pageSize, String keyword) {
        LambdaQueryWrapper<SiteSource> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SiteSource::getName, keyword)
                    .or().like(SiteSource::getUrl, keyword)
                    .or().like(SiteSource::getRemark, keyword));
        }
        wrapper.orderByDesc(SiteSource::getUpdatedAt)
               .orderByDesc(SiteSource::getId);
        Page<SiteSource> pageParam = new Page<>(Math.max(page, 1), Math.max(pageSize, 1));
        return siteSourceMapper.selectPage(pageParam, wrapper);
    }

    @Override
    public List<SiteSource> listAll() {
        LambdaQueryWrapper<SiteSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SiteSource::getId);
        return siteSourceMapper.selectList(wrapper);
    }

    @Override
    public SiteSource getById(Long id) {
        return siteSourceMapper.selectById(id);
    }

    @Override
    public SiteSource saveOrUpdate(SiteSourceSaveDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        if (dto.getId() != null) {
            SiteSource exist = siteSourceMapper.selectById(dto.getId());
            if (exist == null) {
                throw new IllegalArgumentException("站点不存在: " + dto.getId());
            }
            exist.setName(dto.getName());
            exist.setUrl(dto.getUrl());
            exist.setImageRule(dto.getImageRule());
            exist.setDetailUrl(dto.getDetailUrl());
            exist.setContentUrl(dto.getContentUrl());
            if (dto.getEnabled() != null) exist.setEnabled(dto.getEnabled());
            exist.setRemark(dto.getRemark());
            exist.setUpdatedAt(now);
            siteSourceMapper.updateById(exist);
            return exist;
        }
        SiteSource site = new SiteSource();
        site.setName(dto.getName());
        site.setUrl(dto.getUrl());
        site.setImageRule(dto.getImageRule());
        site.setDetailUrl(dto.getDetailUrl());
        site.setContentUrl(dto.getContentUrl());
        site.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        site.setRemark(dto.getRemark());
        site.setCreatedAt(now);
        site.setUpdatedAt(now);
        siteSourceMapper.insert(site);
        return site;
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        SiteSource site = siteSourceMapper.selectById(id);
        if (site == null) return;
        site.setEnabled(enabled ? 1 : 0);
        site.setUpdatedAt(LocalDateTime.now());
        siteSourceMapper.updateById(site);
    }

    @Override
    public void delete(Long id) {
        siteSourceMapper.deleteById(id);
    }
}
