/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.performance.backend;

import java.net.InetSocketAddress;
import java.util.Optional;

import com.google.common.collect.ImmutableSet;
import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfig;
import com.palantir.atlasdb.cassandra.ImmutableCassandraCredentialsConfig;
import com.palantir.atlasdb.cassandra.ImmutableCassandraKeyValueServiceConfig;
import com.palantir.atlasdb.config.ImmutableLeaderConfig;
import com.palantir.atlasdb.keyvalue.cassandra.CassandraKeyValueServiceImpl;
import com.palantir.atlasdb.spi.KeyValueServiceConfig;

public class CassandraKeyValueServiceInstrumentation extends KeyValueServiceInstrumentation {

    public CassandraKeyValueServiceInstrumentation() {
        super(9160, "cassandra-docker-compose.yml");
    }

    @Override
    public KeyValueServiceConfig getKeyValueServiceConfig(InetSocketAddress addr) {
        return ImmutableCassandraKeyValueServiceConfig.builder()
                .addServers(addr)
                .poolSize(20)
                .keyspace("atlasdb")
                .credentials(ImmutableCassandraCredentialsConfig.builder()
                        .username("cassandra")
                        .password("cassandra")
                        .build())
                .ssl(false)
                .replicationFactor(1)
                .mutationBatchCount(10000)
                .mutationBatchSizeBytes(10000000)
                .fetchBatchCount(1000)
                .autoRefreshNodes(false)
                .build();
    }

    @Override
    public boolean canConnect(InetSocketAddress addr) {
        return CassandraKeyValueServiceImpl.createForTesting(
                (CassandraKeyValueServiceConfig) getKeyValueServiceConfig(addr),
                Optional.of(ImmutableLeaderConfig.builder()
                        .quorumSize(1)
                        .localServer(addr.getHostString())
                        .leaders(ImmutableSet.of(addr.getHostString()))
                        .build()))
                .isInitialized();
    }

    @Override
    public String toString() {
        return "CASSANDRA";
    }
}
