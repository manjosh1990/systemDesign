package com.manjoshlabs.com.sharding.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.Contact;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Database Sharding POC API",
        version = "1.0",
        description = "This API demonstrates database sharding using Apache ShardingSphere and PostgreSQL. It automatically routes requests to different databases based on the user ID (Shard Key).",
        contact = @Contact(name = "manjosh.1990@yahoo.com")
    )
)
public class OpenApiConfig {
}
