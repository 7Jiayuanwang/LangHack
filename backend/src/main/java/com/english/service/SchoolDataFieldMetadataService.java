package com.english.service;

import com.english.dto.SchoolDataFilterFieldDto;

import java.util.List;
import java.util.Map;

public interface SchoolDataFieldMetadataService {

    List<SchoolDataFilterFieldDto> getFilterFields(String dataSource);

    Map<String, SchoolDataFilterFieldDto> getFilterFieldMap(String dataSource);
}
