package com.danmaku.exception;

import com.danmaku.vo.ResultVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
