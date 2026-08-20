package com.scuplus.common.exception;

import com.scuplus.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：所有 Controller 抛出的异常都汇聚到这里
 *
 * 原理（人话）：@RestControllerAdvice 会"挂"到所有 Controller 上，
 * 任何一个 Controller 方法里抛出的异常，Spring MVC 都会交给
 * 下面带 @ExceptionHandler 的方法处理，把异常翻译成统一 Result。
 *
 * 三个处理方法从"具体"到"兜底"：
 *   业务异常（可预期的，code 明确） → 参数校验异常 → 未知异常（兜底 500）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：预期内错误，按错误码返回，日志记 warn 即可 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(e.getStatus(), e.getMessage());
    }

    /** 参数校验异常：@Valid 校验 DTO 失败时抛出 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        // 取第一个校验失败字段的错误信息，提示给前端
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.error(ErrorCode.BAD_REQUEST, msg);
    }

    /** 请求的路径不存在：body 返回 40400，HTTP 状态码也写成 404 */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNotFound(NoResourceFoundException e) {
        log.warn("请求的接口不存在：{}", e.getResourcePath());
        return Result.error(ErrorCode.NOT_FOUND);
    }

    /** 兜底：未预期的异常，返回系统繁忙（不暴露内部细节给前端），日志记 error */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);



        return Result.error(ErrorCode.SERVER_ERROR);
    }
}
