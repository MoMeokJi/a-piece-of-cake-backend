package com.momeokji.aiDiarybackend.config;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MusicConfig {

    @Bean
    public JsonFactory jsonFactory() {
        return new JacksonFactory();
    }

    @Bean
    public YouTube youtube(JsonFactory jsonFactory) {
        return new YouTube.Builder(
                new NetHttpTransport(),
                jsonFactory,
                request->{}
        ).setApplicationName("aiDiary->YoutubeService").build();
    }
}
