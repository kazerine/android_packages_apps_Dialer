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

package com.android.dialer.blockreportspam;

import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.Nullable;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForBlockingNumber;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForReportingNotSpam;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.DialogFragmentForUnblockingNumber;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.OnConfirmListener;
import com.android.dialer.blockreportspam.BlockReportSpamDialogs.OnSpamDialogClickListener;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.DialerExecutor.Worker;
import com.android.dialer.common.concurrent.DialerExecutorComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.protos.ProtoParsers;
import com.android.dialer.spam.Spam;
import com.android.dialer.spam.SpamComponent;
import com.android.dialer.spam.SpamSettings;
import com.google.auto.value.AutoValue;

/**
 * A {@link BroadcastReceiver} that shows an appropriate dialog upon receiving notifications from
 * {@link ShowBlockReportSpamDialogNotifier}.
 */
public final class ShowBlockReportSpamDialogReceiver extends BroadcastReceiver {

  static final String ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER = "show_dialog_to_block_number";
  static final String ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM =
      "show_dialog_to_block_number_and_optionally_report_spam";
  static final String ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM = "show_dialog_to_report_not_spam";
  static final String ACTION_SHOW_DIALOG_TO_UNBLOCK_NUMBER = "show_dialog_to_unblock_number";
  static final String EXTRA_DIALOG_INFO = "dialog_info";

  /** {@link FragmentManager} needed to show a {@link android.app.DialogFragment}. */
  private final FragmentManager fragmentManager;

