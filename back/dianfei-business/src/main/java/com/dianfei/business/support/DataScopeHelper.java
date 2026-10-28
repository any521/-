package com.dianfei.business.support;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.dianfei.common.aspect.DataScopeAspect.DataScopeContext;

/**
 * @DataScope 过滤片段应用助手：
 * 切面把 "area_id = xxx" 写入 DataScopeContext，Service 在查询 Wrapper 上调用本方法拼接。
 */
public final class DataScopeHelper {

    private DataScopeHelper() {
    }

    /** 将当前线程的数据权限片段拼到查询条件（无限制时不拼接） */
    public static void apply(AbstractWrapper<?, ?, ?> wrapper) {
        String filter = DataScopeContext.get();
        if (filter != null && !filter.isBlank()) {
            wrapper.apply(filter);
        }
    }
}
