package com.scuplus.common.result;

import com.scuplus.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 统一响应体：所有接口都返回这个结构
 *
 * 与前端约定：status == 0 表示成功，非 0 表示业务错误码
 * 前端 http.js 检查 res.data.status === 0 来判断成败
 *
 * 为什么所有接口都要包一层 Result？
 * 1. 错误信息结构统一，前端不用为每个接口写不同的错误处理
 * 2. 业务错误码可扩展（对应后续的 ErrorCode 枚举）
 * 3. 数据外层加一层，后续加时间戳/版本号等元信息不用改接口签名
 */
@Getter
public class Result<T> {

    /** 状态码：0=成功，非 0=错误码 */
    private int status;

    /** 提示信息 */
    private String msg;

    /** 业务数据（成功时才有值，失败时一般为 null） */
    private T data;
    private void setStatus(int status) { this.status = status; }
    private void setMsg(String msg) { this.msg = msg; }
    private void setData(T data) { this.data = data; }


    /** 成功，无返回数据 */
    public static <T> Result<T> success() {
        return success(null);
    }

    /** 成功，携带返回数据 */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setStatus(0);
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    /** 失败，返回错误码和提示信息 */
    public static <T> Result<T> error(int status, String msg) {
        Result<T> result = new Result<>();
        result.setStatus(status);
        result.setMsg(msg);
        return result;
    }

    /** 失败，传入错误码枚举（推荐写法，避免魔法数字） */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getStatus(), errorCode.getMsg());
    }

    public static <T> Result<T> error(ErrorCode errorCode,String msg)
    {
        return error(errorCode.getStatus(),msg!=null ? msg : errorCode.getMsg());
    }
}