  /** Returns an {@link IntentFilter} containing all actions accepted by this broadcast receiver. */
  public static IntentFilter getIntentFilter() {
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM);
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER);
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM);
    intentFilter.addAction(ACTION_SHOW_DIALOG_TO_UNBLOCK_NUMBER);
    return intentFilter;
  }

  public ShowBlockReportSpamDialogReceiver(FragmentManager fragmentManager) {
    this.fragmentManager = fragmentManager;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.onReceive");

    String action = intent.getAction();

    switch (Assert.isNotNull(action)) {
      case ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER:
        showDialogToBlockNumber(context, intent);
        break;
      case ACTION_SHOW_DIALOG_TO_BLOCK_NUMBER_AND_OPTIONALLY_REPORT_SPAM:
        showDialogToBlockNumberAndOptionallyReportSpam(context, intent);
        break;
      case ACTION_SHOW_DIALOG_TO_REPORT_NOT_SPAM:
        showDialogToReportNotSpam(context, intent);
        break;
      case ACTION_SHOW_DIALOG_TO_UNBLOCK_NUMBER:
        showDialogToUnblockNumber(context, intent);
        break;
      default:
        throw new IllegalStateException("Unsupported action: " + action);
    }
  }

  private void showDialogToBlockNumberAndOptionallyReportSpam(Context context, Intent intent) {
    LogUtil.enterBlock(
        "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam");

    Assert.checkArgument(intent.hasExtra(EXTRA_DIALOG_INFO));
    BlockReportSpamDialogInfo dialogInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_DIALOG_INFO, BlockReportSpamDialogInfo.getDefaultInstance());

    Spam spam = SpamComponent.get(context).spam();
    SpamSettings spamSettings = SpamComponent.get(context).spamSettings();
    FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler =
        new FilteredNumberAsyncQueryHandler(context);

    // Set up the positive listener for the dialog.
    OnSpamDialogClickListener onSpamDialogClickListener =
        reportSpam -> {
          LogUtil.i(
              "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam",
              "confirmed");

          if (reportSpam && spamSettings.isSpamEnabled()) {
            LogUtil.i(
                "ShowBlockReportSpamDialogReceiver.showDialogToBlockNumberAndOptionallyReportSpam",
                "report spam");
            Logger.get(context)
                .logImpression(
                    DialerImpression.Type
                        .REPORT_CALL_AS_SPAM_VIA_CALL_LOG_BLOCK_REPORT_SPAM_SENT_VIA_BLOCK_NUMBER_DIALOG);
            spam.reportSpamFromCallHistory(
                dialogInfo.getNormalizedNumber(),
                dialogInfo.getCountryIso(),
                dialogInfo.getCallType(),
                dialogInfo.getReportingLocation(),
                dialogInfo.getContactSource());
          }

          filteredNumberAsyncQueryHandler.blockNumber(
              unused ->
                  Logger.get(context)
                      .logImpression(DialerImpression.Type.USER_ACTION_BLOCKED_NUMBER),
              dialogInfo.getNormalizedNumber(),
              dialogInfo.getCountryIso());
        };

    // Create and show the dialog.
    DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam.newInstance(
            dialogInfo.getNormalizedNumber(),
            spamSettings.isDialogReportSpamCheckedByDefault(),
            onSpamDialogClickListener,
            /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.BLOCK_REPORT_SPAM_DIALOG_TAG);
  }

  private void showDialogToBlockNumber(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.showDialogToBlockNumber");

    Assert.checkArgument(intent.hasExtra(EXTRA_DIALOG_INFO));
    BlockReportSpamDialogInfo dialogInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_DIALOG_INFO, BlockReportSpamDialogInfo.getDefaultInstance());

    FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler =
        new FilteredNumberAsyncQueryHandler(context);

    // Set up the positive listener for the dialog.
    OnConfirmListener onConfirmListener =
        () -> {
          LogUtil.i("ShowBlockReportSpamDialogReceiver.showDialogToBlockNumber", "block number");
          filteredNumberAsyncQueryHandler.blockNumber(
              unused ->
                  Logger.get(context)
                      .logImpression(DialerImpression.Type.USER_ACTION_BLOCKED_NUMBER),
              dialogInfo.getNormalizedNumber(),
              dialogInfo.getCountryIso());
        };

    // Create and show the dialog.
    DialogFragmentForBlockingNumber.newInstance(
            dialogInfo.getNormalizedNumber(), onConfirmListener, /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.BLOCK_DIALOG_TAG);
  }

  private void showDialogToReportNotSpam(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.showDialogToReportNotSpam");

    Assert.checkArgument(intent.hasExtra(EXTRA_DIALOG_INFO));
    BlockReportSpamDialogInfo dialogInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_DIALOG_INFO, BlockReportSpamDialogInfo.getDefaultInstance());

    // Set up the positive listener for the dialog.
    OnConfirmListener onConfirmListener =
        () -> {
          LogUtil.i("ShowBlockReportSpamDialogReceiver.showDialogToReportNotSpam", "confirmed");

          if (SpamComponent.get(context).spamSettings().isSpamEnabled()) {
            Logger.get(context)
                .logImpression(DialerImpression.Type.DIALOG_ACTION_CONFIRM_NUMBER_NOT_SPAM);
            SpamComponent.get(context)
                .spam()
                .reportNotSpamFromCallHistory(
                    dialogInfo.getNormalizedNumber(),
                    dialogInfo.getCountryIso(),
                    dialogInfo.getCallType(),
                    dialogInfo.getReportingLocation(),
                    dialogInfo.getContactSource());
          }
        };

    // Create & show the dialog.
    DialogFragmentForReportingNotSpam.newInstance(
            dialogInfo.getNormalizedNumber(), onConfirmListener, /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.NOT_SPAM_DIALOG_TAG);
  }

  private void showDialogToUnblockNumber(Context context, Intent intent) {
    LogUtil.enterBlock("ShowBlockReportSpamDialogReceiver.showDialogToUnblockNumber");

    Assert.checkArgument(intent.hasExtra(EXTRA_DIALOG_INFO));
    BlockReportSpamDialogInfo dialogInfo =
        ProtoParsers.getTrusted(
            intent, EXTRA_DIALOG_INFO, BlockReportSpamDialogInfo.getDefaultInstance());

    FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler =
        new FilteredNumberAsyncQueryHandler(context);

    // Set up the positive listener for the dialog.
    OnConfirmListener onConfirmListener =
        () -> {
          LogUtil.i("ShowBlockReportSpamDialogReceiver.showDialogToUnblockNumber", "confirmed");

          DialerExecutorComponent.get(context)
              .dialerExecutorFactory()
              .createNonUiTaskBuilder(
                  new GetIdForBlockedNumberWorker(filteredNumberAsyncQueryHandler))
              .onSuccess(
                  idForBlockedNumber -> {
                    LogUtil.i(
                        "ShowBlockReportSpamDialogReceiver.showDialogToUnblockNumber",
                        "ID for the blocked number retrieved");
                    if (idForBlockedNumber == null) {
                      throw new IllegalStateException("ID for a blocked number is null.");
                    }

                    LogUtil.i(
                        "ShowBlockReportSpamDialogReceiver.showDialogToUnblockNumber",
                        "unblocking number");
                    filteredNumberAsyncQueryHandler.unblock(
                        (rows, values) ->
                            Logger.get(context)
                                .logImpression(DialerImpression.Type.USER_ACTION_UNBLOCKED_NUMBER),
                        idForBlockedNumber);
                  })
              .onFailure(
                  throwable -> {
                    throw new RuntimeException(throwable);
                  })
              .build()
              .executeSerial(
                  NumberInfo.newBuilder()
                      .setNormalizedNumber(dialogInfo.getNormalizedNumber())
                      .setCountryIso(dialogInfo.getCountryIso())
                      .build());
        };

    // Create & show the dialog.
    DialogFragmentForUnblockingNumber.newInstance(
            dialogInfo.getNormalizedNumber(), onConfirmListener, /* dismissListener = */ null)
        .show(fragmentManager, BlockReportSpamDialogs.UNBLOCK_DIALOG_TAG);
  }

  /** A {@link Worker} that retrieves the ID of a blocked number from the database. */
  private static final class GetIdForBlockedNumberWorker implements Worker<NumberInfo, Integer> {

    private final FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler;

    GetIdForBlockedNumberWorker(FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler) {
      this.filteredNumberAsyncQueryHandler = filteredNumberAsyncQueryHandler;
    }

    @Nullable
    @Override
    public Integer doInBackground(NumberInfo input) throws Throwable {
      LogUtil.enterBlock("GetIdForBlockedNumberWorker.doInBackground");

      return filteredNumberAsyncQueryHandler.getBlockedIdSynchronous(
          input.getNormalizedNumber(), input.getCountryIso());
    }
  }

  /**
   * Contains information about a number and serves as the input to {@link
   * GetIdForBlockedNumberWorker}.
   */
  @AutoValue
  abstract static class NumberInfo {
    static Builder newBuilder() {
      return new AutoValue_ShowBlockReportSpamDialogReceiver_NumberInfo.Builder();
    }

    abstract String getNormalizedNumber();

    abstract String getCountryIso();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setNormalizedNumber(String normalizedNumber);

      abstract Builder setCountryIso(String countryIso);

      abstract NumberInfo build();
    }
  }
}
