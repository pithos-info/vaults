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

package info.pithos.vault;

import info.pithos.runtime.core.vault.VaultClient;
import info.pithos.runtime.model.protocol.Context.RequestContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Full vault interface — extends VaultClient (credential resolution) with CRUD
 * operations for tooling. Implementations inherit metrics and logging from
 * AbstractVaultClient for both credential resolution and secret management.
 */
public interface VaultStorage extends VaultClient {

  // --- Read ---

  CompletableFuture<String> getSecret(RequestContext requestContext, String name);

  CompletableFuture<String> getSecret(RequestContext requestContext, String name, String version);

  CompletableFuture<byte[]> getSecretBytes(RequestContext requestContext, String name);

  CompletableFuture<byte[]> getSecretBytes(RequestContext requestContext, String name, String version);

  // --- Write ---

  CompletableFuture<Void> setSecret(RequestContext requestContext, String name, String value);

  CompletableFuture<Void> setSecretBytes(RequestContext requestContext, String name, byte[] value);

  // --- Delete ---

  CompletableFuture<Boolean> deleteSecret(RequestContext requestContext, String name);

  // --- Query ---

  CompletableFuture<Boolean> secretExists(RequestContext requestContext, String name);

  /**
   * Lists secret names whose path starts with the given prefix (relative to the mount root).
   * Returns an empty list when no secrets match.
   */
  CompletableFuture<List<String>> listSecrets(RequestContext requestContext, String prefix);
}
