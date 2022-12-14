package com.cloud.gateway.service;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

import static com.cloud.gateway.config.NacosGatewayConfig.*;

/**
 * @author king
 * @version 1.0
 * @className NacosDynamicRouteService
 * @description TODO
 * @date 2022/8/28
 */

@Service
@Slf4j
@DependsOn({"nacosGatewayConfig"})
public class NacosDynamicRouteService {


    @Autowired
    private NacosRefreshRouteService nacosRefreshRouteService;


    private ConfigService configService;


    @Autowired
    private ObjectMapper objectMapper;


    @PostConstruct
    public void init() {
        log.info(">>>>>>>>>> init gateway route <<<<<<<<<<");
        configService = initConfigService();
        if (null == configService) {
            log.error(">>>>>>> init the ConfigService failed!!!");
        }
        String configInfo = null;
        try {
            configInfo = configService.getConfig(NACOS_ROUTE_DATA_ID, NACOS_ROUTE_GROUP, DEFAULT_TIMEOUT);
            log.info(">>>>>>>>> get the gateway configInfo: {}", configInfo);
            List<RouteDefinition> routeDefinitions = objectMapper.readValue(configInfo, new TypeReference<List<RouteDefinition>>() {
            });


            for (RouteDefinition definition : routeDefinitions) {
                log.info(">>>>>>>>>> load route : {}", definition.toString());
                nacosRefreshRouteService.add(definition);
            }
        } catch (NacosException | JsonProcessingException e) {
            e.printStackTrace();
        }
        dynamicRouteByNacosListener(NACOS_ROUTE_DATA_ID, NACOS_ROUTE_GROUP);
    }


    private void dynamicRouteByNacosListener(String dataId, String group) {
        try {
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    log.info("-------------------getExecutor-------------------");
                    return null;
                }


                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info(">>>>>>>>> listened configInfo change: {}", configInfo);
                    List<RouteDefinition> routeDefinitions = null;
                    try {
                        routeDefinitions = objectMapper.readValue(configInfo, new TypeReference<List<RouteDefinition>>() {
                        });
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    nacosRefreshRouteService.updateList(routeDefinitions);
                }
            });
        } catch (NacosException e) {
            e.printStackTrace();
        }
    }


    private ConfigService initConfigService() {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", NACOS_SERVER_ADDR);
        properties.setProperty("namespace", NACOS_NAMESPACE);
        try {
            return NacosFactory.createConfigService(properties);
        } catch (NacosException e) {
            e.printStackTrace();
            return null;
        }
    }
}
