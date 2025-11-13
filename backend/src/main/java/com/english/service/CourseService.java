package com.english.service;

import com.english.entity.Course;

import java.util.List;
import java.util.Map;

public interface CourseService {
    Map<String, Object> getCoursesByPage(Map<String, Object> params);
    Course getCourseById(Long id);
    void createCourse(Course course);
    void updateCourse(Course course);
    void deleteCourse(Long id);
    List<Course> getActiveCourses();
    List<Course> getCoursesByKeyword(String keyword);
    Map<String, Object> getCourseStats();
}