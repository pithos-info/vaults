package info.pithos.vault.hashicorp;

import info.pithos.vault.VaultClient;
import info.pithos.runtime.core.context.ApplicationContext;
import info.pithos.runtime.core.context.ServiceModule;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class HashiCorpVaultModule extends ServiceModule {

    private HashiCorpVaultClient client;

    public HashiCorpVaultModule(ApplicationContext context) {
        super(context);
    }

    @Override
    public boolean init() {
        if (this.initialized.compareAndSet(false, true)) {
            this.client = new HashiCorpVaultClient(this.getApplicationContext());
        }
        return this.initialized.get();
    }

    @Override
    protected void configure() {
        super.configure();
        super.bind(VaultClient.class).toInstance(this.client);
        super.bind(HashiCorpVaultClient.class).toInstance(this.client);
    }

    @Override
    public CompletableFuture<Boolean> start(long timeout, TimeUnit unit) {
        return client.start(timeout, unit);
    }

    @Override
    public CompletableFuture<Boolean> shutdown(long timeout, TimeUnit unit) {
        return client.shutdown(timeout, unit);
    }
}
