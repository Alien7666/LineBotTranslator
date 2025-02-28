package com.linetranslate.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableMongoRepositories(basePackages = "com.linetranslate.bot.repository")
@Slf4j
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${mongodb.uri:${MONGODB_URI:mongodb://localhost:27017/linebot_translator}}")
    private String mongoUri;

    @Value("${mongodb.database:${MONGODB_DATABASE:linebot_translator}}")
    private String mongoDatabaseName;

    @Override
    protected String getDatabaseName() {
        return mongoDatabaseName;
    }

    @Override
    public MongoClient mongoClient() {
        try {
            log.info("嘗試連接到 MongoDB: {}", mongoUri);
            ConnectionString connectionString = new ConnectionString(mongoUri);
            MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .build();

            return MongoClients.create(mongoClientSettings);
        } catch (Exception e) {
            log.error("無法連接到 MongoDB: {}", e.getMessage());
            // 返回一個假的客戶端，讓應用程式能夠啟動，但功能會受限
            return null;
        }
    }
}