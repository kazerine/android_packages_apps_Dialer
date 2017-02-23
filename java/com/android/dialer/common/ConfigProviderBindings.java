/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.dialer.common;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

/** Accessor for getting a {@link ConfigProvider}. */
public class ConfigProviderBindings {

  private static ConfigProvider configProvider;

  public static ConfigProvider get(@NonNull Context context) {
    Assert.isNotNull(context);
    if (configProvider != null) {
      return configProvider;
    }

    Context application = context.getApplicationContext();
    if (application instanceof ConfigProviderFactory) {
      configProvider = ((ConfigProviderFactory) application).getConfigProvider();
    }

    if (configProvider == null) {
      configProvider = new ConfigProviderStub();
    }

    return configProvider;
  }

  @VisibleForTesting
  public static void setForTesting(@Nullable ConfigProvider configProviderForTesting) {
    configProvider = configProviderForTesting;
  }

  private static class ConfigProviderStub implements ConfigProvider {
    @Override
    public String getString(String key, String defaultValue) {
      return defaultValue;
    }

    @Override
    public long getLong(String key, long defaultValue) {
      return defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return defaultValue;
    }
  }
}
