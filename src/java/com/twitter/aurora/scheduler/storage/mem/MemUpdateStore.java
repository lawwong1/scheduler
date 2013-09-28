/*
 * Copyright 2013 Twitter, Inc.
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
package com.twitter.aurora.scheduler.storage.mem;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;

import com.twitter.aurora.gen.JobKey;
import com.twitter.aurora.gen.JobUpdateConfiguration;
import com.twitter.aurora.gen.Lock;
import com.twitter.aurora.gen.LockKey;
import com.twitter.aurora.scheduler.base.JobKeys;
import com.twitter.aurora.scheduler.storage.UpdateStore;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An in-memory update store.
 */
class MemUpdateStore implements UpdateStore.Mutable {

  private static final Function<JobUpdateConfiguration, JobUpdateConfiguration> DEEP_COPY_CONFIG =
      Util.deepCopier();
  private static final Function<Lock, Lock> DEEP_COPY_LOCK = Util.deepCopier();

  private final Map<JobKey, JobUpdateConfiguration> configs = Maps.newConcurrentMap();
  private final Map<LockKey, Lock> locks = Maps.newConcurrentMap();

  private JobKey key(JobKey jobKey) {
    return JobKeys.assertValid(jobKey).deepCopy();
  }

  private JobKey key(JobUpdateConfiguration config) {
    checkNotNull(config);

    return key(config.getJobKey());
  }

  @Override
  public void saveJobUpdateConfig(JobUpdateConfiguration config) {
    configs.put(key(config), DEEP_COPY_CONFIG.apply(config));
  }

  @Override
  public void removeShardUpdateConfigs(JobKey jobKey) {
    configs.remove(jobKey);
  }

  @Override
  public void deleteShardUpdateConfigs() {
    configs.clear();
  }

  @Override
  public void saveLock(Lock lock) {
    locks.put(lock.getKey(), DEEP_COPY_LOCK.apply(lock));
  }

  @Override
  public void removeLock(LockKey lockKey) {
    locks.remove(lockKey);
  }

  @Override
  public void deleteLocks() {
    locks.clear();
  }

  @Override
  public Optional<JobUpdateConfiguration> fetchJobUpdateConfig(JobKey jobKey) {
    return Optional.fromNullable(configs.get(key(jobKey)))
        .transform(DEEP_COPY_CONFIG);
  }

  @Override
  public Set<JobUpdateConfiguration> fetchUpdateConfigs(String role) {
    return FluentIterable.from(configs.values())
        .filter(hasRole(role))
        .transform(DEEP_COPY_CONFIG)
        .toSet();
  }

  @Override
  public Set<String> fetchUpdatingRoles() {
    return FluentIterable.from(configs.values())
        .transform(GET_ROLE)
        .toSet();
  }

  @Override
  public Set<Lock> fetchLocks() {
    return FluentIterable.from(locks.values())
        .transform(DEEP_COPY_LOCK)
        .toSet();
  }

  @Override
  public Optional<Lock> fetchLock(LockKey lockKey) {
    return Optional.fromNullable(locks.get(lockKey)).transform(DEEP_COPY_LOCK);
  }

  private static final Function<JobUpdateConfiguration, String> GET_ROLE =
      new Function<JobUpdateConfiguration, String>() {
        @Override public String apply(JobUpdateConfiguration config) {
          return config.getJobKey().getRole();
        }
      };

  private static Predicate<JobUpdateConfiguration> hasRole(String role) {
    checkNotNull(role);

    return Predicates.compose(Predicates.equalTo(role), GET_ROLE);
  }
}
