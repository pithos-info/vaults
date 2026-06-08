package info.pithos.vault.gcp;

import info.pithos.vault.VaultClient;
import info.pithos.runtime.core.context.ApplicationContext;
import info.pithos.runtime.core.context.ServiceModule;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class GcpSecretManagerModule extends ServiceModule {

    private GcpSecretManagerClient client;

    public GcpSecretManagerModule(ApplicationContext context) {
        super(context);
    }

    @Override
    public boolean init() {
        if (this.initialized.compareAndSet(false, true)) {
            this.client = new GcpSecretManagerClient(this.getApplicationContext());
        }
        return this.initialized.get();
    }

    @Override
    public CompletableFuture<Boolean> start(long timeout, TimeUnit unit) {
        return client.start(timeout, unit);
    }

    @Override
    public CompletableFuture<Boolean> shutdown(long timeout, TimeUnit unit) {
        return client.shutdown(timeout, unit);
    }

    @Override
    protected void configure() {
        super.configure();
        super.bind(VaultClient.class).toInstance(this.client);
        super.bind(GcpSecretManagerClient.class).toInstance(this.client);
    }
}
