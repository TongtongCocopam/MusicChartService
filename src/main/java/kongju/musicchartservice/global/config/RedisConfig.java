package kongju.musicchartservice.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        // 키를 어떻게 문자열로 바꿀지 정하기
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        var valueSerializer = org.springframework.data.redis.serializer.RedisSerializer.json();

        // 비동기용 설정 묶음
        RedisSerializationContext<String, Object> context =
                RedisSerializationContext
                        .<String, Object>newSerializationContext(keySerializer)
                        .value(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}