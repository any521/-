package com.dianfei.common.exception;

import com.dianfei.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：Service 层主动抛出，由 GlobalExceptionHandler 转换为标准 Result
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(String msg) {
        super(msg);
        this.resultCode = ResultCode.FAILED;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String msg) {
        super(msg);
        this.resultCode = resultCode;
    }
}
