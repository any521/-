package com.dianfei.business.science.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

/**
 * 作者信息 VO（V42）
 */
@Data
@Builder
public class AuthorInfoVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String nickname;
    private String avatar;
    private Integer followerCount;
    private Integer articleCount;
}
