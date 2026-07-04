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

import info.pithos.runtime.core.metrics.InfraOperation;

public enum VaultOperation implements InfraOperation {
    GET_SECRET("vault.get.secret"),
    SET_SECRET("vault.set.secret"),
    DELETE_SECRET("vault.delete.secret"),
    SECRET_EXISTS("vault.secret.exists"),
    LIST_SECRETS("vault.list.secrets");

    private final String stem;

    VaultOperation(String stem) {
        this.stem = stem;
    }

    @Override
    public String stem() { return stem; }
}
