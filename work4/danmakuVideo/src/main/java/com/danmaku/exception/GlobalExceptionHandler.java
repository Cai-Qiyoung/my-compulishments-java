package com.danmaku.exception;

import com.danmaku.vo.ResultVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResultVo<?> handleBusinessException(BusinessException e) {
        log.error("业务异常：{}", e.getMessage());
        return ResultVo.fail(e.code, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResultVo<?> handleException(Exception e) {
        log.error("系统异常：", e);
        return ResultVo.fail("服务器异常，请稍后重试");
    }
}