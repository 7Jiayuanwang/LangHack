package com.english.service;

import com.english.dto.EnglishExemptionApplicationRequest;
import com.english.entity.CourseExemptionApplication;
import com.english.entity.User;

public interface EnglishExemptionApplicationService {

    CourseExemptionApplication getLatestForStudent(Long studentId);

    CourseExemptionApplication submit(User student, EnglishExemptionApplicationRequest request);
}
