/**
 * Copyright 2017 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.keyvalue.dbkvs.impl;

import javax.annotation.Nullable;

import org.immutables.value.Value;

@Value.Immutable
abstract class Token {
    @Nullable
    abstract byte[] row();
    @Nullable
    abstract byte[] col();
    @Nullable
    abstract Long timestamp();
    abstract boolean shouldSkip();

    public static final Token INITIAL = ImmutableToken.builder().shouldSkip(false).build();
}
