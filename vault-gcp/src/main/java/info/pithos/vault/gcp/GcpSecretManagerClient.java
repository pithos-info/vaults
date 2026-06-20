/*
 * Copyright 2026 Pithos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package info.pithos.vault.gcp;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.ProjectName;
import com.google.cloud.secretmanager.v1.Replication;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.protobuf.ByteString;
import info.pithos.vault.AbstractVaultClient;
import info.pithos.runtime.core.context.ApplicationContext;
import info.pithos.runtime.model.config.Config.GcpSecretManagerConfigs;
import info.pithos.runtime.model.protocol.Context.RequestContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GcpSecretManagerClient extends AbstractVaultClient {

    private final GcpSecretManagerConfigs configs;
    private volatile SecretManagerServiceClient secretClient;

    public GcpSecretManagerClient(ApplicationContext context) {
        super(context);
        this.configs = context.getSystemContext().getConfigMap().getGcpSecretManagerConfigs();
    }

    @Override
    public CompletableFuture<Boolean> start(long timeout, TimeUnit unit) {
        return submitAsync(() -> {
            secretClient = SecretManagerServiceClient.create();
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> shutdown(long timeout, TimeUnit unit) {
        return submitAsync(() -> {
            SecretManagerServiceClient c = secretClient;
            secretClient = null;
            if (c != null) c.close();
            return true;
        });
    }

    @Override
    public CompletableFuture<String> getSecret(RequestContext requestContext, String name) {
        return getSecret(requestContext, name, "latest");
    }

    @Override
    public CompletableFuture<String> getSecret(RequestContext requestContext, String name, String version) {
        return submitAsync(() -> {
            String ver = (version != null && !version.isEmpty()) ? version : "latest";
            SecretVersionName versionName = SecretVersionName.of(configs.getProjectId(), toSecretId(requestContext, name), ver);
            AccessSecretVersionResponse response = client().accessSecretVersion(versionName);
            return response.getPayload().getData().toStringUtf8();
        });
    }

    @Override
    public CompletableFuture<byte[]> getSecretBytes(RequestContext requestContext, String name) {
        return getSecretBytes(requestContext, name, "latest");
    }

    @Override
    public CompletableFuture<byte[]> getSecretBytes(RequestContext requestContext, String name, String version) {
        return submitAsync(() -> {
            String ver = (version != null && !version.isEmpty()) ? version : "latest";
            SecretVersionName versionName = SecretVersionName.of(configs.getProjectId(), toSecretId(requestContext, name), ver);
            AccessSecretVersionResponse response = client().accessSecretVersion(versionName);
            return response.getPayload().getData().toByteArray();
        });
    }

    @Override
    public CompletableFuture<Void> setSecret(RequestContext requestContext, String name, String value) {
        return setSecretBytes(requestContext, name, value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public CompletableFuture<Void> setSecretBytes(RequestContext requestContext, String name, byte[] value) {
        return submitAsync(() -> {
            String secretId = toSecretId(requestContext, name);
            ensureSecretExists(secretId);
            SecretName secretName = SecretName.of(configs.getProjectId(), secretId);
            SecretPayload payload = SecretPayload.newBuilder()
                .setData(ByteString.copyFrom(value))
                .build();
            client().addSecretVersion(secretName, payload);
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteSecret(RequestContext requestContext, String name) {
        return submitAsync(() -> {
            SecretName secretName = SecretName.of(configs.getProjectId(), toSecretId(requestContext, name));
            client().deleteSecret(secretName);
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> secretExists(RequestContext requestContext, String name) {
        return submitAsync(() -> {
            try {
                SecretName secretName = SecretName.of(configs.getProjectId(), toSecretId(requestContext, name));
                client().getSecret(secretName);
                return true;
            } catch (com.google.api.gax.rpc.NotFoundException e) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> listSecrets(RequestContext requestContext, String prefix) {
        return submitAsync(() -> {
            ProjectName projectName = ProjectName.of(configs.getProjectId());
            String filter = (prefix != null && !prefix.isEmpty()) ? "name:" + sanitize(prefix) : "";
            List<String> names = new ArrayList<>();
            SecretManagerServiceClient.ListSecretsPagedResponse paged =
                filter.isEmpty()
                    ? client().listSecrets(projectName)
                    : client().listSecrets(
                        com.google.cloud.secretmanager.v1.ListSecretsRequest.newBuilder()
                            .setParent(projectName.toString())
                            .setFilter(filter)
                            .build());
            for (Secret secret : paged.iterateAll()) {
                String fullName = secret.getName();
                names.add(fullName.substring(fullName.lastIndexOf('/') + 1));
            }
            return Collections.unmodifiableList(names);
        });
    }

    // --- Helpers ---

    @Override
    protected String createSecretPath(RequestContext requestContext, String name) {
        return sanitize(super.createSecretPath(requestContext, name));
    }

    private String toSecretId(RequestContext requestContext, String name) {
        return createSecretPath(requestContext, name);
    }

    private void ensureSecretExists(String secretId) {
        try {
            client().getSecret(SecretName.of(configs.getProjectId(), secretId));
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            Secret secret = Secret.newBuilder()
                .setReplication(Replication.newBuilder()
                    .setAutomatic(Replication.Automatic.getDefaultInstance())
                    .build())
                .build();
            client().createSecret(configs.getProjectId(), secretId, secret);
        }
    }

    private static String sanitize(String path) {
        return path.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private SecretManagerServiceClient client() {
        SecretManagerServiceClient c = secretClient;
        if (c == null)
            throw new IllegalStateException(getClass().getSimpleName() + " not started — call start() first");
        return c;
    }
}
