package br.com.oficina.adapter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oficina.security.serverless-jwt")
public record ServerlessJwtProperties(String publicKey, String issuer, String audience) {}
