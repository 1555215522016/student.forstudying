package com.scuplus.common.exception;

import lombok.Getter;

/**
 * 业务异常：Service 层遇到业务规则不满足时抛出
 * 例如：资源不存在、重复提交、无权限访问
 *
 * 为什么要有自定义异常？
 * 让业务代码"抛异常"而不是"层层返回错误码"——
 * Service 只管 throw，不用关心错误怎么翻译成响应，
 * 由 GlobalExceptionHandler 统一接住转成 Result。
 *
 * 为什么继承 RuntimeException（运行时异常）而不是 Exception？
 * 业务异常是"代码里主动抛出的预期错误"，不应该强制调用方 catch，
 * 否则每个方法都得 try-catch 污染业务代码。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码，转成 Result.status 返回给前端 */
    private final int status;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.status = errorCode.getStatus();
    }

    /** 覆盖默认提示语：保留错误码，但给出更具体的说明 */
    public BusinessException(ErrorCode errorCode, String msg) {
        super(msg);
        this.status = errorCode.getStatus();
    }
}
