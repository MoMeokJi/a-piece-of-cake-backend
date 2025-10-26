package com.momeokji.aiDiarybackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

	@Bean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
		StringRedisTemplate t = new StringRedisTemplate(cf);
		t.setKeySerializer(new StringRedisSerializer());
		t.setValueSerializer(new StringRedisSerializer());
		return t;
	}
}
