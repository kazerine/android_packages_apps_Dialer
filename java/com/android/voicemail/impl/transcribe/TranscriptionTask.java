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
package com.android.voicemail.impl.transcribe;

import android.annotation.TargetApi;
import android.app.job.JobWorkItem;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.voicemail.impl.VvmLog;
import com.android.voicemail.impl.transcribe.TranscriptionService.JobCallback;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClient;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionClientFactory;
import com.android.voicemail.impl.transcribe.grpc.TranscriptionResponse;
import com.google.internal.communications.voicemailtranscription.v1.AudioFormat;
import com.google.internal.communications.voicemailtranscription.v1.TranscriptionStatus;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;

/**
 * Background task to get a voicemail transcription and update the database.
 *
 * <pre>
 * This task performs the following steps:
 *   1. Update the transcription-state in the database to 'in-progress'
 *   2. Create grpc client and transcription request
 *   3. Make synchronous or asynchronous grpc transcription request to backend server
 *     3a. On response
 *       Update the database with transcription (if successful) and new transcription-state
 *     3b. On network error
 *       If retry-count < max then increment retry-count and retry the request
 *       Otherwise update the transcription-state in the database to 'transcription-failed'
 *   4. Notify the callback that the work item is complete
 * </pre>
 */
public abstract class TranscriptionTask implements Runnable {
  private static final String TAG = "TranscriptionTask";

  private final Context context;
  private final JobCallback callback;
  private final JobWorkItem workItem;
  private final TranscriptionClientFactory clientFactory;
  private final Uri voicemailUri;
  private final TranscriptionDbHelper databaseHelper;
  protected final TranscriptionConfigProvider configProvider;
  protected ByteString audioData;
  protected AudioFormat encoding;

  static final String AMR_PREFIX = "#!AMR\n";

  /** Functional interface for sending requests to the transcription server */
  public interface Request {
    TranscriptionResponse getResponse(TranscriptionClient client);
  }

  TranscriptionTask(
      Context context,
      JobCallback callback,
      JobWorkItem workItem,
      TranscriptionClientFactory clientFactory,
      TranscriptionConfigProvider configProvider) {
    this.context = context;
    this.callback = callback;
    this.workItem = workItem;
    this.clientFactory = clientFactory;
    this.voicemailUri = getVoicemailUri(workItem);
    this.configProvider = configProvider;
    databaseHelper = new TranscriptionDbHelper(context, voicemailUri);
  }

  @Override
  public void run() {
    VvmLog.i(TAG, "run");
    if (readAndValidateAudioFile()) {
      updateTranscriptionState(VoicemailCompat.TRANSCRIPTION_IN_PROGRESS);
      transcribeVoicemail();
    } else {
      if (AudioFormat.AUDIO_FORMAT_UNSPECIFIED.equals(encoding)) {
        Logger.get(context)
            .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_VOICEMAIL_FORMAT_NOT_SUPPORTED);
      } else {
        Logger.get(context)
            .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_VOICEMAIL_INVALID_DATA);
      }
      updateTranscriptionState(VoicemailCompat.TRANSCRIPTION_FAILED);
    }
    ThreadUtil.postOnUiThread(
        () -> {
          callback.onWorkCompleted(workItem);
        });
  }

  protected abstract Pair<String, TranscriptionStatus> getTranscription();

  protected abstract DialerImpression.Type getRequestSentImpression();

  private void transcribeVoicemail() {
    VvmLog.i(TAG, "transcribeVoicemail");
    Pair<String, TranscriptionStatus> pair = getTranscription();
    String transcript = pair.first;
    TranscriptionStatus status = pair.second;
    if (!TextUtils.isEmpty(transcript)) {
      updateTranscriptionAndState(transcript, VoicemailCompat.TRANSCRIPTION_AVAILABLE);
      VvmLog.i(TAG, "transcribeVoicemail, got response");
      Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_SUCCESS);
    } else {
      VvmLog.i(TAG, "transcribeVoicemail, transcription unsuccessful, " + status);
      switch (status) {
        case FAILED_LANGUAGE_NOT_SUPPORTED:
          Logger.get(context)
              .logImpression(
                  DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_LANGUAGE_NOT_SUPPORTED);
          break;
        case FAILED_NO_SPEECH_DETECTED:
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_NO_SPEECH_DETECTED);
          break;
        case EXPIRED:
          Logger.get(context)
              .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_EXPIRED);
          break;
        default:
          Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_EMPTY);
          break;
      }
      updateTranscriptionAndState(transcript, VoicemailCompat.TRANSCRIPTION_FAILED);
    }
  }

  protected TranscriptionResponse sendRequest(Request request) {
    VvmLog.i(TAG, "sendRequest");
    TranscriptionClient client = clientFactory.getClient();
    for (int i = 0; i < configProvider.getMaxTranscriptionRetries(); i++) {
      VvmLog.i(TAG, "sendRequest, try: " + (i + 1));
      if (i == 0) {
        Logger.get(context).logImpression(getRequestSentImpression());
      } else {
        Logger.get(context).logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_REQUEST_RETRY);
      }

      TranscriptionResponse response = request.getResponse(client);
      if (response.hasRecoverableError()) {
        Logger.get(context)
            .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_RECOVERABLE_ERROR);
        backoff(i);
      } else {
        return response;
      }
    }

    Logger.get(context)
        .logImpression(DialerImpression.Type.VVM_TRANSCRIPTION_RESPONSE_TOO_MANY_ERRORS);
    return null;
  }

  private static void backoff(int retryCount) {
    VvmLog.i(TAG, "backoff, count: " + retryCount);
    long millis = (1L << retryCount) * 1000;
    sleep(millis);
  }

  protected static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      VvmLog.w(TAG, "interrupted");
      Thread.currentThread().interrupt();
    }
  }

  private void updateTranscriptionAndState(String transcript, int newState) {
    databaseHelper.setTranscriptionAndState(transcript, newState);
  }

  private void updateTranscriptionState(int newState) {
    databaseHelper.setTranscriptionState(newState);
  }

  // Uses try-with-resource
  @TargetApi(android.os.Build.VERSION_CODES.M)
  private boolean readAndValidateAudioFile() {
    if (voicemailUri == null) {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, file not found.");
      return false;
    } else {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, reading: " + voicemailUri);
    }

    try (InputStream in = context.getContentResolver().openInputStream(voicemailUri)) {
      audioData = ByteString.readFrom(in);
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, read " + audioData.size() + " bytes");
    } catch (IOException e) {
      VvmLog.e(TAG, "Transcriber.readAndValidateAudioFile", e);
      return false;
    }

    if (audioData.startsWith(ByteString.copyFromUtf8(AMR_PREFIX))) {
      encoding = AudioFormat.AMR_NB_8KHZ;
    } else {
      VvmLog.i(TAG, "Transcriber.readAndValidateAudioFile, unknown encoding");
      encoding = AudioFormat.AUDIO_FORMAT_UNSPECIFIED;
      return false;
    }

    return true;
  }

  private static Uri getVoicemailUri(JobWorkItem workItem) {
    return workItem.getIntent().getParcelableExtra(TranscriptionService.EXTRA_VOICEMAIL_URI);
  }
}
