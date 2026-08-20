package com.mkt.task.convert;

import com.mkt.task.command.TaskActionCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.domain.ActionMerger;
import com.mkt.task.domain.StepTypes;
import com.mkt.task.response.PlatformActionView;
import com.mkt.task.response.RewardPreviewView;
import com.mkt.task.response.StepPreviewView;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotViews {

    private SnapshotViews() {}

    public static RewardPreviewView rewardPreview(SnapshotContent snapshot) {
        if (snapshot == null || snapshot.steps() == null) {
            return new RewardPreviewView("", 0);
        }
        int total = 0;
        String first = "";
        for (TaskStepCommand step : snapshot.steps()) {
            if (StepTypes.REWARD.equals(step.type())) {
                total++;
                if (first.isEmpty()) {
                    first = step.name() == null ? "" : step.name();
                }
            }
        }
        return new RewardPreviewView(first, total);
    }

    public static List<StepPreviewView> stepsPreview(SnapshotContent snapshot) {
        if (snapshot == null || snapshot.steps() == null) {
            return List.of();
        }
        List<StepPreviewView> views = new ArrayList<>(snapshot.steps().size());
        for (TaskStepCommand step : snapshot.steps()) {
            views.add(new StepPreviewView(step.seq(), step.name(), step.type(), step.progressTarget()));
        }
        return views;
    }

    public static PlatformActionView action(SnapshotContent snapshot, String stepCode, String platform) {
        if (snapshot == null) {
            return null;
        }
        TaskActionCommand merged = ActionMerger.merge(snapshot.actions(), stepCode, platform);
        return new PlatformActionView(merged.actionType(), ActionMerger.params(merged), merged.buttonText());
    }

    public static TaskStepCommand step(SnapshotContent snapshot, String code) {
        if (snapshot == null || snapshot.steps() == null || code == null) {
            return null;
        }
        for (TaskStepCommand step : snapshot.steps()) {
            if (code.equals(step.code())) {
                return step;
            }
        }
        return null;
    }
}
