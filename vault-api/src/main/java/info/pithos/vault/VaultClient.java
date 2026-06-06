package info.pithos.vault;

import info.pithos.runtime.core.context.ServiceLifeCycle;
import info.pithos.runtime.model.protocol.http.Context.RequestContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface VaultClient extends ServiceLifeCycle {

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
