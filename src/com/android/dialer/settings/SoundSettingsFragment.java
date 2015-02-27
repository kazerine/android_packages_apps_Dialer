/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.dialer.settings;

import android.content.Context;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.dialer.R;
import com.android.phone.common.util.SettingsUtil;

import java.lang.Boolean;
import java.lang.CharSequence;
import java.lang.Object;
import java.lang.Override;
import java.lang.Runnable;
import java.lang.String;
import java.lang.Thread;

public class SoundSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final int NO_DTMF_TONE = 0;
    private static final int PLAY_DTMF_TONE = 1;

    private static final int NO_VIBRATION_FOR_CALLS = 0;
    private static final int DO_VIBRATION_FOR_CALLS = 1;

    private static final int MSG_UPDATE_RINGTONE_SUMMARY = 1;

    private Context mContext;

    private Preference mRingtonePreference;
    private CheckBoxPreference mVibrateWhenRinging;
    private CheckBoxPreference mPlayDtmfTone;

    private final Runnable mRingtoneLookupRunnable = new Runnable() {
        @Override
        public void run() {
            updateRingtonePreferenceSummary();
        }
    };

    private final Handler mRingtoneLookupComplete = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_RINGTONE_SUMMARY:
                    mRingtonePreference.setSummary((CharSequence) msg.obj);
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getActivity().getApplicationContext();

        addPreferencesFromResource(R.xml.sound_settings);

        mRingtonePreference = findPreference(mContext.getString(R.string.ringtone_preference_key));
        mVibrateWhenRinging = (CheckBoxPreference) findPreference(
                mContext.getString(R.string.vibrate_on_preference_key));
        mPlayDtmfTone = (CheckBoxPreference) findPreference(
                mContext.getString(R.string.play_dtmf_preference_key));

        if (hasVibrator()) {
            mVibrateWhenRinging.setOnPreferenceChangeListener(this);
        } else {
            getPreferenceScreen().removePreference(mVibrateWhenRinging);
            mVibrateWhenRinging = null;
        }

        mPlayDtmfTone.setOnPreferenceChangeListener(this);
        mPlayDtmfTone.setChecked(shouldPlayDtmfTone());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mVibrateWhenRinging != null) {
            mVibrateWhenRinging.setChecked(shouldVibrateWhenRinging());
        }

        // Lookup the ringtone name asynchronously.
        new Thread(mRingtoneLookupRunnable).start();
    }

    /**
     * Supports onPreferenceChangeListener to look for preference changes.
     *
     * @param preference The preference to be changed
     * @param objValue The value of the selection, NOT its localized display value.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mVibrateWhenRinging) {
            boolean doVibrate = (Boolean) objValue;
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING,
                    doVibrate ? DO_VIBRATION_FOR_CALLS : NO_VIBRATION_FOR_CALLS);
        }
        return true;
    }

    /**
     * Click listener for toggle events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mPlayDtmfTone) {
            Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.DTMF_TONE_WHEN_DIALING,
                    mPlayDtmfTone.isChecked() ? PLAY_DTMF_TONE : NO_DTMF_TONE);
        }
        return true;
    }

    /**
     * Updates the summary text on the ringtone preference with the name of the ringtone.
     */
    private void updateRingtonePreferenceSummary() {
        SettingsUtil.updateRingtoneName(
                mContext,
                mRingtoneLookupComplete,
                RingtoneManager.TYPE_RINGTONE,
                mRingtonePreference.getKey(),
                MSG_UPDATE_RINGTONE_SUMMARY);
    }

    /**
     * Obtain the value for "vibrate when ringing" setting. The default value is false.
     *
     * Watch out: if the setting is missing in the device, this will try obtaining the old
     * "vibrate on ring" setting from AudioManager, and save the previous setting to the new one.
     */
    private boolean shouldVibrateWhenRinging() {
        int vibrateWhenRingingSetting = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING,
                NO_VIBRATION_FOR_CALLS);
        return hasVibrator() && (vibrateWhenRingingSetting == DO_VIBRATION_FOR_CALLS);
    }

    /**
     * Obtains the value for dialpad/DTMF tones. The default value is true.
     */
    private boolean shouldPlayDtmfTone() {
        int dtmfToneSetting = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING,
                PLAY_DTMF_TONE);
        return dtmfToneSetting == PLAY_DTMF_TONE;
    }

    /**
     * Whether the device hardware has a vibrator.
     */
    private boolean hasVibrator() {
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        return vibrator != null && vibrator.hasVibrator();
    }
}
