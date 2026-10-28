package com.dianfei.common.exception;

import com.dianfei.common.result.Result;
import com.dianfei.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * 全局异常处理：业务 / 参数校验 / 权限 / 系统 分级处理
 * C20：认证/授权/限流/参数错误返回真实 HTTP 状态码 + body Result；其余业务码保持 HTTP 200 由 code 承载
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        log.warn("[业务异常] code={}, msg={}", e.getResultCode().getCode(), e.getMessage());
        return build(e.getResultCode(), e.getMessage());
    }

    /** 参数校验异常：@RequestBody @Valid */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return build(ResultCode.PARAM_ERROR, msg);
    }

    /** 参数校验异常：表单绑定 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("参数校验失败");
        return build(ResultCode.PARAM_ERROR, msg);
    }

    /** 参数校验异常：@RequestParam / @PathVariable */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return build(ResultCode.PARAM_ERROR, e.getMessage());
    }

    /** 权限不足 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return build(ResultCode.FORBIDDEN);
    }

    /**
     * 路径不存在（未匹配任何 Controller/静态资源）：返回真实 HTTP 404，
     * 避免被兜底成 5000 系统异常——错误语义不准确，且会被外部扫描器刷爆 ERROR 日志
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("[路径不存在] method={}, resource={}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.failed(ResultCode.NOT_FOUND, "接口路径不存在"));
    }

    /** 兜底系统异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("[系统异常]", e);
        return build(ResultCode.SYSTEM_ERROR);
    }

    private ResponseEntity<Result<Void>> build(ResultCode resultCode) {
        return build(resultCode, resultCode.getMsg());
    }

    private ResponseEntity<Result<Void>> build(ResultCode resultCode, String msg) {
        Result<Void> body = Result.failed(resultCode, msg);
        return ResponseEntity.status(resultCode.httpStatus()).body(body);
    }
}
