package com.chuck.framework.redis_distributed_lock;

import org.springframework.data.redis.core.RedisTemplate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * RedisDistributedLock
 * 责任人:  chenlei
 * 修改人： chenlei
 * 创建/修改时间: 2020/6/28  17:32
 * Copyright : 2014-2020 深圳令令科技有限公司-版权所有
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisDistributedLock {
    String[] keys() default "";//分布式锁的key,唯一性,格式类似mybatis的参数,例如: #params.id
    Class<?> redisTemplateBean() default RedisTemplate.class;//redisTemplate实例,最好自己通过@Configration和@Bean提供bean,如果直接用容器的,可能导致报错
    String redisKeyPrefix() default "";//key的前缀
    String redisKeySuffix() default "";//key的后缀
    long lockTime() default 10000L;//锁的时间,到期自动释放,单位毫秒
    long waitTime() default 2000L;//等待时间,单位毫秒
    int retryCountThreshold() default 1;//重试阈值
    String blockingHint() default "当前访问人数较多,请稍后再试!";//阻塞时的提示信息
}
