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
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.lettertile.LetterTileDrawable;
import com.android.dialer.searchfragment.common.Projections;
import com.android.dialer.searchfragment.common.QueryBoldingUtil;
import com.android.dialer.searchfragment.common.R;
import com.android.dialer.telecom.TelecomUtil;

/** ViewHolder for a nearby place row. */
public final class RemoteContactViewHolder extends RecyclerView.ViewHolder
    implements View.OnClickListener {

  private final Context context;
  private final TextView nameView;
  private final TextView numberView;
  private final QuickContactBadge photo;

  private String number;

  public RemoteContactViewHolder(View view) {
    super(view);
    view.setOnClickListener(this);
    photo = view.findViewById(R.id.photo);
    nameView = view.findViewById(R.id.primary);
    numberView = view.findViewById(R.id.secondary);
    context = view.getContext();
  }

  /**
   * Binds the ViewHolder with a cursor from {@link RemoteContactsCursorLoader} with the data found
   * at the cursors current position.
   */
  public void bind(Cursor cursor, String query) {
    number = cursor.getString(Projections.PHONE_NUMBER);
    String name = cursor.getString(Projections.PHONE_DISPLAY_NAME);
    String label = getLabel(context.getResources(), cursor);
    String secondaryInfo =
        TextUtils.isEmpty(label)
            ? number
            : context.getString(
                com.android.contacts.common.R.string.call_subject_type_and_number, label, number);

    nameView.setText(QueryBoldingUtil.getNameWithQueryBolded(query, name));
    numberView.setText(QueryBoldingUtil.getNameWithQueryBolded(query, secondaryInfo));

    if (shouldShowPhoto(cursor)) {
      nameView.setVisibility(View.VISIBLE);
      photo.setVisibility(View.VISIBLE);
      String photoUri = cursor.getString(Projections.PHONE_PHOTO_URI);
      ContactPhotoManager.getInstance(context)
          .loadDialerThumbnailOrPhoto(
              photo,
              getContactUri(cursor),
              cursor.getLong(Projections.PHONE_PHOTO_ID),
              photoUri == null ? null : Uri.parse(photoUri),
              name,
              LetterTileDrawable.TYPE_DEFAULT);
    } else {
      nameView.setVisibility(View.GONE);
      photo.setVisibility(View.INVISIBLE);
    }
  }

  private boolean shouldShowPhoto(Cursor cursor) {
    int currentPosition = cursor.getPosition();
    if (currentPosition == 0) {
      return true;
    }

    String currentLookupKey = cursor.getString(Projections.PHONE_LOOKUP_KEY);
    cursor.moveToPosition(currentPosition - 1);
    String previousLookupKey = cursor.getString(Projections.PHONE_LOOKUP_KEY);
    cursor.moveToPosition(currentPosition);
    return !currentLookupKey.equals(previousLookupKey);
  }

  // TODO(calderwoodra): unify this into a utility method with CallLogAdapter#getNumberType
  private static String getLabel(Resources resources, Cursor cursor) {
    int numberType = cursor.getInt(Projections.PHONE_TYPE);
    String numberLabel = cursor.getString(Projections.PHONE_LABEL);

    // Returns empty label instead of "custom" if the custom label is empty.
    if (numberType == Phone.TYPE_CUSTOM && TextUtils.isEmpty(numberLabel)) {
      return "";
    }
    return (String) Phone.getTypeLabel(resources, numberType, numberLabel);
  }

  private static Uri getContactUri(Cursor cursor) {
    long contactId = cursor.getLong(Projections.PHONE_ID);
    String lookupKey = cursor.getString(Projections.PHONE_LOOKUP_KEY);
    return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
  }

  @Override
  public void onClick(View v) {
    TelecomUtil.placeCall(
        context, new CallIntentBuilder(number, CallInitiationType.Type.REGULAR_SEARCH).build());
  }
}
