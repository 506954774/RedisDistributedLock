package com.chuck.framework.redis_distributed_lock;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RedisDistributedLockHandler
 *  * 责任人:  chenlei
 *  * 修改人： chenlei
 *
 * 核心功能：AOP实现redis分布式锁,从参数中取出id,针对此id使用redis的setNx命令加一个带有时效性的锁.
 * 使用场景:抢购秒杀
 **/
@Component
@Aspect
@Slf4j
public class RedisDistributedLockHandler implements ApplicationContextAware {

    public static final String TAG="分布式锁-";

    private ApplicationContext applicationContext;

    @Around(value = "@annotation(redisDistributedLock)")
    public Object redisLock(ProceedingJoinPoint jp, RedisDistributedLock redisDistributedLock) throws Exception{

        String blockingHint = redisDistributedLock.blockingHint();//阻塞时报错

        try {
                Object result=null;

                //框架拿到的入参集合
                Object[] args = jp.getArgs();

                //如果方法是无参的，则直接抛出异常
                if(args==null||args.length==0){
                    throw new RuntimeException(blockingHint);
                }
                else{
                    //从IOC容器中获取redisTemplateBean
                    RedisTemplate redisTemplate = (RedisTemplate) this.applicationContext.getBean(redisDistributedLock.redisTemplateBean());

                    //前缀
                    String redisKeyPrefix = redisDistributedLock.redisKeyPrefix();
                    //后缀
                    String redisKeySuffix = redisDistributedLock.redisKeySuffix();
                    //redis锁的过期时间
                    long lockTime = redisDistributedLock.lockTime();


                    String[] keys=redisDistributedLock.keys();

                    if(keys!=null&&keys.length!=0){

                            Signature sig = jp.getSignature();
                            MethodSignature msig = null;
                            if (!(sig instanceof MethodSignature)) {
                                throw new IllegalArgumentException("该注解只能用于方法");
                            }
                            msig = (MethodSignature) sig;
                            Object target = jp.getTarget();
                            Method currentMethod = target.getClass().getMethod(msig.getName(), msig.getParameterTypes());

                            String  redisKey=  buildKey(currentMethod, jp.getArgs(),redisDistributedLock.keys(),redisKeyPrefix,redisKeySuffix);;

                            log.info( TAG+"分布式redisKey:{}", redisKey);

                            //加上UUID,自己上的锁只能自己解开
                            String value= UUID.randomUUID().toString();

                            //设置分布式锁,key是锁id,value是当前应用进程标识,lockTime后自动删除.lockTime内能把业务做完,则不会有任何问题
                            boolean notBlocking= redisTemplate.opsForValue().setIfAbsent(redisKey,value,lockTime, TimeUnit.MILLISECONDS);

                            if(!notBlocking){
                                //阻塞,则等待,并重试
                                int retryCount=0;
                                int retryCountThreshold=redisDistributedLock.retryCountThreshold();
                                long waitTime=redisDistributedLock.waitTime();
                                while (!notBlocking&&retryCount<retryCountThreshold){
                                    retryCount++;
                                  Thread.sleep(waitTime);
                                  notBlocking= redisTemplate.opsForValue().setIfAbsent(redisKey,value,lockTime, TimeUnit.MILLISECONDS);
                                }
                            }
                            //等待过后仍然阻塞了,则抛出(后续可以抛给降级)
                            if(!notBlocking){
                                log.warn( TAG+blockingHint);
                                throw new RuntimeException(blockingHint);
                            }

                            //拿到锁了,继续做业务
                            try {
                                result=doExec(jp, args);
                                return result;
                            } catch (Exception e) {
                                log.error( TAG+"异常:"+ Arrays.toString(e.getStackTrace()));
                                throw new RuntimeException(blockingHint);
                            }

                            //如果实际业务里抛异常了,最终释放当前锁
                            finally {
                                ValueOperations<String, String> vopt = redisTemplate.opsForValue();
                                //自己才能删除自己的锁
                                if(value.equals(vopt.get(redisKey))){
                                    //不论过程如何,最后把锁删掉
                                    redisTemplate.delete(redisKey);
                                    log.info(TAG + "释放分布式锁,value:" + value);
                                }
                            }

                    }
                    else {
                        throw new RuntimeException(blockingHint);
                    }

                }

        } catch (Exception e) {
            log.error( TAG+"异常:"+ Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(blockingHint);
        }

    }

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final ExpressionParser PARSER = new SpelExpressionParser();


    /**
     * 拼接redis分布式锁的key
     * @param method
     * @param parameterValues
     * @param definitionKeys
     * @param prefix
     * @param suffix
     * @return
     */
    protected String buildKey(Method method, Object[] parameterValues,String[] definitionKeys,String prefix, String suffix) {
        StringBuilder sb = new StringBuilder(prefix==null?"":prefix );
        if (definitionKeys.length > 1 || !"".equals(definitionKeys[0])) {
            sb.append(getSpelDefinitionKey(definitionKeys, method, parameterValues,"",""));
        }
        sb.append(suffix==null?"":suffix);
        return sb.toString();
    }

    /**
     * 根据配置,获取key
     * @param definitionKeys id配置,类似mybatis里的入参,例如:#params.id
     * @param method          方法
     * @param parameterValues 实参
     * @param prefix          前缀
     * @param suffix          后缀
     * @return
     */
    protected String getSpelDefinitionKey(String[] definitionKeys, Method method, Object[] parameterValues,String prefix, String suffix) {
        EvaluationContext context = new MethodBasedEvaluationContext(null, method, parameterValues, NAME_DISCOVERER);
        List<String> definitionKeyList = new ArrayList<>(definitionKeys.length);
        for (String definitionKey : definitionKeys) {
            if (definitionKey != null && !definitionKey.isEmpty()) {
                String key = PARSER.parseExpression(definitionKey).getValue(context).toString();
                definitionKeyList.add(key);
            }
        }
        return StringUtils.collectionToDelimitedString(definitionKeyList, ".", prefix==null?"":prefix, suffix==null?"":suffix);
    }

    /**
     * 继续执行方法
     * @param jp
     * @param args
     * @return
     * @throws Exception
     */
    private Object doExec(ProceedingJoinPoint jp, Object[] args) throws Exception {
        try {
            Object result = jp.proceed(args);
            //如果这里不返回result，则目标对象实际返回值会被置为null
            return result;
        } catch (Throwable throwable) {
            log.error(  "[redis分布式锁,AOP操作异常]，方法签名："+jp.toString()+",参数:"+Arrays.toString(args), throwable);
            throw new Exception(throwable);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
         this.applicationContext=applicationContext;
    }
}























