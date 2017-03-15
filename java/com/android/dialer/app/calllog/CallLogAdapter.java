/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.dialer.app.calllog;

import android.app.Activity;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Trace;
import android.provider.CallLog;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.telecom.PhoneAccountHandle;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.compat.PhoneNumberUtilsCompat;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.dialer.app.Bindings;
import com.android.dialer.app.DialtactsActivity;
import com.android.dialer.app.R;
import com.android.dialer.app.calllog.CallLogGroupBuilder.GroupCreator;
import com.android.dialer.app.calllog.calllogcache.CallLogCache;
import com.android.dialer.app.contactinfo.ContactInfoCache;
import com.android.dialer.app.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.app.voicemail.VoicemailPlaybackPresenter.OnVoicemailDeletedListener;
import com.android.dialer.blocking.FilteredNumberAsyncQueryHandler;
import com.android.dialer.calldetails.nano.CallDetailsEntries;
import com.android.dialer.calldetails.nano.CallDetailsEntries.CallDetailsEntry;
import com.android.dialer.calllogutils.PhoneAccountUtils;
import com.android.dialer.calllogutils.PhoneCallDetails;
import com.android.dialer.common.Assert;
import com.android.dialer.common.AsyncTaskExecutor;
import com.android.dialer.common.AsyncTaskExecutors;
import com.android.dialer.common.LogUtil;
import com.android.dialer.enrichedcall.EnrichedCallCapabilities;
import com.android.dialer.enrichedcall.EnrichedCallComponent;
import com.android.dialer.enrichedcall.EnrichedCallManager;
import com.android.dialer.enrichedcall.EnrichedCallManager.CapabilitiesListener;
import com.android.dialer.enrichedcall.historyquery.proto.nano.HistoryResult;
import com.android.dialer.logging.Logger;
import com.android.dialer.logging.nano.DialerImpression;
import com.android.dialer.phonenumbercache.CallLogQuery;
import com.android.dialer.phonenumbercache.ContactInfo;
import com.android.dialer.phonenumbercache.ContactInfoHelper;
import com.android.dialer.phonenumberutil.PhoneNumberHelper;
import com.android.dialer.spam.Spam;
import com.android.dialer.util.PermissionsUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adapter class to fill in data for the Call Log. */
public class CallLogAdapter extends GroupingListAdapter
    implements GroupCreator, OnVoicemailDeletedListener, CapabilitiesListener {

  // Types of activities the call log adapter is used for
  public static final int ACTIVITY_TYPE_CALL_LOG = 1;
  public static final int ACTIVITY_TYPE_DIALTACTS = 2;
  private static final int NO_EXPANDED_LIST_ITEM = -1;
  public static final int ALERT_POSITION = 0;
  private static final int VIEW_TYPE_ALERT = 1;
  private static final int VIEW_TYPE_CALLLOG = 2;

  private static final String KEY_EXPANDED_POSITION = "expanded_position";
  private static final String KEY_EXPANDED_ROW_ID = "expanded_row_id";

  public static final String LOAD_DATA_TASK_IDENTIFIER = "load_data";

  protected final Activity mActivity;
  protected final VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;
  /** Cache for repeated requests to Telecom/Telephony. */
  protected final CallLogCache mCallLogCache;

  private final CallFetcher mCallFetcher;
  @NonNull private final FilteredNumberAsyncQueryHandler mFilteredNumberAsyncQueryHandler;
  private final int mActivityType;

  /** Instance of helper class for managing views. */
  private final CallLogListItemHelper mCallLogListItemHelper;
  /** Helper to group call log entries. */
  private final CallLogGroupBuilder mCallLogGroupBuilder;

  private final AsyncTaskExecutor mAsyncTaskExecutor = AsyncTaskExecutors.createAsyncTaskExecutor();
  private ContactInfoCache mContactInfoCache;
  // Tracks the position of the currently expanded list item.
  private int mCurrentlyExpandedPosition = RecyclerView.NO_POSITION;
  // Tracks the rowId of the currently expanded list item, so the position can be updated if there
  // are any changes to the call log entries, such as additions or removals.
  private long mCurrentlyExpandedRowId = NO_EXPANDED_LIST_ITEM;

  private final CallLogAlertManager mCallLogAlertManager;
  /** The OnClickListener used to expand or collapse the action buttons of a call log entry. */
  private final View.OnClickListener mExpandCollapseListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          CallLogListItemViewHolder viewHolder = (CallLogListItemViewHolder) v.getTag();
          if (viewHolder == null) {
            return;
          }

          if (mVoicemailPlaybackPresenter != null) {
            // Always reset the voicemail playback state on expand or collapse.
            mVoicemailPlaybackPresenter.resetAll();
          }

          if (viewHolder.rowId == mCurrentlyExpandedRowId) {
            // Hide actions, if the clicked item is the expanded item.
            viewHolder.showActions(false);

            mCurrentlyExpandedPosition = RecyclerView.NO_POSITION;
            mCurrentlyExpandedRowId = NO_EXPANDED_LIST_ITEM;
          } else {
            if (viewHolder.callType == CallLog.Calls.MISSED_TYPE) {
              CallLogAsyncTaskUtil.markCallAsRead(mActivity, viewHolder.callIds);
              if (mActivityType == ACTIVITY_TYPE_DIALTACTS) {
                ((DialtactsActivity) v.getContext()).updateTabUnreadCounts();
              }
            }
            expandViewHolderActions(viewHolder);
          }
        }
      };

  /**
   * A list of {@link CallLogQuery#ID} that will be hidden. The hide might be temporary so instead
   * if removing an item, it will be shown as an invisible view. This simplifies the calculation of
   * item position.
   */
  @NonNull private Set<Long> mHiddenRowIds = new ArraySet<>();
  /**
   * Holds a list of URIs that are pending deletion or undo. If the activity ends before the undo
   * timeout, all of the pending URIs will be deleted.
   *
   * <p>TODO: move this and OnVoicemailDeletedListener to somewhere like {@link
   * VisualVoicemailCallLogFragment}. The CallLogAdapter does not need to know about what to do with
   * hidden item or what to hide.
   */
  @NonNull private final Set<Uri> mHiddenItemUris = new ArraySet<>();

  private CallLogListItemViewHolder.OnClickListener mBlockReportSpamListener;
  /**
   * Map, keyed by call Id, used to track the day group for a call. As call log entries are put into
   * the primary call groups in {@link com.android.dialer.app.calllog.CallLogGroupBuilder}, they are
   * also assigned a secondary "day group". This map tracks the day group assigned to all calls in
   * the call log. This information is used to trigger the display of a day group header above the
   * call log entry at the start of a day group. Note: Multiple calls are grouped into a single
   * primary "call group" in the call log, and the cursor used to bind rows includes all of these
   * calls. When determining if a day group change has occurred it is necessary to look at the last
   * entry in the call log to determine its day group. This map provides a means of determining the
   * previous day group without having to reverse the cursor to the start of the previous day call
   * log entry.
   */
  private Map<Long, Integer> mDayGroups = new ArrayMap<>();

  private boolean mLoading = true;
  private ContactsPreferences mContactsPreferences;

  private boolean mIsSpamEnabled;

  public CallLogAdapter(
      Activity activity,
      ViewGroup alertContainer,
      CallFetcher callFetcher,
      CallLogCache callLogCache,
      ContactInfoCache contactInfoCache,
      VoicemailPlaybackPresenter voicemailPlaybackPresenter,
      @NonNull FilteredNumberAsyncQueryHandler filteredNumberAsyncQueryHandler,
      int activityType) {
    super();

    mActivity = activity;
    mCallFetcher = callFetcher;
    mVoicemailPlaybackPresenter = voicemailPlaybackPresenter;
    if (mVoicemailPlaybackPresenter != null) {
      mVoicemailPlaybackPresenter.setOnVoicemailDeletedListener(this);
    }

    mActivityType = activityType;

    mContactInfoCache = contactInfoCache;

    if (!PermissionsUtil.hasContactsPermissions(activity)) {
      mContactInfoCache.disableRequestProcessing();
    }

    Resources resources = mActivity.getResources();

    mCallLogCache = callLogCache;

    PhoneCallDetailsHelper phoneCallDetailsHelper =
        new PhoneCallDetailsHelper(mActivity, resources, mCallLogCache);
    mCallLogListItemHelper =
        new CallLogListItemHelper(phoneCallDetailsHelper, resources, mCallLogCache);
    mCallLogGroupBuilder = new CallLogGroupBuilder(this);
    mFilteredNumberAsyncQueryHandler = Assert.isNotNull(filteredNumberAsyncQueryHandler);

    mContactsPreferences = new ContactsPreferences(mActivity);

    mBlockReportSpamListener =
        new BlockReportSpamListener(
            mActivity,
            ((Activity) mActivity).getFragmentManager(),
            this,
            mFilteredNumberAsyncQueryHandler);
    setHasStableIds(true);

    mCallLogAlertManager =
        new CallLogAlertManager(this, LayoutInflater.from(mActivity), alertContainer);
  }

  private void expandViewHolderActions(CallLogListItemViewHolder viewHolder) {
    if (!TextUtils.isEmpty(viewHolder.voicemailUri)) {
      Logger.get(mActivity).logImpression(DialerImpression.Type.VOICEMAIL_EXPAND_ENTRY);
    }

    int lastExpandedPosition = mCurrentlyExpandedPosition;
    // Show the actions for the clicked list item.
    viewHolder.showActions(true);
    mCurrentlyExpandedPosition = viewHolder.getAdapterPosition();
    mCurrentlyExpandedRowId = viewHolder.rowId;

    // If another item is expanded, notify it that it has changed. Its actions will be
    // hidden when it is re-binded because we change mCurrentlyExpandedRowId above.
    if (lastExpandedPosition != RecyclerView.NO_POSITION) {
      notifyItemChanged(lastExpandedPosition);
    }
  }

  public void onSaveInstanceState(Bundle outState) {
    outState.putInt(KEY_EXPANDED_POSITION, mCurrentlyExpandedPosition);
    outState.putLong(KEY_EXPANDED_ROW_ID, mCurrentlyExpandedRowId);
  }

  public void onRestoreInstanceState(Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      mCurrentlyExpandedPosition =
          savedInstanceState.getInt(KEY_EXPANDED_POSITION, RecyclerView.NO_POSITION);
      mCurrentlyExpandedRowId =
          savedInstanceState.getLong(KEY_EXPANDED_ROW_ID, NO_EXPANDED_LIST_ITEM);
    }
  }

  /** Requery on background thread when {@link Cursor} changes. */
  @Override
  protected void onContentChanged() {
    mCallFetcher.fetchCalls();
  }

  public void setLoading(boolean loading) {
    mLoading = loading;
  }

  public boolean isEmpty() {
    if (mLoading) {
      // We don't want the empty state to show when loading.
      return false;
    } else {
      return getItemCount() == 0;
    }
  }

  public void clearFilteredNumbersCache() {
    mFilteredNumberAsyncQueryHandler.clearCache();
  }

  public void onResume() {
    if (PermissionsUtil.hasPermission(mActivity, android.Manifest.permission.READ_CONTACTS)) {
      mContactInfoCache.start();
    }
    mContactsPreferences.refreshValue(ContactsPreferences.DISPLAY_ORDER_KEY);
    mIsSpamEnabled = Spam.get(mActivity).isSpamEnabled();
    getEnrichedCallManager().registerCapabilitiesListener(this);
    notifyDataSetChanged();
  }

  public void onPause() {
    pauseCache();
    for (Uri uri : mHiddenItemUris) {
      CallLogAsyncTaskUtil.deleteVoicemail(mActivity, uri, null);
    }
    getEnrichedCallManager().unregisterCapabilitiesListener(this);
  }

  public void onStop() {
    getEnrichedCallManager().clearCachedData();
  }

  public CallLogAlertManager getAlertManager() {
    return mCallLogAlertManager;
  }

  @VisibleForTesting
  /* package */ void pauseCache() {
    mContactInfoCache.stop();
    mCallLogCache.reset();
  }

  @Override
  protected void addGroups(Cursor cursor) {
    mCallLogGroupBuilder.addGroups(cursor);
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
    if (viewType == VIEW_TYPE_ALERT) {
      return mCallLogAlertManager.createViewHolder(parent);
    }
    return createCallLogEntryViewHolder(parent);
  }

  /**
   * Creates a new call log entry {@link ViewHolder}.
   *
   * @param parent the parent view.
   * @return The {@link ViewHolder}.
   */
  private ViewHolder createCallLogEntryViewHolder(ViewGroup parent) {
    LayoutInflater inflater = LayoutInflater.from(mActivity);
    View view = inflater.inflate(R.layout.call_log_list_item, parent, false);
    CallLogListItemViewHolder viewHolder =
        CallLogListItemViewHolder.create(
            view,
            mActivity,
            mBlockReportSpamListener,
            mExpandCollapseListener,
            mCallLogCache,
            mCallLogListItemHelper,
            mVoicemailPlaybackPresenter);

    viewHolder.callLogEntryView.setTag(viewHolder);

    viewHolder.primaryActionView.setTag(viewHolder);

    return viewHolder;
  }

  /**
   * Binds the views in the entry to the data in the call log. TODO: This gets called 20-30 times
   * when Dialer starts up for a single call log entry and should not. It invokes cross-process
   * methods and the repeat execution can get costly.
   *
   * @param viewHolder The view corresponding to this entry.
   * @param position The position of the entry.
   */
  @Override
  public void onBindViewHolder(ViewHolder viewHolder, int position) {
    Trace.beginSection("onBindViewHolder: " + position);
    switch (getItemViewType(position)) {
      case VIEW_TYPE_ALERT:
        //Do nothing
        break;
      default:
        bindCallLogListViewHolder(viewHolder, position);
        break;
    }
    Trace.endSection();
  }

  @Override
  public void onViewRecycled(ViewHolder viewHolder) {
    if (viewHolder.getItemViewType() == VIEW_TYPE_CALLLOG) {
      CallLogListItemViewHolder views = (CallLogListItemViewHolder) viewHolder;
      if (views.asyncTask != null) {
        views.asyncTask.cancel(true);
      }
    }
  }

  @Override
  public void onViewAttachedToWindow(ViewHolder viewHolder) {
    if (viewHolder.getItemViewType() == VIEW_TYPE_CALLLOG) {
      ((CallLogListItemViewHolder) viewHolder).isAttachedToWindow = true;
    }
  }

  @Override
  public void onViewDetachedFromWindow(ViewHolder viewHolder) {
    if (viewHolder.getItemViewType() == VIEW_TYPE_CALLLOG) {
      ((CallLogListItemViewHolder) viewHolder).isAttachedToWindow = false;
    }
  }

  /**
   * Binds the view holder for the call log list item view.
   *
   * @param viewHolder The call log list item view holder.
   * @param position The position of the list item.
   */
  private void bindCallLogListViewHolder(final ViewHolder viewHolder, final int position) {
    Cursor c = (Cursor) getItem(position);
    if (c == null) {
      return;
    }
    CallLogListItemViewHolder views = (CallLogListItemViewHolder) viewHolder;
    views.isLoaded = false;
    int groupSize = getGroupSize(position);
    CallDetailsEntries callDetailsEntries = createCallDetailsEntries(c, groupSize);
    PhoneCallDetails details = createPhoneCallDetails(c, groupSize, views);
    if (mHiddenRowIds.contains(c.getLong(CallLogQuery.ID))) {
      views.callLogEntryView.setVisibility(View.GONE);
      views.dayGroupHeader.setVisibility(View.GONE);
      return;
    } else {
      views.callLogEntryView.setVisibility(View.VISIBLE);
      // dayGroupHeader will be restored after loadAndRender() if it is needed.
    }
    if (mCurrentlyExpandedRowId == views.rowId) {
      views.inflateActionViewStub();
    }
    loadAndRender(views, views.rowId, details, callDetailsEntries);
  }

  private void loadAndRender(
      final CallLogListItemViewHolder views,
      final long rowId,
      final PhoneCallDetails details,
      final CallDetailsEntries callDetailsEntries) {
    // Reset block and spam information since this view could be reused which may contain
    // outdated data.
    views.isSpam = false;
    views.blockId = null;
    views.isSpamFeatureEnabled = false;
    views.isCallComposerCapable =
        isCallComposerCapable(PhoneNumberUtils.formatNumberToE164(views.number, views.countryIso));
    final AsyncTask<Void, Void, Boolean> loadDataTask =
        new AsyncTask<Void, Void, Boolean>() {
          @Override
          protected Boolean doInBackground(Void... params) {
            views.blockId =
                mFilteredNumberAsyncQueryHandler.getBlockedIdSynchronousForCalllogOnly(
                    views.number, views.countryIso);
            details.isBlocked = views.blockId != null;
            if (isCancelled()) {
              return false;
            }
            if (mIsSpamEnabled) {
              views.isSpamFeatureEnabled = true;
              // Only display the call as a spam call if there are incoming calls in the list.
              // Call log cards with only outgoing calls should never be displayed as spam.
              views.isSpam =
                  details.hasIncomingCalls()
                      && Spam.get(mActivity)
                          .checkSpamStatusSynchronous(views.number, views.countryIso);
              details.isSpam = views.isSpam;
            }
            if (isCancelled()) {
              return false;
            }
            setCallDetailsEntriesHistoryResults(
                PhoneNumberUtils.formatNumberToE164(views.number, views.countryIso),
                callDetailsEntries);
            views.setDetailedPhoneDetails(callDetailsEntries);
            return !isCancelled() && loadData(views, rowId, details);
          }

          private void setCallDetailsEntriesHistoryResults(
              @Nullable String number, CallDetailsEntries callDetailsEntries) {
            if (number == null) {
              return;
            }
            Map<CallDetailsEntry, List<HistoryResult>> mappedResults =
                getEnrichedCallManager().getAllHistoricalData(number, callDetailsEntries);
            for (CallDetailsEntry entry : callDetailsEntries.entries) {
              List<HistoryResult> results = mappedResults.get(entry);
              if (results != null) {
                entry.historyResults = mappedResults.get(entry).toArray(new HistoryResult[0]);
                LogUtil.v(
                    "CallLogAdapter.setCallDetailsEntriesHistoryResults",
                    "mapped %d results",
                    entry.historyResults.length);
              }
            }
          }

          @Override
          protected void onPostExecute(Boolean success) {
            views.isLoaded = true;
            if (success) {
              int currentGroup = getDayGroupForCall(views.rowId);
              if (currentGroup != details.previousGroup) {
                views.dayGroupHeaderVisibility = View.VISIBLE;
                views.dayGroupHeaderText = getGroupDescription(currentGroup);
              } else {
                views.dayGroupHeaderVisibility = View.GONE;
              }
              render(views, details, rowId);
            }
          }
        };

    views.asyncTask = loadDataTask;
    mAsyncTaskExecutor.submit(LOAD_DATA_TASK_IDENTIFIER, loadDataTask);
  }

  @MainThread
  private boolean isCallComposerCapable(@Nullable String e164Number) {
    if (e164Number == null) {
      return false;
    }

    EnrichedCallCapabilities capabilities = getEnrichedCallManager().getCapabilities(e164Number);
    if (capabilities == null) {
      getEnrichedCallManager().requestCapabilities(e164Number);
      return false;
    }
    return capabilities.supportsCallComposer();
  }

  /**
   * Initialize PhoneCallDetails by reading all data from cursor. This method must be run on main
   * thread since cursor is not thread safe.
   */
  @MainThread
  private PhoneCallDetails createPhoneCallDetails(
      Cursor cursor, int count, final CallLogListItemViewHolder views) {
    Assert.isMainThread();
    final String number = cursor.getString(CallLogQuery.NUMBER);
    final String postDialDigits =
        (VERSION.SDK_INT >= VERSION_CODES.N) ? cursor.getString(CallLogQuery.POST_DIAL_DIGITS) : "";
    final String viaNumber =
        (VERSION.SDK_INT >= VERSION_CODES.N) ? cursor.getString(CallLogQuery.VIA_NUMBER) : "";
    final int numberPresentation = cursor.getInt(CallLogQuery.NUMBER_PRESENTATION);
    final ContactInfo cachedContactInfo = ContactInfoHelper.getContactInfo(cursor);
    final PhoneCallDetails details =
        new PhoneCallDetails(number, numberPresentation, postDialDigits);
    details.viaNumber = viaNumber;
    details.countryIso = cursor.getString(CallLogQuery.COUNTRY_ISO);
    details.date = cursor.getLong(CallLogQuery.DATE);
    details.duration = cursor.getLong(CallLogQuery.DURATION);
    details.features = getCallFeatures(cursor, count);
    details.geocode = cursor.getString(CallLogQuery.GEOCODED_LOCATION);
    details.transcription = cursor.getString(CallLogQuery.TRANSCRIPTION);
    details.callTypes = getCallTypes(cursor, count);

    details.accountComponentName = cursor.getString(CallLogQuery.ACCOUNT_COMPONENT_NAME);
    details.accountId = cursor.getString(CallLogQuery.ACCOUNT_ID);
    details.cachedContactInfo = cachedContactInfo;

    if (!cursor.isNull(CallLogQuery.DATA_USAGE)) {
      details.dataUsage = cursor.getLong(CallLogQuery.DATA_USAGE);
    }

    views.rowId = cursor.getLong(CallLogQuery.ID);
    // Stash away the Ids of the calls so that we can support deleting a row in the call log.
    views.callIds = getCallIds(cursor, count);
    details.previousGroup = getPreviousDayGroup(cursor);

    // Store values used when the actions ViewStub is inflated on expansion.
    views.number = number;
    views.countryIso = details.countryIso;
    views.postDialDigits = details.postDialDigits;
    views.numberPresentation = numberPresentation;

    if (details.callTypes[0] == CallLog.Calls.VOICEMAIL_TYPE
        || details.callTypes[0] == CallLog.Calls.MISSED_TYPE) {
      details.isRead = cursor.getInt(CallLogQuery.IS_READ) == 1;
    }
    views.callType = cursor.getInt(CallLogQuery.CALL_TYPE);
    views.voicemailUri = cursor.getString(CallLogQuery.VOICEMAIL_URI);

    return details;
  }

  @MainThread
  private static CallDetailsEntries createCallDetailsEntries(Cursor cursor, int count) {
    Assert.isMainThread();
    int position = cursor.getPosition();
    CallDetailsEntries entries = new CallDetailsEntries();
    entries.entries = new CallDetailsEntry[count];
    for (int i = 0; i < count; i++) {
      CallDetailsEntry entry = new CallDetailsEntry();
      entry.callId = cursor.getLong(CallLogQuery.ID);
      entry.callType = cursor.getInt(CallLogQuery.CALL_TYPE);
      entry.dataUsage = cursor.getLong(CallLogQuery.DATA_USAGE);
      entry.date = cursor.getLong(CallLogQuery.DATE);
      entry.duration = cursor.getLong(CallLogQuery.DURATION);
      entry.features |= cursor.getInt(CallLogQuery.FEATURES);
      entries.entries[i] = entry;
      cursor.moveToNext();
    }
    cursor.moveToPosition(position);
    return entries;
  }

  /**
   * Load data for call log. Any expensive operation should be put here to avoid blocking main
   * thread. Do NOT put any cursor operation here since it's not thread safe.
   */
  @WorkerThread
  private boolean loadData(CallLogListItemViewHolder views, long rowId, PhoneCallDetails details) {
    Assert.isWorkerThread();
    if (rowId != views.rowId) {
      LogUtil.i(
          "CallLogAdapter.loadData",
          "rowId of viewHolder changed after load task is issued, aborting load");
      return false;
    }

    final PhoneAccountHandle accountHandle =
        PhoneAccountUtils.getAccount(details.accountComponentName, details.accountId);

    final boolean isVoicemailNumber =
        mCallLogCache.isVoicemailNumber(accountHandle, details.number);

    // Note: Binding of the action buttons is done as required in configureActionViews when the
    // user expands the actions ViewStub.

    ContactInfo info = ContactInfo.EMPTY;
    if (PhoneNumberHelper.canPlaceCallsTo(details.number, details.numberPresentation)
        && !isVoicemailNumber) {
      // Lookup contacts with this number
      // Only do remote lookup in first 5 rows.
      info =
          mContactInfoCache.getValue(
              details.number + details.postDialDigits,
              details.countryIso,
              details.cachedContactInfo,
              rowId
                  < Bindings.get(mActivity)
                      .getConfigProvider()
                      .getLong("number_of_call_to_do_remote_lookup", 5L));
    }
    CharSequence formattedNumber =
        info.formattedNumber == null
            ? null
            : PhoneNumberUtilsCompat.createTtsSpannable(info.formattedNumber);
    details.updateDisplayNumber(mActivity, formattedNumber, isVoicemailNumber);

    views.displayNumber = details.displayNumber;
    views.accountHandle = accountHandle;
    details.accountHandle = accountHandle;

    if (!TextUtils.isEmpty(info.name) || !TextUtils.isEmpty(info.nameAlternative)) {
      details.contactUri = info.lookupUri;
      details.namePrimary = info.name;
      details.nameAlternative = info.nameAlternative;
      details.nameDisplayOrder = mContactsPreferences.getDisplayOrder();
      details.numberType = info.type;
      details.numberLabel = info.label;
      details.photoUri = info.photoUri;
      details.sourceType = info.sourceType;
      details.objectId = info.objectId;
      details.contactUserType = info.userType;
    }

    views.info = info;
    views.numberType =
        (String)
            Phone.getTypeLabel(mActivity.getResources(), details.numberType, details.numberLabel);

    mCallLogListItemHelper.updatePhoneCallDetails(details);
    return true;
  }

  /**
   * Render item view given position. This is running on UI thread so DO NOT put any expensive
   * operation into it.
   */
  @MainThread
  private void render(CallLogListItemViewHolder views, PhoneCallDetails details, long rowId) {
    Assert.isMainThread();
    if (rowId != views.rowId) {
      LogUtil.i(
          "CallLogAdapter.render",
          "rowId of viewHolder changed after load task is issued, aborting render");
      return;
    }

    // Default case: an item in the call log.
    views.primaryActionView.setVisibility(View.VISIBLE);
    views.workIconView.setVisibility(
        details.contactUserType == ContactsUtils.USER_TYPE_WORK ? View.VISIBLE : View.GONE);

    mCallLogListItemHelper.setPhoneCallDetails(views, details);
    if (mCurrentlyExpandedRowId == views.rowId) {
      // In case ViewHolders were added/removed, update the expanded position if the rowIds
      // match so that we can restore the correct expanded state on rebind.
      mCurrentlyExpandedPosition = views.getAdapterPosition();
      views.showActions(true);
    } else {
      views.showActions(false);
    }
    views.dayGroupHeader.setVisibility(views.dayGroupHeaderVisibility);
    views.dayGroupHeader.setText(views.dayGroupHeaderText);
  }

  @Override
  public int getItemCount() {
    return super.getItemCount() + (mCallLogAlertManager.isEmpty() ? 0 : 1);
  }

  @Override
  public int getItemViewType(int position) {
    if (position == ALERT_POSITION && !mCallLogAlertManager.isEmpty()) {
      return VIEW_TYPE_ALERT;
    }
    return VIEW_TYPE_CALLLOG;
  }

  /**
   * Retrieves an item at the specified position, taking into account the presence of a promo card.
   *
   * @param position The position to retrieve.
   * @return The item at that position.
   */
  @Override
  public Object getItem(int position) {
    return super.getItem(position - (mCallLogAlertManager.isEmpty() ? 0 : 1));
  }

  @Override
  public long getItemId(int position) {
    Cursor cursor = (Cursor) getItem(position);
    if (cursor != null) {
      return cursor.getLong(CallLogQuery.ID);
    } else {
      return 0;
    }
  }

  @Override
  public int getGroupSize(int position) {
    return super.getGroupSize(position - (mCallLogAlertManager.isEmpty() ? 0 : 1));
  }

  protected boolean isCallLogActivity() {
    return mActivityType == ACTIVITY_TYPE_CALL_LOG;
  }

  /**
   * In order to implement the "undo" function, when a voicemail is "deleted" i.e. when the user
   * clicks the delete button, the deleted item is temporarily hidden from the list. If a user
   * clicks delete on a second item before the first item's undo option has expired, the first item
   * is immediately deleted so that only one item can be "undoed" at a time.
   */
  @Override
  public void onVoicemailDeleted(CallLogListItemViewHolder viewHolder, Uri uri) {
    mHiddenRowIds.add(viewHolder.rowId);
    // Save the new hidden item uri in case the activity is suspend before the undo has timed out.
    mHiddenItemUris.add(uri);

    collapseExpandedCard();
    notifyItemChanged(viewHolder.getAdapterPosition());
    // The next item might have to update its day group label
    notifyItemChanged(viewHolder.getAdapterPosition() + 1);
  }

  private void collapseExpandedCard() {
    mCurrentlyExpandedRowId = NO_EXPANDED_LIST_ITEM;
    mCurrentlyExpandedPosition = RecyclerView.NO_POSITION;
  }

  /** When the list is changing all stored position is no longer valid. */
  public void invalidatePositions() {
    mCurrentlyExpandedPosition = RecyclerView.NO_POSITION;
  }

  /** When the user clicks "undo", the hidden item is unhidden. */
  @Override
  public void onVoicemailDeleteUndo(long rowId, int adapterPosition, Uri uri) {
    mHiddenItemUris.remove(uri);
    mHiddenRowIds.remove(rowId);
    notifyItemChanged(adapterPosition);
    // The next item might have to update its day group label
    notifyItemChanged(adapterPosition + 1);
  }

  /** This callback signifies that a database deletion has completed. */
  @Override
  public void onVoicemailDeletedInDatabase(long rowId, Uri uri) {
    mHiddenItemUris.remove(uri);
  }

  /**
   * Retrieves the day group of the previous call in the call log. Used to determine if the day
   * group has changed and to trigger display of the day group text.
   *
   * @param cursor The call log cursor.
   * @return The previous day group, or DAY_GROUP_NONE if this is the first call.
   */
  private int getPreviousDayGroup(Cursor cursor) {
    // We want to restore the position in the cursor at the end.
    int startingPosition = cursor.getPosition();
    moveToPreviousNonHiddenRow(cursor);
    if (cursor.isBeforeFirst()) {
      cursor.moveToPosition(startingPosition);
      return CallLogGroupBuilder.DAY_GROUP_NONE;
    }
    int result = getDayGroupForCall(cursor.getLong(CallLogQuery.ID));
    cursor.moveToPosition(startingPosition);
    return result;
  }

  private void moveToPreviousNonHiddenRow(Cursor cursor) {
    while (cursor.moveToPrevious() && mHiddenRowIds.contains(cursor.getLong(CallLogQuery.ID))) {}
  }

  /**
   * Given a call Id, look up the day group that the call belongs to. The day group data is
   * populated in {@link com.android.dialer.app.calllog.CallLogGroupBuilder}.
   *
   * @param callId The call to retrieve the day group for.
   * @return The day group for the call.
   */
  @MainThread
  private int getDayGroupForCall(long callId) {
    Integer result = mDayGroups.get(callId);
    if (result != null) {
      return result;
    }
    return CallLogGroupBuilder.DAY_GROUP_NONE;
  }

  /**
   * Returns the call types for the given number of items in the cursor.
   *
   * <p>It uses the next {@code count} rows in the cursor to extract the types.
   *
   * <p>It position in the cursor is unchanged by this function.
   */
  private static int[] getCallTypes(Cursor cursor, int count) {
    int position = cursor.getPosition();
    int[] callTypes = new int[count];
    for (int index = 0; index < count; ++index) {
      callTypes[index] = cursor.getInt(CallLogQuery.CALL_TYPE);
      cursor.moveToNext();
    }
    cursor.moveToPosition(position);
    return callTypes;
  }

  /**
   * Determine the features which were enabled for any of the calls that make up a call log entry.
   *
   * @param cursor The cursor.
   * @param count The number of calls for the current call log entry.
   * @return The features.
   */
  private int getCallFeatures(Cursor cursor, int count) {
    int features = 0;
    int position = cursor.getPosition();
    for (int index = 0; index < count; ++index) {
      features |= cursor.getInt(CallLogQuery.FEATURES);
      cursor.moveToNext();
    }
    cursor.moveToPosition(position);
    return features;
  }

  /**
   * Sets whether processing of requests for contact details should be enabled.
   *
   * <p>This method should be called in tests to disable such processing of requests when not
   * needed.
   */
  @VisibleForTesting
  void disableRequestProcessingForTest() {
    // TODO: Remove this and test the cache directly.
    mContactInfoCache.disableRequestProcessing();
  }

  @VisibleForTesting
  void injectContactInfoForTest(String number, String countryIso, ContactInfo contactInfo) {
    // TODO: Remove this and test the cache directly.
    mContactInfoCache.injectContactInfoForTest(number, countryIso, contactInfo);
  }

  /**
   * Stores the day group associated with a call in the call log.
   *
   * @param rowId The row Id of the current call.
   * @param dayGroup The day group the call belongs in.
   */
  @Override
  @MainThread
  public void setDayGroup(long rowId, int dayGroup) {
    if (!mDayGroups.containsKey(rowId)) {
      mDayGroups.put(rowId, dayGroup);
    }
  }

  /** Clears the day group associations on re-bind of the call log. */
  @Override
  @MainThread
  public void clearDayGroups() {
    mDayGroups.clear();
  }

  /**
   * Retrieves the call Ids represented by the current call log row.
   *
   * @param cursor Call log cursor to retrieve call Ids from.
   * @param groupSize Number of calls associated with the current call log row.
   * @return Array of call Ids.
   */
  private long[] getCallIds(final Cursor cursor, final int groupSize) {
    // We want to restore the position in the cursor at the end.
    int startingPosition = cursor.getPosition();
    long[] ids = new long[groupSize];
    // Copy the ids of the rows in the group.
    for (int index = 0; index < groupSize; ++index) {
      ids[index] = cursor.getLong(CallLogQuery.ID);
      cursor.moveToNext();
    }
    cursor.moveToPosition(startingPosition);
    return ids;
  }

  /**
   * Determines the description for a day group.
   *
   * @param group The day group to retrieve the description for.
   * @return The day group description.
   */
  private CharSequence getGroupDescription(int group) {
    if (group == CallLogGroupBuilder.DAY_GROUP_TODAY) {
      return mActivity.getResources().getString(R.string.call_log_header_today);
    } else if (group == CallLogGroupBuilder.DAY_GROUP_YESTERDAY) {
      return mActivity.getResources().getString(R.string.call_log_header_yesterday);
    } else {
      return mActivity.getResources().getString(R.string.call_log_header_other);
    }
  }

  @Override
  public void onCapabilitiesUpdated() {
    notifyDataSetChanged();
  }

  @NonNull
  private EnrichedCallManager getEnrichedCallManager() {
    return EnrichedCallComponent.get(mActivity).getEnrichedCallManager();
  }

  /** Interface used to initiate a refresh of the content. */
  public interface CallFetcher {

    void fetchCalls();
  }
}
