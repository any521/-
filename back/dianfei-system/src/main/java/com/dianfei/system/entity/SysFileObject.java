package com.dianfei.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dianfei.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 文件对象元数据；私有业务文件必须先登记所有者才能被读取。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file_object")
public class SysFileObject extends BaseEntity {

    private String storagePath;
    private Long ownerId;
    private String biz;
    private Integer publicAccess;
}
