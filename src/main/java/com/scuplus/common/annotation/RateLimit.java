package com.scuplus.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    int windowSeconds() default 1;
    int maxRequests() default 3;
    String message() default "操作过于繁忙，请稍后再试";
}
