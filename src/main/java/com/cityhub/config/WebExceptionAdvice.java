package com.cityhub.config;

import com.cityhub.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：统一转成 {@link Result} 返回，避免把堆栈直接抛给前端。
 *
 * @author CityHub
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /** 路径变量 / 查询参数类型不对，例如 /user/info/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型错误: name={}, value={}", e.getName(), e.getValue());
        return Result.fail("参数格式不正确：" + e.getName());
    }

    /** 缺少必填参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必填参数: {}", e.getParameterName());
        return Result.fail("缺少必填参数：" + e.getParameterName());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("业务异常: {}", e.toString(), e);
        return Result.fail("服务器异常，请稍后再试");
    }
}
