package cache;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCache {
	
	private static final String RedisHostname = System.getProperty("REDIS_HOSTNAME");
    private static final String RedisKey = System.getProperty("REDIS_KEY");
    private static final int REDIS_PORT = Integer.parseInt(System.getProperty("REDIS_PORT", "6380"));
    private static final int REDIS_TIMEOUT = Integer.parseInt(System.getProperty("REDIS_TIMEOUT", "1000"));
    private static final boolean Redis_USE_TLS = Boolean.parseBoolean(System.getProperty("REDIS_USE_TLS", "true"));
	
	private static JedisPool instance;
	
	public synchronized static JedisPool getCachePool() {
		if( instance != null)
			return instance;
		
		var poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT, REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
		return instance;
	}
}
