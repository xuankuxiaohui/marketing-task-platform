package com.mkt.identity.it;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Records commit vs rollback so tests can prove login rejection is not rolled back. */
public final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    private int commits;
    private int rollbacks;

    public RecordingTransactionManager() {
        setNestedTransactionAllowed(true);
    }

    public int commits() {
        return commits;
    }

    public int rollbacks() {
        return rollbacks;
    }

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        rollbacks++;
    }
}
