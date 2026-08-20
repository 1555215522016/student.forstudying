package com.scuplus.common.result;

import lombok.Data;

import java.util.List;

/**
 * 分页结果：作为 Result.data 的内容
 *
 * 返回给前端的结构：
 * {
 *   "status": 0,
 *   "msg": "success",
 *   "data": { "total": 137, "list": [ ... ] }
 * }
 *
 * 为什么 data 要包一层 total + list 而不是直接返回 list？
 * 前端分页控件需要知道总条数（显示"共 137 条"、算总页数），
 * 只返回 list 就拿不到 total，还得再发一次 count 请求。
 */
@Data
public class PageResult<T> {

    /** 总条数（前端分页显示"共 N 条"） */
    private long total;

    /** 当前页数据 */
    private List<T> list;

    /** 构造分页结果 */
    public static <T> PageResult<T> of(List<T> list, long total) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setList(list);
        pageResult.setTotal(total);
        return pageResult;
    }
}
