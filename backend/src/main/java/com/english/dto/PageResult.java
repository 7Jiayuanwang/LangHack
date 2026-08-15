package com.english.dto;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    // 当前页数据
    private List<T> records;
    // 总条数
    private Long total;
    // 当前页码
    private Integer pageNum;
    // 每页条数
    private Integer pageSize;
    // 总页数
    private Integer pages;
}