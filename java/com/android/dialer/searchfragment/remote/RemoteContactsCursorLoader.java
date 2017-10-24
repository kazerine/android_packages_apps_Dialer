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

package com.android.dialer.searchfragment.remote;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.remote.RemoteDirectoriesCursorLoader.Directory;
import java.util.ArrayList;
import java.util.List;

/**
 * Cursor loader to load extended contacts on device.
 *
 * <p>This loader performs several database queries in serial and merges the resulting cursors
 * together into {@link RemoteContactsCursor}. If there are no results, the loader will return a
 * null cursor.
 */
public final class RemoteContactsCursorLoader extends CursorLoader {

  private static final Uri ENTERPRISE_CONTENT_FILTER_URI =
      Uri.withAppendedPath(Phone.CONTENT_URI, "filter_enterprise");

  private static final String IGNORE_NUMBER_TOO_LONG_CLAUSE = "length(" + Phone.NUMBER + ") < 1000";
  private static final String PHONE_NUMBER_NOT_NULL = Phone.NUMBER + " IS NOT NULL";
  private static final String MAX_RESULTS = "10";

  private final String query;
  private final List<Directory> directories;
  private final Cursor[] cursors;

  public RemoteContactsCursorLoader(Context context, String query, List<Directory> directories) {
    super(
        context,
        null,
        Projections.DATA_PROJECTION,
        IGNORE_NUMBER_TOO_LONG_CLAUSE + " AND " + PHONE_NUMBER_NOT_NULL,
        null,
        Phone.SORT_KEY_PRIMARY);
    this.query = query;
    this.directories = new ArrayList<>(directories);
    cursors = new Cursor[directories.size()];
  }

  @Override
  public Cursor loadInBackground() {
    for (int i = 0; i < directories.size(); i++) {
      Directory directory = directories.get(i);
      // Since the on device contacts could be queried as remote directories and we already query
      // them in SearchContactsCursorLoader, avoid querying them again.
      // TODO(calderwoodra): It's a happy coincidence that on device contacts don't have directory
      // names set, leaving this todo to investigate a better way to isolate them from other remote
      // directories.
      if (TextUtils.isEmpty(directory.getDisplayName())) {
        cursors[i] = null;
        continue;
      }
      cursors[i] =
          getContext()
              .getContentResolver()
              .query(
                  getContentFilterUri(query, directory.getId()),
                  getProjection(),
                  getSelection(),
                  getSelectionArgs(),
                  getSortOrder());
    }
    return RemoteContactsCursor.newInstance(getContext(), cursors, directories);
  }

  @VisibleForTesting
  static Uri getContentFilterUri(String query, int directoryId) {
    Uri baseUri =
        VERSION.SDK_INT >= VERSION_CODES.N
            ? ENTERPRISE_CONTENT_FILTER_URI
            : Phone.CONTENT_FILTER_URI;

    return baseUri
        .buildUpon()
        .appendPath(query)
        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId))
        .appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true")
        .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, MAX_RESULTS)
        .build();
  }
}
