-- 检查行政班表中的学院数据
SELECT 
    class_code,
    class_name,
    college_code,
    college_name,
    major_code,
    major_name
FROM administrative_class
LIMIT 10;

-- 检查字段映射配置
SELECT 
    data_source,
    source_field,
    target_field,
    priority
FROM field_mappings
WHERE data_source = 'administrative_class'
  AND target_field IN ('college_code', 'college_name')
ORDER BY priority DESC;
