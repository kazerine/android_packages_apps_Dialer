/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.dialer.phonenumberutil;

import android.content.Context;
import android.provider.CallLog;
import android.support.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.compat.CompatUtils;
import com.android.dialer.compat.telephony.TelephonyManagerCompat;
import com.android.dialer.phonenumbergeoutil.PhoneNumberGeoUtilComponent;
import com.android.dialer.telecom.TelecomUtil;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PhoneNumberHelper {

  private static final String TAG = "PhoneNumberUtil";
  private static final Set<String> LEGACY_UNKNOWN_NUMBERS =
      new HashSet<>(Arrays.asList("-1", "-2", "-3"));

  /** Returns true if it is possible to place a call to the given number. */
  public static boolean canPlaceCallsTo(CharSequence number, int presentation) {
    return presentation == CallLog.Calls.PRESENTATION_ALLOWED
        && !TextUtils.isEmpty(number)
        && !isLegacyUnknownNumbers(number);
  }

  /**
   * Returns true if the given number is the number of the configured voicemail. To be able to
   * mock-out this, it is not a static method.
   */
  public static boolean isVoicemailNumber(
      Context context, PhoneAccountHandle accountHandle, CharSequence number) {
    if (TextUtils.isEmpty(number)) {
      return false;
    }
    return TelecomUtil.isVoicemailNumber(context, accountHandle, number.toString());
  }

  /**
   * Returns true if the given number is a SIP address. To be able to mock-out this, it is not a
   * static method.
   */
  public static boolean isSipNumber(CharSequence number) {
    return number != null && isUriNumber(number.toString());
  }

  public static boolean isUnknownNumberThatCanBeLookedUp(
      Context context, PhoneAccountHandle accountHandle, CharSequence number, int presentation) {
    if (presentation == CallLog.Calls.PRESENTATION_UNKNOWN) {
      return false;
    }
    if (presentation == CallLog.Calls.PRESENTATION_RESTRICTED) {
      return false;
    }
    if (presentation == CallLog.Calls.PRESENTATION_PAYPHONE) {
      return false;
    }
    if (TextUtils.isEmpty(number)) {
      return false;
    }
    if (isVoicemailNumber(context, accountHandle, number)) {
      return false;
    }
    if (isLegacyUnknownNumbers(number)) {
      return false;
    }
    return true;
  }

  public static boolean isLegacyUnknownNumbers(CharSequence number) {
    return number != null && LEGACY_UNKNOWN_NUMBERS.contains(number.toString());
  }

  /**
   * @param countryIso Country ISO used if there is no country code in the number, may be null
   *     otherwise.
   * @return a geographical description string for the specified number.
   */
  public static String getGeoDescription(
      Context context, String number, @Nullable String countryIso) {
    return PhoneNumberGeoUtilComponent.get(context)
        .getPhoneNumberGeoUtil()
        .getGeoDescription(context, number, countryIso);
  }

  /**
   * @param phoneAccountHandle {@code PhonAccountHandle} used to get current network country ISO.
   *     May be null if no account is in use or selected, in which case default account will be
   *     used.
   * @return The ISO 3166-1 two letters country code of the country the user is in based on the
   *     network location. If the network location does not exist, fall back to the locale setting.
   */
  public static String getCurrentCountryIso(
      Context context, @Nullable PhoneAccountHandle phoneAccountHandle) {
    // Without framework function calls, this seems to be the most accurate location service
    // we can rely on.
    String countryIso =
        TelephonyManagerCompat.getNetworkCountryIsoForPhoneAccountHandle(
            context, phoneAccountHandle);
    if (TextUtils.isEmpty(countryIso)) {
      countryIso = CompatUtils.getLocale(context).getCountry();
      LogUtil.i(
          "PhoneNumberHelper.getCurrentCountryIso",
          "No CountryDetector; falling back to countryIso based on locale: " + countryIso);
    }
    countryIso = countryIso.toUpperCase();

    return countryIso;
  }

  /**
   * @return Formatted phone number. e.g. 1-123-456-7890. Returns the original number if formatting
   *     failed.
   */
  public static String formatNumber(@Nullable String number, String countryIso) {
    // The number can be null e.g. schema is voicemail and uri content is empty.
    if (number == null) {
      return null;
    }
    String formattedNumber = PhoneNumberUtils.formatNumber(number, countryIso);
    return formattedNumber != null ? formattedNumber : number;
  }

  /**
   * Determines if the specified number is actually a URI (i.e. a SIP address) rather than a regular
   * PSTN phone number, based on whether or not the number contains an "@" character.
   *
   * @param number Phone number
   * @return true if number contains @
   *     <p>TODO: Remove if PhoneNumberUtils.isUriNumber(String number) is made public.
   */
  public static boolean isUriNumber(String number) {
    // Note we allow either "@" or "%40" to indicate a URI, in case
    // the passed-in string is URI-escaped.  (Neither "@" nor "%40"
    // will ever be found in a legal PSTN number.)
    return number != null && (number.contains("@") || number.contains("%40"));
  }

  /**
   * @param number SIP address of the form "username@domainname" (or the URI-escaped equivalent
   *     "username%40domainname")
   *     <p>TODO: Remove if PhoneNumberUtils.getUsernameFromUriNumber(String number) is made public.
   * @return the "username" part of the specified SIP address, i.e. the part before the "@"
   *     character (or "%40").
   */
  public static String getUsernameFromUriNumber(String number) {
    // The delimiter between username and domain name can be
    // either "@" or "%40" (the URI-escaped equivalent.)
    int delimiterIndex = number.indexOf('@');
    if (delimiterIndex < 0) {
      delimiterIndex = number.indexOf("%40");
    }
    if (delimiterIndex < 0) {
      LogUtil.i(
          "PhoneNumberHelper.getUsernameFromUriNumber",
          "getUsernameFromUriNumber: no delimiter found in SIP address: "
              + LogUtil.sanitizePii(number));
      return number;
    }
    return number.substring(0, delimiterIndex);
  }

  private static boolean isVerizon(Context context) {
    // Verizon MCC/MNC codes copied from com/android/voicemailomtp/res/xml/vvm_config.xml.
    // TODO(sail): Need a better way to do per carrier and per OEM configurations.
    switch (context.getSystemService(TelephonyManager.class).getSimOperator()) {
      case "310004":
      case "310010":
      case "310012":
      case "310013":
      case "310590":
      case "310890":
      case "310910":
      case "311110":
      case "311270":
      case "311271":
      case "311272":
      case "311273":
      case "311274":
      case "311275":
      case "311276":
      case "311277":
      case "311278":
      case "311279":
      case "311280":
      case "311281":
      case "311282":
      case "311283":
      case "311284":
      case "311285":
      case "311286":
      case "311287":
      case "311288":
      case "311289":
      case "311390":
      case "311480":
      case "311481":
      case "311482":
      case "311483":
      case "311484":
      case "311485":
      case "311486":
      case "311487":
      case "311488":
      case "311489":
        return true;
      default:
        return false;
    }
  }

  /**
   * Gets the label to display for a phone call where the presentation is set as
   * PRESENTATION_RESTRICTED. For Verizon we want this to be displayed as "Restricted". For all
   * other carriers we want this to be be displayed as "Private number".
   */
  public static CharSequence getDisplayNameForRestrictedNumber(Context context) {
    if (isVerizon(context)) {
      return context.getString(R.string.private_num_verizon);
    } else {
      return context.getString(R.string.private_num_non_verizon);
    }
  }
}
