package info.pithos.vault;

import info.pithos.runtime.core.context.ApplicationContext;
import info.pithos.runtime.core.util.Util;
import info.pithos.runtime.model.protocol.http.Context.RequestContext;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractVaultClient implements VaultClient {

    protected final ApplicationContext context;

    protected AbstractVaultClient(ApplicationContext context) {
        if (context == null) throw new IllegalArgumentException("context = null");
        this.context = context;
    }

    protected String createSecretPath(RequestContext requestContext, String name) {
        return Util.createKey(requestContext, createSecretPathName(name));
    }

    protected <T> CompletableFuture<T> submitAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, context.getSystemContext().getForkJoinExecutor());
    }

    protected String createSecretPathName(String name) {
        return context.getSystemContext().getServiceName() + ":" + name;
    }
}
