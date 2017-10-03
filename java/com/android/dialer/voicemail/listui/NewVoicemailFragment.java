/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.dialer.voicemail.listui;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.dialer.common.LogUtil;
import com.android.dialer.voicemail.datasources.VoicemailData;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fragment for Dialer Voicemail Tab. */
public final class NewVoicemailFragment extends Fragment {
  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.new_voicemail_call_log_fragment, container, false);
    RecyclerView recyclerView =
        (RecyclerView) view.findViewById(R.id.new_voicemail_call_log_recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

    // TODO(uabdullah): To be removed once we hook up the UI to the voicemail backend
    List<VoicemailData> voicemailData = new ArrayList<>();
    Random rand = new Random();
    for (int i = 0; i < 50; i++) {
      VoicemailData mocked =
          VoicemailData.builder()
              .setName("Fatima Abdullah " + i)
              .setLocation("San Francisco, CA")
              .setDate("March " + (rand.nextInt(30) + 1))
              .setDuration("00:" + (rand.nextInt(50) + 10))
              .setTranscription(
                  "This is a transcription text message that literally means nothing.")
              .build();
      voicemailData.add(mocked);
    }

    LogUtil.i("onCreateView", "size of input:" + voicemailData.size());
    recyclerView.setAdapter(new NewVoicemailCallLogAdapter(voicemailData));
    return view;
  }
}
