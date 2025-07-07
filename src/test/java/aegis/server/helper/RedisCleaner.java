package aegis.server.helper;

import jakarta.annotation.PreDestroy;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RedisCleaner implements InitializingBean {

    private final RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;

    @Autowired
    public RedisCleaner(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public void afterPropertiesSet() {
        connection = redisClient.connect();
        syncCommands = connection.sync();
    }

    public void clean() {
        syncCommands.flushdb();
    }

    @PreDestroy
    public void closeConnection() {
        if (connection != null) {
            connection.close();
        }
    }
}
