//package com.english.service.impl;
//
//import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
//import com.baomidou.mybatisplus.core.metadata.IPage;
//import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import com.english.entity.Unit;
//import com.english.mapper.UnitMapper;
//import com.english.service.UnitService;
//import org.springframework.stereotype.Service;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@Service
//public class UnitServiceImpl extends ServiceImpl<UnitMapper, Unit> implements UnitService {
//
//    @Override
//    public Map<String, Object> getUnitsByPage(Map<String, Object> params) {
//        int page = (int) params.get("page");
//        int size = (int) params.get("size");
//        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId").toString()) : null;
//        String keyword = (String) params.get("keyword");
//
//        QueryWrapper<Unit> queryWrapper = new QueryWrapper<>();
//
//        if (courseId != null) {
//            queryWrapper.eq("course_id", courseId);
//        }
//
//        if (keyword != null && !keyword.isEmpty()) {
//            queryWrapper.and(wrapper -> wrapper
//                    .like("name", keyword)
//                    .or()
//                    .like("description", keyword));
//        }
//
//        queryWrapper.orderByAsc("order_num").orderByDesc("created_time");
//
//        Page<Unit> pageParam = new Page<>(page, size);
//        IPage<Unit> pageResult = page(pageParam, queryWrapper);
//
//        Map<String, Object> result = new HashMap<>();
//        result.put("records", pageResult.getRecords());
//        result.put("total", pageResult.getTotal());
//        result.put("pages", pageResult.getPages());
//        result.put("current", pageResult.getCurrent());
//        result.put("size", pageResult.getSize());
//
//        return result;
//    }
//
//    @Override
//    public List<Unit> getUnitsByCourseId(Long courseId) {
//        QueryWrapper<Unit> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq("course_id", courseId);
//        queryWrapper.orderByAsc("order_num").orderByDesc("created_time");
//        return list(queryWrapper);
//    }
//
//    @Override
//    public Unit getUnitById(Long id) {
//        return getById(id);
//    }
//
//    @Override
//    public void createUnit(Unit unit) {
//        save(unit);
//    }
//
//    @Override
//    public void updateUnit(Unit unit) {
//        updateById(unit);
//    }
//
//    @Override
//    public void deleteUnit(Long id) {
//        // 物理删除
//        removeById(id);
//    }
//}

package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Unit;
import com.english.mapper.UnitMapper;
import com.english.service.UnitService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UnitServiceImpl extends ServiceImpl<UnitMapper, Unit> implements UnitService {

    @Override
    public Map<String, Object> getUnitsByPage(Map<String, Object> params) {
        int page = (int) params.get("page");
        int size = (int) params.get("size");
        Long courseId = params.get("courseId") != null ? Long.valueOf(params.get("courseId").toString()) : null;
        String keyword = (String) params.get("keyword");

        QueryWrapper<Unit> queryWrapper = new QueryWrapper<>();

        if (courseId != null) {
            queryWrapper.eq("course_id", courseId);
        }

        if (keyword != null && !keyword.isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like("name", keyword)
                    .or()
                    .like("description", keyword));
        }

        queryWrapper.orderByAsc("order_num").orderByDesc("created_time");

        Page<Unit> pageParam = new Page<>(page, size);
        IPage<Unit> pageResult = page(pageParam, queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public List<Unit> getUnitsByCourseId(Long courseId) {
        QueryWrapper<Unit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("course_id", courseId);
        queryWrapper.orderByAsc("order_num").orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public Unit getUnitById(Long id) {
        return getById(id);
    }

    @Override
    public void createUnit(Unit unit) {
        save(unit);
    }

    @Override
    public void updateUnit(Unit unit) {
        updateById(unit);
    }

    @Override
    public void deleteUnit(Long id) {
        removeById(id);
    }

    // 新增：实现接口中定义的checkUnitBelongToCourse方法
    @Override
    public boolean checkUnitBelongToCourse(Long unitId, Long courseId) {
        Unit unit = getById(unitId);
        return unit != null && unit.getCourseId().equals(courseId);
    }
}
