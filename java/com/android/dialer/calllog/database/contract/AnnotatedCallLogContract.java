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
     * Primary text to display for the entry. This could be a name from a local contact or caller ID
     * data source, or it could just be a phone number, for example.
     *
     * <p>This is exactly how it should appear to the user. If the user's locale or name display
     * preferences change, this column should be rewritten.
     *
     * <p>Type: TEXT
     */
    String PRIMARY_TEXT = "primary_text";

    /**
     * Local photo URI for the contact associated with the phone number, if it exists.
     *
     * <p>Photos currently only come from local contacts database and not caller ID sources. If
     * there is no photo for a contact then an appropriate letter tile should be drawn.
     *
     * <p>TYPE: TEXT
     */
    String CONTACT_PHOTO_URI = "contact_photo_uri";

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
     * See CallLog.Calls.IS_READ.
     *
     * <p>TYPE: INTEGER (boolean)
     */
    String IS_READ = "is_read";

    /**
     * See CallLog.Calls.GEOCODED_LOCATION.
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
     * See CallLog.Calls.FEATURES.
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

    String[] ALL_COMMON_COLUMNS =
        new String[] {
          _ID,
          TIMESTAMP,
          PRIMARY_TEXT,
          CONTACT_PHOTO_URI,
          NUMBER_TYPE_LABEL,
          IS_READ,
          GEOCODED_LOCATION,
          PHONE_ACCOUNT_LABEL,
          PHONE_ACCOUNT_COLOR,
          FEATURES,
          IS_BUSINESS,
          IS_VOICEMAIL
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
     * The phone number formatted in a way suitable for display to the user. This value is generated
     * on the fly when the {@link CoalescedAnnotatedCallLog} is generated.
     *
     * <p>Type: TEXT
     */
    public static final String FORMATTED_NUMBER = "formatted_number";

    /**
     * The call types of the most recent 3 calls, encoded as a CallTypes proto.
     *
     * <p>TYPE: BLOB
     */
    public static final String CALL_TYPES = "call_types";

    /**
     * Columns that are only in the {@link CoalescedAnnotatedCallLog} but not the {@link
     * AnnotatedCallLog}.
     */
    private static final String[] COLUMNS_ONLY_IN_COALESCED_CALL_LOG =
        new String[] {NUMBER_CALLS, FORMATTED_NUMBER, CALL_TYPES};

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
