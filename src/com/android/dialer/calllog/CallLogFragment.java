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

package com.android.dialer.calllog;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.VoicemailContract.Status;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.R;
import com.android.dialer.list.ListsFragment.HostInterface;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.EmptyLoader;
import com.android.dialer.voicemail.VoicemailPlaybackPresenter;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.android.dialerbind.ObjectFactory;

import java.util.List;

/**
 * Displays a list of call log entries. To filter for a particular kind of call
 * (all, missed or voicemails), specify it in the constructor.
 */
public class CallLogFragment extends Fragment
        implements CallLogQueryHandler.Listener, CallLogAdapter.CallFetcher {
    private static final String TAG = "CallLogFragment";

    /**
     * ID of the empty loader to defer other fragments.
     */
    private static final int EMPTY_LOADER_ID = 0;

    private static final String KEY_FILTER_TYPE = "filter_type";
    private static final String KEY_LOG_LIMIT = "log_limit";
    private static final String KEY_DATE_LIMIT = "date_limit";

    // No limit specified for the number of logs to show; use the CallLogQueryHandler's default.
    private static final int NO_LOG_LIMIT = -1;
    // No date-based filtering.
    private static final int NO_DATE_LIMIT = 0;

    private RecyclerView mRecyclerView;
    private LinearLayoutManager mLayoutManager;
    private CallLogAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private VoicemailPlaybackPresenter mVoicemailPlaybackPresenter;
    private boolean mScrollToTop;

    /** Whether there is at least one voicemail source installed. */
    private boolean mVoicemailSourcesAvailable = false;

    private View mEmptyListView;
    private KeyguardManager mKeyguardManager;

    private boolean mEmptyLoaderRunning;
    private boolean mCallLogFetched;
    private boolean mVoicemailStatusFetched;

    private final Handler mHandler = new Handler();

    private class CustomContentObserver extends ContentObserver {
        public CustomContentObserver() {
            super(mHandler);
        }
        @Override
        public void onChange(boolean selfChange) {
            mRefreshDataRequired = true;
        }
    }

    // See issue 6363009
    private final ContentObserver mCallLogObserver = new CustomContentObserver();
    private final ContentObserver mContactsObserver = new CustomContentObserver();
    private final ContentObserver mVoicemailStatusObserver = new CustomContentObserver();
    private boolean mRefreshDataRequired = true;

    // Exactly same variable is in Fragment as a package private.
    private boolean mMenuVisible = true;

    // Default to all calls.
    private int mCallTypeFilter = CallLogQueryHandler.CALL_TYPE_ALL;

    // Log limit - if no limit is specified, then the default in {@link CallLogQueryHandler}
    // will be used.
    private int mLogLimit = NO_LOG_LIMIT;

    // Date limit (in millis since epoch) - when non-zero, only calls which occurred on or after
    // the date filter are included.  If zero, no date-based filtering occurs.
    private long mDateLimit = NO_DATE_LIMIT;

    public CallLogFragment() {
        this(CallLogQueryHandler.CALL_TYPE_ALL, NO_LOG_LIMIT);
    }

    public CallLogFragment(int filterType) {
        this(filterType, NO_LOG_LIMIT);
    }

    public CallLogFragment(int filterType, int logLimit) {
        super();
        mCallTypeFilter = filterType;
        mLogLimit = logLimit;
    }

    /**
     * Creates a call log fragment, filtering to include only calls of the desired type, occurring
     * after the specified date.
     * @param filterType type of calls to include.
     * @param dateLimit limits results to calls occurring on or after the specified date.
     */
    public CallLogFragment(int filterType, long dateLimit) {
        this(filterType, NO_LOG_LIMIT, dateLimit);
    }

    /**
     * Creates a call log fragment, filtering to include only calls of the desired type, occurring
     * after the specified date.  Also provides a means to limit the number of results returned.
     * @param filterType type of calls to include.
     * @param logLimit limits the number of results to return.
     * @param dateLimit limits results to calls occurring on or after the specified date.
     */
    public CallLogFragment(int filterType, int logLimit, long dateLimit) {
        this(filterType, logLimit);
        mDateLimit = dateLimit;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            mCallTypeFilter = state.getInt(KEY_FILTER_TYPE, mCallTypeFilter);
            mLogLimit = state.getInt(KEY_LOG_LIMIT, mLogLimit);
            mDateLimit = state.getLong(KEY_DATE_LIMIT, mDateLimit);
        }

        final Activity activity = getActivity();
        final ContentResolver resolver = activity.getContentResolver();
        String currentCountryIso = GeoUtil.getCurrentCountryIso(activity);
        mCallLogQueryHandler = new CallLogQueryHandler(activity, resolver, this, mLogLimit);
        mKeyguardManager =
                (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        resolver.registerContentObserver(CallLog.CONTENT_URI, true, mCallLogObserver);
        resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true,
                mContactsObserver);
        resolver.registerContentObserver(Status.CONTENT_URI, true, mVoicemailStatusObserver);
        setHasOptionsMenu(true);

        mVoicemailPlaybackPresenter = new VoicemailPlaybackPresenter(activity, state);
        activity.setVolumeControlStream(VoicemailPlaybackPresenter.PLAYBACK_STREAM);
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public boolean onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            // Return false; we did not take ownership of the cursor
            return false;
        }
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);
        // This will update the state of the "Clear call log" menu item.
        getActivity().invalidateOptionsMenu();

        boolean showListView = cursor != null && cursor.getCount() > 0;
        mRecyclerView.setVisibility(showListView ? View.VISIBLE : View.GONE);
        mEmptyListView.setVisibility(!showListView ? View.VISIBLE : View.GONE);

        if (mScrollToTop) {
            // The smooth-scroll animation happens over a fixed time period.
            // As a result, if it scrolls through a large portion of the list,
            // each frame will jump so far from the previous one that the user
            // will not experience the illusion of downward motion.  Instead,
            // if we're not already near the top of the list, we instantly jump
            // near the top, and animate from there.
            if (mLayoutManager.findFirstVisibleItemPosition() > 5) {
                // TODO: Jump to near the top, then begin smooth scroll.
                mRecyclerView.smoothScrollToPosition(0);
            }
            // Workaround for framework issue: the smooth-scroll doesn't
            // occur if setSelection() is called immediately before.
            mHandler.post(new Runnable() {
               @Override
               public void run() {
                   if (getActivity() == null || getActivity().isFinishing()) {
                       return;
                   }
                   mRecyclerView.smoothScrollToPosition(0);
               }
            });

            mScrollToTop = false;
        }
        mCallLogFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
        return true;
    }

    /**
     * Called by {@link CallLogQueryHandler} after a successful query to voicemail status provider.
     */
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        mVoicemailStatusFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    private void destroyEmptyLoaderIfAllDataFetched() {
        if (mCallLogFetched && mVoicemailStatusFetched && mEmptyLoaderRunning) {
            mEmptyLoaderRunning = false;
            getLoaderManager().destroyLoader(EMPTY_LOADER_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mEmptyListView = view.findViewById(R.id.empty_list_view);

        String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        boolean isShowingRecentsTab = mLogLimit != NO_LOG_LIMIT || mDateLimit != NO_DATE_LIMIT;
        mAdapter = ObjectFactory.newCallLogAdapter(
                getActivity(),
                this,
                new ContactInfoHelper(getActivity(), currentCountryIso),
                mVoicemailPlaybackPresenter,
                isShowingRecentsTab);
        mRecyclerView.setAdapter(mAdapter);

        fetchCalls();
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        updateEmptyMessage(mCallTypeFilter);
        mAdapter.onRestoreInstanceState(savedInstanceState);
    }

    /**
     * Based on the new intent, decide whether the list should be configured
     * to scroll up to display the first item.
     */
    public void configureScreenFromIntent(Intent newIntent) {
        // Typically, when switching to the call-log we want to show the user
        // the same section of the list that they were most recently looking
        // at.  However, under some circumstances, we want to automatically
        // scroll to the top of the list to present the newest call items.
        // For example, immediately after a call is finished, we want to
        // display information about that call.
        mScrollToTop = Calls.CONTENT_TYPE.equals(newIntent.getType());
    }

    @Override
    public void onStart() {
        // Start the empty loader now to defer other fragments.  We destroy it when both calllog
        // and the voicemail status are fetched.
        getLoaderManager().initLoader(EMPTY_LOADER_ID, null,
                new EmptyLoader.Callback(getActivity()));
        mEmptyLoaderRunning = true;
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onPause() {
        mVoicemailPlaybackPresenter.onPause(getActivity().isFinishing());
        mAdapter.pauseCache();
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();

        updateOnTransition(false /* onEntry */);
    }

    @Override
    public void onDestroy() {
        mAdapter.pauseCache();
        mAdapter.changeCursor(null);
        mVoicemailPlaybackPresenter.onDestroy(getActivity().isFinishing());

        getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
        getActivity().getContentResolver().unregisterContentObserver(mVoicemailStatusObserver);
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_FILTER_TYPE, mCallTypeFilter);
        outState.putInt(KEY_LOG_LIMIT, mLogLimit);
        outState.putLong(KEY_DATE_LIMIT, mDateLimit);

        mAdapter.onSaveInstanceState(outState);
        mVoicemailPlaybackPresenter.onSaveInstanceState(outState);
    }

    @Override
    public void fetchCalls() {
        mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit);
    }

    private void updateEmptyMessage(int filterType) {
        final int messageId;
        switch (filterType) {
            case Calls.MISSED_TYPE:
                messageId = R.string.recentMissed_empty;
                break;
            case Calls.VOICEMAIL_TYPE:
                messageId = R.string.recentVoicemails_empty;
                break;
            case CallLogQueryHandler.CALL_TYPE_ALL:
                messageId = R.string.recentCalls_empty;
                break;
            default:
                throw new IllegalArgumentException("Unexpected filter type in CallLogFragment: "
                        + filterType);
        }
        DialerUtils.configureEmptyListView(
                mEmptyListView, R.drawable.empty_call_log, messageId, getResources());
    }

    CallLogAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setMenuVisibility(boolean menuVisible) {
        super.setMenuVisibility(menuVisible);
        if (mMenuVisible != menuVisible) {
            mMenuVisible = menuVisible;
            if (!menuVisible) {
                updateOnTransition(false /* onEntry */);
            } else if (isResumed()) {
                refreshData();
            }
        }
    }

    /** Requests updates to the data to be shown. */
    private void refreshData() {
        // Prevent unnecessary refresh.
        if (mRefreshDataRequired) {
            // Mark all entries in the contact info cache as out of date, so they will be looked up
            // again once being shown.
            mAdapter.invalidateCache();
            mAdapter.setLoading(true);

            fetchCalls();
            mCallLogQueryHandler.fetchVoicemailStatus();

            updateOnTransition(true /* onEntry */);
            mRefreshDataRequired = false;
        } else {
            // Refresh the display of the existing data to update the timestamp text descriptions.
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Updates the call data and notification state on entering or leaving the call log tab.
     *
     * If we are leaving the call log tab, mark all the missed calls as read.
     *
     * TODO: Move to CallLogActivity
     */
    private void updateOnTransition(boolean onEntry) {
        // We don't want to update any call data when keyguard is on because the user has likely not
        // seen the new calls yet.
        // This might be called before onCreate() and thus we need to check null explicitly.
        if (mKeyguardManager != null && !mKeyguardManager.inKeyguardRestrictedInputMode()) {
            // On either of the transitions we update the missed call and voicemail notifications.
            // While exiting we additionally consume all missed calls (by marking them as read).
            mCallLogQueryHandler.markNewCallsAsOld();
            if (!onEntry) {
                mCallLogQueryHandler.markMissedCallsAsRead();
            }
            CallLogNotificationsHelper.removeMissedCallNotifications(getActivity());
            CallLogNotificationsHelper.updateVoicemailNotifications(getActivity());
        }
    }
}
