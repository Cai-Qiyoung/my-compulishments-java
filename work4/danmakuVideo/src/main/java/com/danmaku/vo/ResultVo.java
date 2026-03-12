package com.danmaku.vo;

import lombok.Data;

@Data
public class ResultVo<T> {
    private Integer code; // 状态码：10000-成功，其他-失败
    private String msg;
    private T data;

    // 成功返回(无数据)
    public static <T> ResultVo<T> success() {
        ResultVo<T> vo = new ResultVo<>();
        vo.setCode(10000);
        vo.setMsg("success");
        return vo;
    }

    // 成功返回(有数据)
    public static <T> ResultVo<T> success(T data) {
        ResultVo<T> vo = new ResultVo<>();
        vo.setCode(10000);
        vo.setMsg("success");
        vo.setData(data);
        return vo;
    }

    // 自定义
    public static <T> ResultVo<T> success(String msg) {
        ResultVo<T> vo = new ResultVo<>();
        vo.setCode(10000);
        vo.setMsg(msg);
        return vo;
    }

    // 失败返回
    public static <T> ResultVo<T> fail(Integer code, String msg) {
        ResultVo<T> vo = new ResultVo<>();
        vo.setCode(code);
        vo.setMsg(msg);
        return vo;
    }

    // 通用失败返回
    public static <T> ResultVo<T> fail(String msg) {
        return fail(-1, msg);
    }
}