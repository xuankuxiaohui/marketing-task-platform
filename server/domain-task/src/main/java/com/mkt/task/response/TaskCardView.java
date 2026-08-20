package com.mkt.task.response;

public record TaskCardView(
        long taskId,
        String taskCode,
        String name,
        String category,
        String iconUrl,
        String badgeText,
        RewardPreviewView rewardPreview,
        String userStatus,
        int sortWeight) {}
