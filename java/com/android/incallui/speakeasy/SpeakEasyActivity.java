/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.incallui.speakeasy;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;

/** Activity to for SpeakEasy component. */
public class SpeakEasyActivity extends FragmentActivity {

  private SpeakEasy speakEasy;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    speakEasy = SpeakEasyComponent.get(this).speakEasy();
    setContentView(R.layout.activity_speakeasy);
    Fragment speakEasyFragment =
        speakEasy.getSpeakEasyFragment("", "John Snow", SystemClock.elapsedRealtime());
    if (speakEasyFragment != null) {
      getSupportFragmentManager()
          .beginTransaction()
          .add(R.id.fragment_speakeasy, speakEasyFragment)
          .commit();
    }
    getWindow().setStatusBarColor(getColor(R.color.speakeasy_status_bar_color));
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  }
}
