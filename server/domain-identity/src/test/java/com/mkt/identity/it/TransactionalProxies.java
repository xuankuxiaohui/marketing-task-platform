package com.mkt.identity.it;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Class-based Spring TX proxy so {@code @Transactional} on concrete services is honored in tests. */
public final class TransactionalProxies {

    private TransactionalProxies() {}

    public static <T> T proxy(T target, PlatformTransactionManager transactionManager) {
        ProxyFactory factory = new ProxyFactory();
        factory.setTarget(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        @SuppressWarnings("unchecked")
        T proxy = (T) factory.getProxy(target.getClass().getClassLoader());
        return proxy;
    }
}
