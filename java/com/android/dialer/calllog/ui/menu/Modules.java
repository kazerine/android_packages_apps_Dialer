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

package com.android.dialer.calllog.ui.menu;

import android.content.Context;
import android.provider.CallLog.Calls;
import android.telecom.PhoneAccountHandle;
import android.text.TextUtils;
import com.android.dialer.blockreportspam.BlockReportSpamDialogInfo;
import com.android.dialer.calldetails.CallDetailsActivity;
import com.android.dialer.calldetails.CallDetailsHeaderInfo;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.calllog.model.CoalescedRow;
import com.android.dialer.calllogutils.CallLogEntryText;
import com.android.dialer.calllogutils.NumberAttributesConverter;
import com.android.dialer.duo.DuoConstants;
import com.android.dialer.glidephotomanager.PhotoInfo;
import com.android.dialer.historyitemactions.DividerModule;
import com.android.dialer.historyitemactions.DuoCallModule;
import com.android.dialer.historyitemactions.HistoryItemActionModule;
import com.android.dialer.historyitemactions.IntentModule;
import com.android.dialer.historyitemactions.SharedModules;
import com.android.dialer.logging.ReportingLocation;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.telecom.TelecomUtil;
import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configures the modules for the bottom sheet; these are the rows below the top row (primary
 * action) in the bottom sheet.
 */
@SuppressWarnings("Guava")
final class Modules {

  static List<HistoryItemActionModule> fromRow(Context context, CoalescedRow row) {
    // Conditionally add each module, which are items in the bottom sheet's menu.
    List<HistoryItemActionModule> modules = new ArrayList<>();

    String normalizedNumber = row.getNumber().getNormalizedNumber();
    boolean canPlaceCalls =
        PhoneNumberHelper.canPlaceCallsTo(normalizedNumber, row.getNumberPresentation());

    if (canPlaceCalls) {
      modules.addAll(createModulesForCalls(context, row, normalizedNumber));
      Optional<HistoryItemActionModule> moduleForSendingTextMessage =
          SharedModules.createModuleForSendingTextMessage(
              context, normalizedNumber, row.getNumberAttributes().getIsBlocked());
      if (moduleForSendingTextMessage.isPresent()) {
        modules.add(moduleForSendingTextMessage.get());
      }
    }

    if (!modules.isEmpty()) {
      modules.add(new DividerModule());
    }


    // TODO(zachh): Module for CallComposer.

    if (canPlaceCalls) {
      Optional<HistoryItemActionModule> moduleForAddingToContacts =
          SharedModules.createModuleForAddingToContacts(
              context,
              row.getNumber(),
              row.getNumberAttributes().getName(),
              row.getNumberAttributes().getLookupUri(),
              row.getNumberAttributes().getIsBlocked(),
              row.getNumberAttributes().getIsSpam());
      if (moduleForAddingToContacts.isPresent()) {
        modules.add(moduleForAddingToContacts.get());
      }

      BlockReportSpamDialogInfo blockReportSpamDialogInfo =
          BlockReportSpamDialogInfo.newBuilder()
              .setNormalizedNumber(row.getNumber().getNormalizedNumber())
              .setCountryIso(row.getNumber().getCountryIso())
              .setCallType(row.getCallType())
              .setReportingLocation(ReportingLocation.Type.CALL_LOG_HISTORY)
              .setContactSource(row.getNumberAttributes().getContactSource())
              .build();
      modules.addAll(
          SharedModules.createModulesHandlingBlockedOrSpamNumber(
              context,
              blockReportSpamDialogInfo,
              row.getNumberAttributes().getIsBlocked(),
              row.getNumberAttributes().getIsSpam()));

      Optional<HistoryItemActionModule> moduleForCopyingNumber =
          SharedModules.createModuleForCopyingNumber(context, normalizedNumber);
      if (moduleForCopyingNumber.isPresent()) {
        modules.add(moduleForCopyingNumber.get());
      }
    }

    modules.add(createModuleForAccessingCallDetails(context, row));

    modules.add(new DeleteCallLogItemModule(context, row.getCoalescedIds()));

    return modules;
  }

  private static List<HistoryItemActionModule> createModulesForCalls(
      Context context, CoalescedRow row, String normalizedNumber) {
    // Don't add call options if a number is blocked.
    if (row.getNumberAttributes().getIsBlocked()) {
      return Collections.emptyList();
    }

    List<HistoryItemActionModule> modules = new ArrayList<>();
    PhoneAccountHandle phoneAccountHandle =
        TelecomUtil.composePhoneAccountHandle(
            row.getPhoneAccountComponentName(), row.getPhoneAccountId());

    // Add an audio call item
    modules.add(
        IntentModule.newCallModule(
            context, normalizedNumber, phoneAccountHandle, CallInitiationType.Type.CALL_LOG));

    // Add a video item if (1) the call log entry is for a video call, and (2) the call is not spam.
    if ((row.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO
        && !row.getNumberAttributes().getIsSpam()) {
      boolean isDuoCall =
          DuoConstants.PHONE_ACCOUNT_COMPONENT_NAME
              .flattenToString()
              .equals(row.getPhoneAccountComponentName());
      modules.add(
          isDuoCall
              ? new DuoCallModule(context, normalizedNumber, CallInitiationType.Type.CALL_LOG)
              : IntentModule.newCarrierVideoCallModule(
                  context, normalizedNumber, phoneAccountHandle, CallInitiationType.Type.CALL_LOG));
    }

    // TODO(zachh): Also show video option if the call log entry is for an audio call but video
    // capabilities are present?

    return modules;
  }

  private static HistoryItemActionModule createModuleForAccessingCallDetails(
      Context context, CoalescedRow row) {
    boolean canReportAsInvalidNumber = row.getNumberAttributes().getCanReportAsInvalidNumber();
    boolean canSupportAssistedDialing =
        !TextUtils.isEmpty(row.getNumberAttributes().getLookupUri());

    return new IntentModule(
        context,
        CallDetailsActivity.newInstance(
            context,
            row.getCoalescedIds(),
            createCallDetailsHeaderInfoFromRow(context, row),
            canReportAsInvalidNumber,
            canSupportAssistedDialing),
        R.string.call_details_menu_label,
        R.drawable.quantum_ic_info_outline_vd_theme_24);
  }

  private static CallDetailsHeaderInfo createCallDetailsHeaderInfoFromRow(
      Context context, CoalescedRow row) {
    return CallDetailsHeaderInfo.newBuilder()
        .setDialerPhoneNumber(row.getNumber())
        .setPhotoInfo(createPhotoInfoFromRow(row))
        .setPrimaryText(CallLogEntryText.buildPrimaryText(context, row).toString())
        .setSecondaryText(
            CallLogEntryText.buildSecondaryTextForBottomSheet(context, row).toString())
        .build();
  }

  private static PhotoInfo createPhotoInfoFromRow(CoalescedRow row) {
    return NumberAttributesConverter.toPhotoInfoBuilder(row.getNumberAttributes())
        .setFormattedNumber(row.getFormattedNumber())
        .setIsVideo((row.getFeatures() & Calls.FEATURES_VIDEO) == Calls.FEATURES_VIDEO)
        .setIsVoicemail(row.getIsVoicemailCall())
        .build();
  }
}
