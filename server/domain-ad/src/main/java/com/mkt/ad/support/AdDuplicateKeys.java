package com.mkt.ad.support;

import java.sql.SQLIntegrityConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;

public final class AdDuplicateKeys {

    private AdDuplicateKeys() {}

    public static boolean duplicate(Throwable ex) {
        while (ex != null) {
            if (ex instanceof DuplicateKeyException || ex instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            String message = ex.getMessage();
            if (message != null && message.contains("Duplicate")) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    public static DuplicateKeyException wrap(String uk, RuntimeException ex) {
        return new DuplicateKeyException(uk, ex);
    }
}
