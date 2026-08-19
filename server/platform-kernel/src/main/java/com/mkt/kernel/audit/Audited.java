package com.mkt.kernel.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a non-GET {@code /admin/**} write. AOP writes {@code audit.log} unless already marked
 * ({@link AuditOnce}). GET and {@code /api/common/**} / {@code /internal/**} MUST NOT use this
 * (design §6.5 / 05-security §3).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String module();

    String action();
}
