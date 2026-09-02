package com.scuplus.common.aspect;


import com.scuplus.common.annotation.RateLimit;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

@Component
@Aspect
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {
    private final StringRedisTemplate redisTemplate;
    private static final String REDIS_KEY_PREFIX="rate_limit:user";
    static final RedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        String script =
                "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]) " +
                        "local current = redis.call('ZCARD', KEYS[1]) " +
                        "if current < tonumber(ARGV[2]) then " +
                        "   redis.call('ZADD', KEYS[1], ARGV[3], ARGV[5])" +
                        "if current == 0 then"+
                        "   redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
                        "end"+
                        "   return 1 " +
                        "else " +
                        "   return 0 " +
                        "end";
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(script, Long.class);
    }
    @Around("@annotation(com.scuplus.annotation.RateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint)throws Throwable
    {
        LoginUser loginUser= (LoginUser) SecurityContextHolder.getContext().
                getAuthentication().getPrincipal();
        Long userId=loginUser.getUserId();
        String key=REDIS_KEY_PREFIX+userId;

        MethodSignature methodSignature=(MethodSignature)joinPoint.getSignature();
        Method method=methodSignature.getMethod();
        RateLimit rateLimit=method.getAnnotation(RateLimit.class);
        int maxRequest= rateLimit.maxRequests();
        int windowSeconds= rateLimit.windowSeconds();
        String message=rateLimit.message();
        Long now=System.currentTimeMillis();
        Long windowStart=now-windowSeconds*1000L;
        String member=now+":"+ UUID.randomUUID().toString();


// 在切面方法中执行：
        Long result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,                // 脚本
                Collections.singletonList(key),   // KEYS[1]
                String.valueOf(windowStart),      // ARGV[1]
                String.valueOf(maxRequest),      // ARGV[2]
                String.valueOf(now),              // ARGV[3]
                String.valueOf(windowSeconds * 2), // ARGV[4]
                member    // ARGV[5]
        );

        if(result==0){
            log.warn("用户{}被限流，窗口{}秒内请求超过{}次",userId,windowSeconds,maxRequest);
            throw new BusinessException(ErrorCode.SERVER_ERROR,message);
        }
        return joinPoint.proceed();



    }
}
