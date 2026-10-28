package com.dianfei.business.science.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 科普文章分类 VO（V51，动态分类来自文章数据）
 */
@Data
@Builder
public class CategoryVO {

    private String code;
    private String name;
}
