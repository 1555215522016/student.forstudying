package com.scuplus.common.exception;

import lombok.Getter;

/**
 * 错误码枚举：所有业务错误码集中管理，杜绝魔法数字
 *
 * 编号规则（5 位数字，前两位表示大类，后三位表示具体场景）：
 *   0       成功
 *   40xxx   客户端错误（请求本身有问题，不是系统的错）
 *     40000  请求参数错误
 *     40100  未登录或登录已过期
 *     40300  无权限访问
 *     40400  资源不存在
 *     40900  资源状态冲突
 *   50xxx   服务端错误（系统出问题了）
 *     50000  系统繁忙
 *
 * 后续各业务模块如需专属错误码，可在保留段外扩展（如 10xxx 模块错误段）。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "success"),

    /** 请求参数错误 */
    BAD_REQUEST(40000, "请求参数错误"),
    /** 缺少必要参数 */
    PARAM_MISSING(40001, "缺少必要参数"),
    /** 参数格式不正确 */
    PARAM_INVALID(40002, "参数格式不正确"),

    /** 未登录或登录已过期 */
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    /** 无权限访问该资源 */
    FORBIDDEN(40300, "无权限访问该资源"),

    /** 资源不存在 */
    NOT_FOUND(40400, "资源不存在"),

    /** 资源状态冲突（如重复提交、数据已被修改） */
    CONFLICT(40900, "资源状态冲突"),

    /** 系统繁忙 */
    SERVER_ERROR(50000, "系统繁忙，请稍后重试");

    /** 状态码，直接作为 Result.status 返回给前端 */
    private final int status;

    /** 提示信息 */
    private final String msg;

    ErrorCode(int status, String msg) {
        this.status = status;
        this.msg = msg;
    }
}
