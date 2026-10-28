package com.dianfei.ai.verify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_ai_3d_task")
public class AiBuild3dTask extends BaseEntity {
    private String taskId;
    private Long userId;
    private String imageUrl;
    private String status;
    private String mode;
    private String specJson;
    private String error;
}
