package com.mkt.ad.support;

/** Appendix A ad.* defaults. Mutable for tests. */
public class AdSettings {

    public static final int DEFAULT_DAILY_LIMIT = 10;
    public static final int DEFAULT_POPUP_COOLDOWN_SECONDS = 600;
    public static final int DEFAULT_SPLASH_SECONDS = 3;
    public static final int DEFAULT_CAROUSEL_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_CAROUSEL_MAX_ITEMS = 5;

    private int dailyImpressionLimit = DEFAULT_DAILY_LIMIT;
    private int popupCooldownSeconds = DEFAULT_POPUP_COOLDOWN_SECONDS;
    private int splashDurationSeconds = DEFAULT_SPLASH_SECONDS;
    private int carouselIntervalSeconds = DEFAULT_CAROUSEL_INTERVAL_SECONDS;
    private int carouselMaxItems = DEFAULT_CAROUSEL_MAX_ITEMS;

    public int dailyImpressionLimit() {
        return dailyImpressionLimit;
    }

    public void setDailyImpressionLimit(int dailyImpressionLimit) {
        this.dailyImpressionLimit = dailyImpressionLimit;
    }

    public int popupCooldownSeconds() {
        return popupCooldownSeconds;
    }

    public void setPopupCooldownSeconds(int popupCooldownSeconds) {
        this.popupCooldownSeconds = popupCooldownSeconds;
    }

    public int splashDurationSeconds() {
        return splashDurationSeconds;
    }

    public void setSplashDurationSeconds(int splashDurationSeconds) {
        this.splashDurationSeconds = splashDurationSeconds;
    }

    public int carouselIntervalSeconds() {
        return carouselIntervalSeconds;
    }

    public void setCarouselIntervalSeconds(int carouselIntervalSeconds) {
        this.carouselIntervalSeconds = carouselIntervalSeconds;
    }

    public int carouselMaxItems() {
        return carouselMaxItems;
    }

    public void setCarouselMaxItems(int carouselMaxItems) {
        this.carouselMaxItems = carouselMaxItems;
    }
}
