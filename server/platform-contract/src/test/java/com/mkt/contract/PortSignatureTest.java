package com.mkt.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Compile-time lock of §2.2.3 port method signatures. */
class PortSignatureTest {

    @Test
    void rewardPortGrantAndReadOnlySummary() {
        assertSignature(
                RewardPort.class,
                "grant",
                GrantResult.class,
                long.class,
                long.class,
                GrantSource.class,
                String.class,
                GrantContext.class);
        assertSignature(RewardPort.class, "userSummary", UserRewardSummary.class, long.class);
        assertSignature(RewardPort.class, "prizeEnabled", boolean.class, long.class);
        assertSignature(
                RewardPort.class,
                "consume",
                long.class,
                long.class,
                int.class,
                String.class,
                String.class,
                String.class);
        assertThat(declaredMethods(RewardPort.class))
                .containsExactlyInAnyOrder("grant", "userSummary", "prizeEnabled", "consume");
    }

    @Test
    void userAttributePortAttributesAndLockAndGet() {
        assertSignature(UserAttributePort.class, "attributes", UserAttributes.class, long.class);
        assertSignature(UserAttributePort.class, "lockAndGet", UserAttributes.class, long.class);
        assertThat(declaredMethods(UserAttributePort.class)).containsExactlyInAnyOrder("attributes", "lockAndGet");
    }

    @Test
    void riskCheckPortCheckAndReadOnlySummary() {
        assertSignature(RiskCheckPort.class, "check", RiskVerdict.class, RiskScene.class, RiskSubject.class);
        assertSignature(RiskCheckPort.class, "userSummary", UserRiskSummary.class, long.class);
        assertThat(declaredMethods(RiskCheckPort.class)).containsExactlyInAnyOrder("check", "userSummary");
    }

    @Test
    void taskReadPortHasOnlyInstanceCounts() {
        assertSignature(TaskReadPort.class, "instanceCounts", InstanceCounts.class, long.class);
        assertThat(declaredMethods(TaskReadPort.class)).containsExactly("instanceCounts");
        assertThat(declaredMethods(TaskReadPort.class))
                .noneMatch(name -> name.matches(".*(grant|save|update|delete|lock|write).*"));
    }

    private static void assertSignature(
            Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(type.getSimpleName() + "." + name + " missing"));
        assertThat(method.getReturnType()).isEqualTo(returnType);
        assertThat(method.getParameterTypes()).containsExactly(parameterTypes);
        assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
    }

    private static List<String> declaredMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).toList();
    }
}
