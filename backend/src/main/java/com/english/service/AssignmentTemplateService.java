package com.english.service;

import com.english.entity.AssignmentTemplate;
import java.util.List;

public interface AssignmentTemplateService {
    List<AssignmentTemplate> listByTeacher(Long teacherId);
    AssignmentTemplate getOwned(Long id, Long teacherId);
    void create(AssignmentTemplate template);
    void update(AssignmentTemplate template, Long teacherId);
    void delete(Long id, Long teacherId);
}
