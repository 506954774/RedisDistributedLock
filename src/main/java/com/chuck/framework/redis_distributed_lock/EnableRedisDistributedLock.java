package com.chuck.framework.redis_distributed_lock;

import com.chuck.framework.config.RedisLockConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;


/**
 * EnableRedisDistributedLock
 * 启用redis分布式锁
 * 责任人:  chenlei
 * 修改人： chenlei
 * 创建/修改时间: 2020/6/28  17:32
 * Copyright : 2014-2020 深圳令令科技有限公司-版权所有
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RedisLockConfig.class)
public @interface EnableRedisDistributedLock {

}
