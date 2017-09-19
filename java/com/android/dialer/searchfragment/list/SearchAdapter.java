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

package com.android.dialer.searchfragment.list;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import com.android.dialer.callcomposer.CallComposerActivity;
import com.android.dialer.callintent.CallInitiationType;
import com.android.dialer.callintent.CallIntentBuilder;
import com.android.dialer.callintent.CallSpecificAppData;
import com.android.dialer.common.Assert;
import com.android.dialer.constants.ActivityRequestCodes;
import com.android.dialer.dialercontact.DialerContact;
import com.android.dialer.lightbringer.LightbringerComponent;
import com.android.dialer.logging.DialerImpression;
import com.android.dialer.logging.Logger;
import com.android.dialer.searchfragment.common.RowClickListener;
import com.android.dialer.searchfragment.common.SearchCursor;
import com.android.dialer.searchfragment.cp2.SearchContactViewHolder;
import com.android.dialer.searchfragment.list.SearchCursorManager.RowType;
import com.android.dialer.searchfragment.nearbyplaces.NearbyPlaceViewHolder;
import com.android.dialer.searchfragment.remote.RemoteContactViewHolder;
import com.android.dialer.util.DialerUtils;
import java.util.List;

/** RecyclerView adapter for {@link NewSearchFragment}. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public final class SearchAdapter extends RecyclerView.Adapter<ViewHolder>
    implements RowClickListener {

  private final SearchCursorManager searchCursorManager;
  private final Activity activity;

  private boolean showZeroSuggest;
  private String query;
  private CallInitiationType.Type callInitiationType = CallInitiationType.Type.UNKNOWN_INITIATION;

  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public SearchAdapter(Activity activity, SearchCursorManager searchCursorManager) {
    this.activity = activity;
    this.searchCursorManager = searchCursorManager;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup root, @RowType int rowType) {
    switch (rowType) {
      case RowType.CONTACT_ROW:
        return new SearchContactViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.search_contact_row, root, false), this);
      case RowType.NEARBY_PLACES_ROW:
        return new NearbyPlaceViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.search_contact_row, root, false));
      case RowType.CONTACT_HEADER:
      case RowType.DIRECTORY_HEADER:
      case RowType.NEARBY_PLACES_HEADER:
        return new HeaderViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.header_layout, root, false));
      case RowType.DIRECTORY_ROW:
        return new RemoteContactViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.search_contact_row, root, false));
      case RowType.SEARCH_ACTION:
        return new SearchActionViewHolder(
            LayoutInflater.from(activity).inflate(R.layout.search_action_layout, root, false));
      case RowType.INVALID:
      default:
        throw Assert.createIllegalStateFailException("Invalid RowType: " + rowType);
    }
  }

  @Override
  public @RowType int getItemViewType(int position) {
    return searchCursorManager.getRowType(position);
  }

  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    if (holder instanceof SearchContactViewHolder) {
      ((SearchContactViewHolder) holder).bind(searchCursorManager.getCursor(position), query);
    } else if (holder instanceof NearbyPlaceViewHolder) {
      ((NearbyPlaceViewHolder) holder).bind(searchCursorManager.getCursor(position), query);
    } else if (holder instanceof RemoteContactViewHolder) {
      ((RemoteContactViewHolder) holder).bind(searchCursorManager.getCursor(position), query);
    } else if (holder instanceof HeaderViewHolder) {
      String header =
          searchCursorManager.getCursor(position).getString(SearchCursor.HEADER_TEXT_POSITION);
      ((HeaderViewHolder) holder).setHeader(header);
    } else if (holder instanceof SearchActionViewHolder) {
      ((SearchActionViewHolder) holder)
          .setAction(searchCursorManager.getSearchAction(position), position, query);
    } else {
      throw Assert.createIllegalStateFailException("Invalid ViewHolder: " + holder);
    }
  }

  public void setContactsCursor(SearchCursor cursor) {
    if (searchCursorManager.setContactsCursor(cursor)) {
      // Since this is a new contacts cursor, we need to reapply the filter.
      searchCursorManager.setQuery(query);
      notifyDataSetChanged();
    }
  }

  void clear() {
    searchCursorManager.clear();
  }

  @Override
  public int getItemCount() {
    if (TextUtils.isEmpty(query) && !showZeroSuggest) {
      return 0;
    }
    return searchCursorManager.getCount();
  }

  /**
   * @param visible If true and query is empty, the adapter won't show any list elements.
   * @see #setQuery(String)
   * @see #getItemCount()
   */
  public void setZeroSuggestVisible(boolean visible) {
    showZeroSuggest = visible;
  }

  public void setQuery(String query) {
    this.query = query;
    if (searchCursorManager.setQuery(query)) {
      notifyDataSetChanged();
    }
  }

  /** Sets the actions to be shown at the bottom of the search results. */
  void setSearchActions(List<Integer> actions) {
    if (searchCursorManager.setSearchActions(actions)) {
      notifyDataSetChanged();
    }
  }

  void setCallInitiationType(CallInitiationType.Type callInitiationType) {
    this.callInitiationType = callInitiationType;
  }

  public void setNearbyPlacesCursor(SearchCursor nearbyPlacesCursor) {
    if (searchCursorManager.setNearbyPlacesCursor(nearbyPlacesCursor)) {
      notifyDataSetChanged();
    }
  }

  public void setRemoteContactsCursor(SearchCursor remoteContactsCursor) {
    if (searchCursorManager.setCorpDirectoryCursor(remoteContactsCursor)) {
      notifyDataSetChanged();
    }
  }

  @Override
  public void placeVoiceCall(String phoneNumber, int ranking) {
    placeCall(phoneNumber, ranking, false);
  }

  @Override
  public void placeVideoCall(String phoneNumber, int ranking) {
    placeCall(phoneNumber, ranking, true);
  }

  private void placeCall(String phoneNumber, int position, boolean isVideoCall) {
    CallSpecificAppData callSpecificAppData =
        CallSpecificAppData.newBuilder()
            .setCallInitiationType(callInitiationType)
            .setPositionOfSelectedSearchResult(position)
            .setCharactersInSearchString(query == null ? 0 : query.length())
            .build();
    Intent intent =
        new CallIntentBuilder(phoneNumber, callSpecificAppData).setIsVideoCall(isVideoCall).build();
    DialerUtils.startActivityWithErrorToast(activity, intent);
  }

  @Override
  public void placeDuoCall(String phoneNumber) {
    Logger.get(activity)
        .logImpression(DialerImpression.Type.LIGHTBRINGER_VIDEO_REQUESTED_FROM_SEARCH);
    Intent intent =
        LightbringerComponent.get(activity).getLightbringer().getIntent(activity, phoneNumber);
    activity.startActivityForResult(intent, ActivityRequestCodes.DIALTACTS_LIGHTBRINGER);
  }

  @Override
  public void openCallAndShare(DialerContact contact) {
    Intent intent = CallComposerActivity.newIntent(activity, contact);
    DialerUtils.startActivityWithErrorToast(activity, intent);
  }
}
