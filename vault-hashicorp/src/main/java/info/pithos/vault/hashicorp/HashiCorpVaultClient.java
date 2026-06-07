package info.pithos.vault.hashicorp;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.response.LogicalResponse;
import info.pithos.vault.AbstractVaultClient;
import info.pithos.runtime.core.context.ApplicationContext;
import info.pithos.runtime.model.config.Config.HashiCorpVaultConfigs;
import info.pithos.runtime.model.protocol.Context.RequestContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HashiCorpVaultClient extends AbstractVaultClient {

    private final HashiCorpVaultConfigs configs;
    private volatile Vault vault;

    public HashiCorpVaultClient(ApplicationContext context) {
        super(context);
        this.configs = context.getSystemContext().getConfigMap().getHashiCorpVaultConfigs();
    }

    @Override
    public CompletableFuture<Boolean> start(long timeout, TimeUnit unit) {
        return submitAsync(() -> {
            VaultConfig config = new VaultConfig().address(configs.getAddress()).token(configs.getToken());
            if (!configs.getNamespace().isEmpty()) {
                config.nameSpace(configs.getNamespace());
            }
            if (configs.getTimeoutMs() > 0) {
                int secs = (int) TimeUnit.MILLISECONDS.toSeconds(configs.getTimeoutMs());
                config.openTimeout(secs).readTimeout(secs);
            }
            vault = new Vault(config.build());
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> shutdown(long timeout, TimeUnit unit) {
        return submitAsync(() -> {
            vault = null;
            return true;
        });
    }

    @Override
    public CompletableFuture<String> getSecret(RequestContext requestContext, String name) {
        return submitAsync(() -> {
            LogicalResponse response = vault().logical().read(kvPath(requestContext, name));
            Map<String, String> data = response.getData();
            return data != null ? data.get("value") : null;
        });
    }

    @Override
    public CompletableFuture<String> getSecret(RequestContext requestContext, String name, String version) {
        // KV v1 does not support versioning; version is ignored
        return getSecret(requestContext, name);
    }

    @Override
    public CompletableFuture<byte[]> getSecretBytes(RequestContext requestContext, String name) {
        return getSecret(requestContext, name)
            .thenApply(v -> v != null ? v.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null);
    }

    @Override
    public CompletableFuture<byte[]> getSecretBytes(RequestContext requestContext, String name, String version) {
        return getSecretBytes(requestContext, name);
    }

    @Override
    public CompletableFuture<Void> setSecret(RequestContext requestContext, String name, String value) {
        return submitAsync(() -> {
            vault().logical().write(kvPath(requestContext, name), Map.of("value", value));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> setSecretBytes(RequestContext requestContext, String name, byte[] value) {
        return setSecret(requestContext, name, new String(value, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public CompletableFuture<Boolean> deleteSecret(RequestContext requestContext, String name) {
        return submitAsync(() -> {
            vault().logical().delete(kvPath(requestContext, name));
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> secretExists(RequestContext requestContext, String name) {
        return submitAsync(() -> {
            try {
                LogicalResponse resp = vault().logical().read(kvPath(requestContext, name));
                Map<String, String> data = resp.getData();
                return data != null && !data.isEmpty();
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> listSecrets(RequestContext requestContext, String prefix) {
        return submitAsync(() -> {
            String listPath = mountPath() + (prefix != null && !prefix.isEmpty() ? "/" + prefix : "");
            LogicalResponse resp = vault().logical().list(listPath);
            List<String> keys = resp.getListData();
            return keys != null ? keys : Collections.emptyList();
        });
    }

    private String kvPath(RequestContext requestContext, String name) {
        return mountPath() + "/" + createSecretPath(requestContext, name);
    }

    private String mountPath() {
        String mp = configs.getMountPath();
        return (mp != null && !mp.isEmpty()) ? mp : "secret";
    }

    private Vault vault() {
        Vault v = vault;
        if (v == null)
            throw new IllegalStateException(getClass().getSimpleName() + " not started — call start() first");
        return v;
    }
}
