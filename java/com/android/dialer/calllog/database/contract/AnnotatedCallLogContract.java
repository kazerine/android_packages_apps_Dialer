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

package com.android.dialer.calllog.database.contract;

import android.net.Uri;
import android.provider.BaseColumns;
import com.android.dialer.constants.Constants;
import java.util.Arrays;

/** Contract for the AnnotatedCallLog content provider. */
public class AnnotatedCallLogContract {
  public static final String AUTHORITY = Constants.get().getAnnotatedCallLogProviderAuthority();

  public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

  /**
   * Columns shared by {@link AnnotatedCallLog} and {@link CoalescedAnnotatedCallLog}.
   *
   * <p>When adding columns be sure to update {@link #ALL_COMMON_COLUMNS}.
   */
  interface CommonColumns extends BaseColumns {

    /**
     * Timestamp of the entry, in milliseconds.
     *
     * <p>Type: INTEGER (long)
     */
    String TIMESTAMP = "timestamp";

    /**
     * Copied from {@link android.provider.CallLog.Calls#CACHED_NAME}.
     *
     * <p>This is exactly how it should appear to the user. If the user's locale or name display
     * preferences change, this column should be rewritten.
     *
     * <p>Type: TEXT
     */
    String NAME = "name";

    /**
     * Copied from {@link android.provider.CallLog.Calls#CACHED_FORMATTED_NUMBER}.
     *
     * <p>Type: TEXT
     */
    String FORMATTED_NUMBER = "formatted_number";

    /**
     * Copied from {@link android.provider.CallLog.Calls#CACHED_PHOTO_URI}.
     *
     * <p>TYPE: TEXT
     */
    String PHOTO_URI = "photo_uri";

    /**
     * Copied from {@link android.provider.CallLog.Calls#CACHED_PHOTO_ID}.
     *
     * <p>Type: INTEGER (long)
     */
    String PHOTO_ID = "photo_id";

    /**
     * Copied from {@link android.provider.CallLog.Calls#CACHED_LOOKUP_URI}.
     *
     * <p>TYPE: TEXT
     */
    String LOOKUP_URI = "lookup_uri";

    // TODO(zachh): If we need to support photos other than local contacts', add a (blob?) column.

    /**
     * The number type as a string to be displayed to the user, for example "Home" or "Mobile".
     *
     * <p>This column should be updated for the appropriate language when the locale changes.
     *
     * <p>TYPE: TEXT
     */
    String NUMBER_TYPE_LABEL = "number_type_label";

    /**
     * See {@link android.provider.CallLog.Calls#IS_READ}.
     *
     * <p>TYPE: INTEGER (boolean)
     */
    String IS_READ = "is_read";

    /**
     * See {@link android.provider.CallLog.Calls#NEW}.
     *
     * <p>Type: INTEGER (boolean)
     */
    String NEW = "new";

    /**
     * See {@link android.provider.CallLog.Calls#GEOCODED_LOCATION}.
     *
     * <p>TYPE: TEXT
     */
    String GEOCODED_LOCATION = "geocoded_location";

    /**
     * String suitable for display which indicates the phone account used to make the call.
     *
     * <p>TYPE: TEXT
     */
    String PHONE_ACCOUNT_LABEL = "phone_account_label";

    /**
     * The color int for the phone account.
     *
     * <p>TYPE: INTEGER (int)
     */
    String PHONE_ACCOUNT_COLOR = "phone_account_color";

    /**
     * See {@link android.provider.CallLog.Calls#FEATURES}.
     *
     * <p>TYPE: INTEGER (int)
     */
    String FEATURES = "features";

    /**
     * True if a caller ID data source informed us that this is a business number. This is used to
     * determine if a generic business avatar should be shown vs. a generic person avatar.
     *
     * <p>TYPE: INTEGER (boolean)
     */
    String IS_BUSINESS = "is_business";

    /**
     * True if this was a call to voicemail. This is used to determine if the voicemail avatar
     * should be displayed.
     *
     * <p>TYPE: INTEGER (boolean)
     */
    String IS_VOICEMAIL = "is_voicemail";

    /**
     * Copied from {@link android.provider.CallLog.Calls#TYPE}.
     *
     * <p>Type: INTEGER (int)
     */
    String CALL_TYPE = "call_type";

    String[] ALL_COMMON_COLUMNS =
        new String[] {
          _ID,
          TIMESTAMP,
          NAME,
          FORMATTED_NUMBER,
          PHOTO_URI,
          PHOTO_ID,
          LOOKUP_URI,
          NUMBER_TYPE_LABEL,
          IS_READ,
          NEW,
          GEOCODED_LOCATION,
          PHONE_ACCOUNT_LABEL,
          PHONE_ACCOUNT_COLOR,
          FEATURES,
          IS_BUSINESS,
          IS_VOICEMAIL,
          CALL_TYPE
        };
  }

  /**
   * AnnotatedCallLog table.
   *
   * <p>This contains all of the non-coalesced call log entries.
   */
  public static final class AnnotatedCallLog implements CommonColumns {

    public static final String TABLE = "AnnotatedCallLog";

    /** The content URI for this table. */
    public static final Uri CONTENT_URI =
        Uri.withAppendedPath(AnnotatedCallLogContract.CONTENT_URI, TABLE);

    /** The MIME type of a {@link android.content.ContentProvider#getType(Uri)} single entry. */
    public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/annotated_call_log";

    /**
     * The phone number called or number the call came from, encoded as a {@link
     * com.android.dialer.DialerPhoneNumber} proto. The number may be empty if it was an incoming
     * call and the number was unknown.
     *
     * <p>This column is only present in the annotated call log, and not the coalesced annotated
     * call log. The coalesced version uses a formatted number string rather than proto bytes.
     *
     * <p>Type: BLOB
     */
    public static final String NUMBER = "number";
  }

  /**
   * Coalesced view of the AnnotatedCallLog table.
   *
   * <p>This is an in-memory view of the {@link AnnotatedCallLog} with some adjacent entries
   * collapsed.
   *
   * <p>When adding columns be sure to update {@link #COLUMNS_ONLY_IN_COALESCED_CALL_LOG}.
   */
  public static final class CoalescedAnnotatedCallLog implements CommonColumns {

    public static final String TABLE = "CoalescedAnnotatedCallLog";

    /** The content URI for this table. */
    public static final Uri CONTENT_URI =
        Uri.withAppendedPath(AnnotatedCallLogContract.CONTENT_URI, TABLE);

    /** The MIME type of a {@link android.content.ContentProvider#getType(Uri)} single entry. */
    public static final String CONTENT_ITEM_TYPE =
        "vnd.android.cursor.item/coalesced_annotated_call_log";

    /**
     * Number of AnnotatedCallLog rows represented by this CoalescedAnnotatedCallLog row.
     *
     * <p>Type: INTEGER
     */
    public static final String NUMBER_CALLS = "number_calls";

    /**
     * Columns that are only in the {@link CoalescedAnnotatedCallLog} but not the {@link
     * AnnotatedCallLog}.
     */
    private static final String[] COLUMNS_ONLY_IN_COALESCED_CALL_LOG = new String[] {NUMBER_CALLS};

    /** All columns in the {@link CoalescedAnnotatedCallLog}. */
    public static final String[] ALL_COLUMNS =
        concat(ALL_COMMON_COLUMNS, COLUMNS_ONLY_IN_COALESCED_CALL_LOG);
  }

  private static String[] concat(String[] first, String[] second) {
    String[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }
}
