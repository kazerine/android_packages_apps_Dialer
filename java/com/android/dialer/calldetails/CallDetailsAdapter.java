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
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.callcomposer.nano.CallComposerContact;
import com.android.dialer.calldetails.nano.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calllogutils.CallTypeHelper;
import com.android.dialer.common.Assert;

/** Adapter for RecyclerView in {@link CallDetailsActivity}. */
public class CallDetailsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  private static final int HEADER_VIEW_TYPE = 1;
  private static final int CALL_ENTRY_VIEW_TYPE = 2;
  private static final int FOOTER_VIEW_TYPE = 3;

  private final CallComposerContact contact;
  private final CallDetailsEntry[] callDetailsEntries;
  private final CallTypeHelper callTypeHelper;

  public CallDetailsAdapter(
      Context context, CallComposerContact contact, CallDetailsEntry[] callDetailsEntries) {
    this.contact = Assert.isNotNull(contact);
    this.callDetailsEntries = Assert.isNotNull(callDetailsEntries);
    callTypeHelper = new CallTypeHelper(context.getResources());
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    switch (viewType) {
      case HEADER_VIEW_TYPE:
        return new CallDetailsHeaderViewHolder(
            inflater.inflate(R.layout.contact_container, parent, false));
      case CALL_ENTRY_VIEW_TYPE:
        return new CallDetailsEntryViewHolder(
            inflater.inflate(R.layout.call_details_entry, parent, false));
      case FOOTER_VIEW_TYPE:
        return new CallDetailsFooterViewHolder(
            inflater.inflate(R.layout.call_details_footer, parent, false));
      default:
        Assert.fail("No ViewHolder available for viewType: " + viewType);
        return null;
    }
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    if (position == 0) { // Header
      ((CallDetailsHeaderViewHolder) holder).updateContactInfo(contact);
    } else if (position == getItemCount() - 1) {
      ((CallDetailsFooterViewHolder) holder).setPhoneNumber(contact.number);
    } else {
      CallDetailsEntryViewHolder viewHolder = (CallDetailsEntryViewHolder) holder;
      viewHolder.setCallDetails(
          contact.number,
          callDetailsEntries[position - 1],
          callTypeHelper,
          position != getItemCount() - 2);
    }
  }

  @Override
  public int getItemViewType(int position) {
    if (position == 0) { // Header
      return HEADER_VIEW_TYPE;
    } else if (position == getItemCount() - 1) {
      return FOOTER_VIEW_TYPE;
    } else {
      return CALL_ENTRY_VIEW_TYPE;
    }
  }

  @Override
  public int getItemCount() {
    return callDetailsEntries.length + 2; // Header + footer
  }
}
