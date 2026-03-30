package kongju.musicchartservice.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        // 키를 어떻게 문자열로 바꿀지 정하기
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        // 값을 어떻게 저장할지
        JacksonJsonRedisSerializer<Object>  jacksonJsonRedisSerializer = new JacksonJsonRedisSerializer<>(Object.class);

        // 비동기용 설정 묶음
        RedisSerializationContext<String, Object> context = RedisSerializationContext
                .<String, Object>newSerializationContext(stringRedisSerializer)
                .value(jacksonJsonRedisSerializer)
                .build();

        return  new ReactiveRedisTemplate<>(factory, context);
    }
}
