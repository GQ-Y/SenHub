package com.hikvision.nvr.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;

/**
 * 配置加载器
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * 从类路径加载配置
     */
    public static Config load(String resourcePath) throws Exception {
        InputStream inputStream = ConfigLoader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new RuntimeException("配置文件未找到: " + resourcePath);
        }
        Config config = mapper.readValue(inputStream, Config.class);
        logger.info("配置文件加载成功: {}", resourcePath);
        return config;
    }

    /**
     * 从文件系统加载配置
     */
    public static Config loadFromFile(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new RuntimeException("配置文件不存在: " + filePath);
        }
        Config config = mapper.readValue(file, Config.class);
        logger.info("配置文件加载成功: {}", filePath);
        return config;
    }
}
