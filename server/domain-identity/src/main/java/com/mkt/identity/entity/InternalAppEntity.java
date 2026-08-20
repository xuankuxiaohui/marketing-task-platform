package com.mkt.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_internal_app")
public class InternalAppEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String appId;
    private String appName;
    private String secretCipher;
    private String prevSecretCipher;
    private LocalDateTime prevExpireAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getSecretCipher() {
        return secretCipher;
    }

    public void setSecretCipher(String secretCipher) {
        this.secretCipher = secretCipher;
    }

    public String getPrevSecretCipher() {
        return prevSecretCipher;
    }

    public void setPrevSecretCipher(String prevSecretCipher) {
        this.prevSecretCipher = prevSecretCipher;
    }

    public LocalDateTime getPrevExpireAt() {
        return prevExpireAt;
    }

    public void setPrevExpireAt(LocalDateTime prevExpireAt) {
        this.prevExpireAt = prevExpireAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean enabled() {
        return "ENABLED".equals(status);
    }
}
