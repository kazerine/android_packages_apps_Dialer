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
 * limitations under the License.
 */

package com.android.dialer.calldetails;

import android.content.Context;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.telecom.PhoneAccount;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import com.android.dialer.calllogutils.CallbackActionHelper.CallbackAction;
import com.android.dialer.common.Assert;
import com.android.dialer.contactphoto.ContactPhotoManager;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.logging.InteractionEvent;
import com.android.dialer.logging.Logger;

/** ViewHolder for Header/Contact in {@link CallDetailsActivity}. */
public class CallDetailsHeaderViewHolder extends RecyclerView.ViewHolder
    implements OnClickListener {

  private final CallbackActionListener callbackActionListener;
  private final ImageView callbackButton;
  private final TextView nameView;
  private final TextView numberView;
  private final TextView networkView;
  private final QuickContactBadge contactPhoto;
  private final Context context;

  private DialerContact contact;
  private @CallbackAction int callbackAction;

  CallDetailsHeaderViewHolder(View container, CallbackActionListener callbackActionListener) {
    super(container);
    context = container.getContext();
    callbackButton = container.findViewById(R.id.call_back_button);
    nameView = container.findViewById(R.id.contact_name);
    numberView = container.findViewById(R.id.phone_number);
    networkView = container.findViewById(R.id.network);
    contactPhoto = container.findViewById(R.id.quick_contact_photo);

    callbackButton.setOnClickListener(this);
    this.callbackActionListener = callbackActionListener;
    Logger.get(context)
        .logQuickContactOnTouch(
            contactPhoto, InteractionEvent.Type.OPEN_QUICK_CONTACT_FROM_CALL_DETAILS, true);
  }

  /** Populates the contact info fields based on the current contact information. */
  void updateContactInfo(DialerContact contact, @CallbackAction int callbackAction) {
    this.contact = contact;
    ContactPhotoManager.getInstance(context)
        .loadDialerThumbnailOrPhoto(
            contactPhoto,
            contact.hasContactUri() ? Uri.parse(contact.getContactUri()) : null,
            contact.getPhotoId(),
            contact.hasPhotoUri() ? Uri.parse(contact.getPhotoUri()) : null,
            contact.getNameOrNumber(),
            contact.getContactType());

    nameView.setText(contact.getNameOrNumber());
    if (!TextUtils.isEmpty(contact.getDisplayNumber())) {
      numberView.setVisibility(View.VISIBLE);
      String secondaryInfo =
          TextUtils.isEmpty(contact.getNumberLabel())
              ? contact.getDisplayNumber()
              : context.getString(
                  com.android.contacts.common.R.string.call_subject_type_and_number,
                  contact.getNumberLabel(),
                  contact.getDisplayNumber());
      numberView.setText(secondaryInfo);
    } else {
      numberView.setVisibility(View.GONE);
      numberView.setText(null);
    }

    if (!TextUtils.isEmpty(contact.getSimDetails().getNetwork())) {
      networkView.setVisibility(View.VISIBLE);
      networkView.setText(contact.getSimDetails().getNetwork());
      if (contact.getSimDetails().getColor() != PhoneAccount.NO_HIGHLIGHT_COLOR) {
        networkView.setTextColor(contact.getSimDetails().getColor());
      }
    }

    setCallbackAction(callbackAction);
  }

  private void setCallbackAction(@CallbackAction int callbackAction) {
    this.callbackAction = callbackAction;
    switch (callbackAction) {
      case CallbackAction.LIGHTBRINGER:
      case CallbackAction.IMS_VIDEO:
        callbackButton.setVisibility(View.VISIBLE);
        callbackButton.setImageResource(R.drawable.quantum_ic_videocam_vd_theme_24);
        break;
      case CallbackAction.VOICE:
        callbackButton.setVisibility(View.VISIBLE);
        callbackButton.setImageResource(R.drawable.quantum_ic_call_vd_theme_24);
        break;
      case CallbackAction.NONE:
        callbackButton.setVisibility(View.GONE);
        break;
      default:
        throw Assert.createIllegalStateFailException("Invalid action: " + callbackAction);
    }
  }

  @Override
  public void onClick(View view) {
    if (view == callbackButton) {
      switch (callbackAction) {
        case CallbackAction.IMS_VIDEO:
          callbackActionListener.placeImsVideoCall(contact.getNumber());
          break;
        case CallbackAction.LIGHTBRINGER:
          callbackActionListener.placeLightbringerCall(contact.getNumber());
          break;
        case CallbackAction.VOICE:
          callbackActionListener.placeVoiceCall(contact.getNumber(), contact.getPostDialDigits());
          break;
        case CallbackAction.NONE:
        default:
          throw Assert.createIllegalStateFailException("Invalid action: " + callbackAction);
      }
    } else {
      throw Assert.createIllegalStateFailException("View OnClickListener not implemented: " + view);
    }
  }

  /** Listener for making a callback */
  interface CallbackActionListener {

    /** Places an IMS video call. */
    void placeImsVideoCall(String phoneNumber);

    /** Places a Lightbringer call. */
    void placeLightbringerCall(String phoneNumber);

    /** Place a traditional voice call. */
    void placeVoiceCall(String phoneNumber, String postDialDigits);
  }
}
