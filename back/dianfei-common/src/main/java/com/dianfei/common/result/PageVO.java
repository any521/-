package com.dianfei.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页返回结果（total + list + pages）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页返回结果")
public class PageVO<T> {

    @Schema(description = "总条数")
    private Long total;

    @Schema(description = "总页数")
    private Long pages;

    @Schema(description = "当前页数据")
    private List<T> list;

    public static <T> PageVO<T> of(IPage<T> page) {
        return new PageVO<>(page.getTotal(), page.getPages(), page.getRecords());
    }

    public static <T> PageVO<T> of(long total, long pages, List<T> list) {
        return new PageVO<>(total, pages, list);
    }
}
