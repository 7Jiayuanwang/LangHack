package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.AssignmentTemplate;
import com.english.exception.BusinessException;
import com.english.mapper.AssignmentTemplateMapper;
import com.english.service.AssignmentTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssignmentTemplateServiceImpl extends ServiceImpl<AssignmentTemplateMapper, AssignmentTemplate>
        implements AssignmentTemplateService {
    @Override
    public List<AssignmentTemplate> listByTeacher(Long teacherId) {
        return list(new QueryWrapper<AssignmentTemplate>()
                .eq("created_by", teacherId)
                .orderByDesc("updated_time", "created_time"));
    }

    @Override
    public AssignmentTemplate getOwned(Long id, Long teacherId) {
        return getOne(new QueryWrapper<AssignmentTemplate>()
                .eq("id", id).eq("created_by", teacherId));
    }

    @Override
    @Transactional
    public void create(AssignmentTemplate template) {
        validate(template);
        save(template);
    }

    @Override
    @Transactional
    public void update(AssignmentTemplate template, Long teacherId) {
        AssignmentTemplate existing = getOwned(template.getId(), teacherId);
        if (existing == null) throw new BusinessException("模板不存在或无权修改");
        validate(template);
        template.setCreatedBy(teacherId);
        updateById(template);
    }

    @Override
    @Transactional
    public void delete(Long id, Long teacherId) {
        AssignmentTemplate existing = getOwned(id, teacherId);
        if (existing == null) throw new BusinessException("模板不存在或无权删除");
        removeById(id);
    }

    private void validate(AssignmentTemplate template) {
        if (template.getName() == null || template.getName().trim().isEmpty()) {
            throw new BusinessException("模板名称不能为空");
        }
        if (template.getName().trim().length() > 120) {
            throw new BusinessException("模板名称不能超过120个字符");
        }
        if (template.getConfigJson() == null || template.getConfigJson().trim().isEmpty()) {
            throw new BusinessException("模板配置不能为空");
        }
    }
}
