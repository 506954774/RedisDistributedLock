# RedisDistributedLock
Redis Distributed Lock
##手写基于redis的分布式锁组件,使用AOP实现
自己手撸的基于redis的分布式锁,已提交至maven中央仓库.使用简单.

#一,使用:
1,引入依赖:

pom.xml:

        <!--redis分布式锁-->
        <dependency>
            <groupId>io.github.506954774</groupId>
            <artifactId>redis-lock</artifactId>
            <version>1.0.0</version>
        </dependency>
2,application.properties:

spring.redis.host=127.0.0.1
spring.redis.port=6379
spring.redis.database=0
spring.redis.lettuce.pool.max-active=32
spring.redis.lettuce.pool.max-wait=300ms
spring.redis.lettuce.pool.max-idle=16
spring.redis.lettuce.pool.min-idle=8
3,Application.java:
```Java
@SpringBootApplication
@EnableRedisDistributedLock //使用该注解,开启分布式锁
public class Application {

    public static void main(String[] args) {
      SpringApplication.run(Application.class, args);
    }

}
```

4,Controller.java:
```Java
@Slf4j
@RestController
@RequestMapping("/redis_distributed_lock")
public class OrderController  {

  public static final String TAG="秒杀-";

 @RedisDistributedLock(
            //GOODS_SECKILL_是redis存数据时,key的前缀,#params.goodsSkuId类似mybatis里,获取实际值,例如商品id
            keys = {"'GOODS_SECKILL_'+#params.goodsSkuId"},
            //锁最迟多久释放,毫秒
            lockTime = 20000L,
            //并发时,等待获取锁的时间,毫秒
            waitTime= 3000L,
            //报错提示,将以RuntimeException的形式抛出
            blockingHint = "当前抢购人数太多,请稍后再试!"
    )
    @ApiOperation(value = "测试分布式锁(商品秒杀抢购场景)", notes = "测试分布式锁(商品秒杀抢购场景)")
    @PostMapping("/secskill")
    public ResponseEntity reduceInventory2(@RequestBody SeckillParams params) {
        log.info("抢到了id为{}的商品",params.getGoodsSkuId());
        try {
            Thread.sleep(1500L);//模拟耗时操作
        } catch (InterruptedException e) {
        }
        return new ResponseEntity<String>("000000", true, "抢购成功!");
    }

}
```


