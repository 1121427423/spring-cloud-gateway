package com.cloud.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
/**
 * @author king
 * @version 1.0
 * @className RedisDynamicRouteService
 * @description TODO
 * @date 2022/8/28
 */

@Slf4j
@Service
public class RedisDynamicRouteService {


    public static final String GATEWAY_ROUTES_PREFIX = "brian:sz_home:gateway_dynamic_route:";


    @Autowired
    private StringRedisTemplate redisTemplate;


    @Resource
    private ObjectMapper objectMapper;


    public Flux<RouteDefinition> getRouteDefinitions() {
        log.info(">>>>>>>>>> getRouteDefinitions <<<<<<<<<<");
        List<RouteDefinition> routeDefinitions = new ArrayList<>();
        redisTemplate.keys(GATEWAY_ROUTES_PREFIX+"*").stream().forEach(key -> {
            String rdStr = getStringBySer(key);
            RouteDefinition routeDefinition = null;
            try {
                routeDefinition = objectMapper.readValue(rdStr, RouteDefinition.class);
                log.info(">>>>>>>>>> load route : {}", routeDefinition.toString());
                routeDefinitions.add(routeDefinition);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }


        });
        return Flux.fromIterable(routeDefinitions);
    }

    /**
     * 通过使用redis默认的序列化获取String类型的值
     *
     * @param key 键
     * @return String类型的值
     */
    public String getStringBySer(String key) {
        try {
            return (String) redisTemplate.execute((RedisCallback<String>) connection -> {
                RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
                byte[] serialize = serializer.serialize(key);
                if (serialize == null) {
                    return null;
                }
                byte[] value = connection.get(serialize);
                return serializer.deserialize(value);
            });
        } catch (Exception e) {
            return null;
        }
    }


    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(routeDefinition -> {
            String rdStr = null;
            try {
                rdStr = objectMapper.writeValueAsString(routeDefinition);
                redisTemplate.opsForValue().set(GATEWAY_ROUTES_PREFIX + routeDefinition.getId(), rdStr);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }


            return Mono.empty();
        });
    }


    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> {
            if (redisTemplate.hasKey(GATEWAY_ROUTES_PREFIX + id)) {
                redisTemplate.delete(GATEWAY_ROUTES_PREFIX + id);
                return Mono.empty();
            }
            return Mono.defer(() -> Mono.error(new NotFoundException("routeDefinition not found, id is: " + id)));
        });
    }


    public Mono<Boolean> get(Mono<String> routeId) {
        return routeId.flatMap(id -> Mono.just(redisTemplate.hasKey(GATEWAY_ROUTES_PREFIX + id)));
    }
}
