package com.english.service;

import com.english.dto.EnglishExemptionApplicationRequest;
import com.english.entity.EnglishExemptionApplication;
import com.english.entity.User;

public interface EnglishExemptionApplicationService {

    EnglishExemptionApplication getLatestForStudent(Long studentId);

    EnglishExemptionApplication submit(User student, EnglishExemptionApplicationRequest request);
}
