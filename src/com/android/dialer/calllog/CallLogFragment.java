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
import android.animation.ValueAnimator;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;
import android.provider.VoicemailContract.Status;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.util.PhoneNumberHelper;
import com.android.contacts.common.util.ViewUtil;
import com.android.dialer.R;
import com.android.dialer.list.ListsFragment.HostInterface;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.util.EmptyLoader;
import com.android.dialer.voicemail.VoicemailStatusHelper;
import com.android.dialer.voicemail.VoicemailStatusHelper.StatusMessage;
import com.android.dialer.voicemail.VoicemailStatusHelperImpl;
import com.android.dialerbind.ObjectFactory;

import java.util.List;

/**
 * Displays a list of call log entries. To filter for a particular kind of call
 * (all, missed or voicemails), specify it in the constructor.
 */
public class CallLogFragment extends ListFragment
        implements CallLogQueryHandler.Listener,
        CallLogAdapter.CallFetcher,
        CallLogAdapter.CallItemExpandedListener {
    private static final String TAG = "CallLogFragment";

    /**
     * ID of the empty loader to defer other fragments.
     */
    private static final int EMPTY_LOADER_ID = 0;

    private static final String KEY_FILTER_TYPE = "filter_type";
    private static final String KEY_LOG_LIMIT = "log_limit";
    private static final String KEY_DATE_LIMIT = "date_limit";
    private static final String KEY_SHOW_FOOTER = "show_footer";

    private CallLogAdapter mAdapter;
    private CallLogQueryHandler mCallLogQueryHandler;
    private boolean mScrollToTop;

    /** Whether there is at least one voicemail source installed. */
    private boolean mVoicemailSourcesAvailable = false;

    private VoicemailStatusHelper mVoicemailStatusHelper;
    private View mStatusMessageView;
    private TextView mStatusMessageText;
    private TextView mStatusMessageAction;
    private KeyguardManager mKeyguardManager;
    private View mFooterView;

    private boolean mEmptyLoaderRunning;
    private boolean mCallLogFetched;
    private boolean mVoicemailStatusFetched;

    private float mExpandedItemTranslationZ;
    private int mFadeInDuration;
    private int mFadeInStartDelay;
    private int mFadeOutDuration;
    private int mExpandCollapseDuration;

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
    private int mLogLimit = -1;

    // Date limit (in millis since epoch) - when non-zero, only calls which occurred on or after
    // the date filter are included.  If zero, no date-based filtering occurs.
    private long mDateLimit = 0;

    // Whether or not to show the Show call history footer view
    private boolean mHasFooterView = false;

    public CallLogFragment() {
        this(CallLogQueryHandler.CALL_TYPE_ALL, -1);
    }

    public CallLogFragment(int filterType) {
        this(filterType, -1);
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
        this(filterType, -1, dateLimit);
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
            mHasFooterView = state.getBoolean(KEY_SHOW_FOOTER, mHasFooterView);
        }

        String currentCountryIso = GeoUtil.getCurrentCountryIso(getActivity());
        mAdapter = ObjectFactory.newCallLogAdapter(getActivity(), this, new ContactInfoHelper(
                getActivity(), currentCountryIso), this, true);
        setListAdapter(mAdapter);
        mCallLogQueryHandler = new CallLogQueryHandler(getActivity().getContentResolver(),
                this, mLogLimit);
        mKeyguardManager =
                (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
        getActivity().getContentResolver().registerContentObserver(CallLog.CONTENT_URI, true,
                mCallLogObserver);
        getActivity().getContentResolver().registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, mContactsObserver);
        getActivity().getContentResolver().registerContentObserver(
                Status.CONTENT_URI, true, mVoicemailStatusObserver);
        setHasOptionsMenu(true);
        updateCallList(mCallTypeFilter, mDateLimit);

        mExpandedItemTranslationZ =
                getResources().getDimension(R.dimen.call_log_expanded_translation_z);
        mFadeInDuration = getResources().getInteger(R.integer.call_log_actions_fade_in_duration);
        mFadeInStartDelay = getResources().getInteger(R.integer.call_log_actions_fade_start);
        mFadeOutDuration = getResources().getInteger(R.integer.call_log_actions_fade_out_duration);
        mExpandCollapseDuration = getResources().getInteger(
                R.integer.call_log_expand_collapse_duration);
    }

    /** Called by the CallLogQueryHandler when the list of calls has been fetched or updated. */
    @Override
    public void onCallsFetched(Cursor cursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        mAdapter.setLoading(false);
        mAdapter.changeCursor(cursor);
        // This will update the state of the "Clear call log" menu item.
        getActivity().invalidateOptionsMenu();
        if (mScrollToTop) {
            final ListView listView = getListView();
            // The smooth-scroll animation happens over a fixed time period.
            // As a result, if it scrolls through a large portion of the list,
            // each frame will jump so far from the previous one that the user
            // will not experience the illusion of downward motion.  Instead,
            // if we're not already near the top of the list, we instantly jump
            // near the top, and animate from there.
            if (listView.getFirstVisiblePosition() > 5) {
                listView.setSelection(5);
            }
            // Workaround for framework issue: the smooth-scroll doesn't
            // occur if setSelection() is called immediately before.
            mHandler.post(new Runnable() {
               @Override
               public void run() {
                   if (getActivity() == null || getActivity().isFinishing()) {
                       return;
                   }
                   listView.smoothScrollToPosition(0);
               }
            });

            mScrollToTop = false;
        }
        mCallLogFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    /**
     * Called by {@link CallLogQueryHandler} after a successful query to voicemail status provider.
     */
    @Override
    public void onVoicemailStatusFetched(Cursor statusCursor) {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }
        updateVoicemailStatusMessage(statusCursor);

        int activeSources = mVoicemailStatusHelper.getNumberActivityVoicemailSources(statusCursor);
        setVoicemailSourcesAvailable(activeSources != 0);
        MoreCloseables.closeQuietly(statusCursor);
        mVoicemailStatusFetched = true;
        destroyEmptyLoaderIfAllDataFetched();
    }

    private void destroyEmptyLoaderIfAllDataFetched() {
        if (mCallLogFetched && mVoicemailStatusFetched && mEmptyLoaderRunning) {
            mEmptyLoaderRunning = false;
            getLoaderManager().destroyLoader(EMPTY_LOADER_ID);
        }
    }

    /** Sets whether there are any voicemail sources available in the platform. */
    private void setVoicemailSourcesAvailable(boolean voicemailSourcesAvailable) {
        if (mVoicemailSourcesAvailable == voicemailSourcesAvailable) return;
        mVoicemailSourcesAvailable = voicemailSourcesAvailable;

        Activity activity = getActivity();
        if (activity != null) {
            // This is so that the options menu content is updated.
            activity.invalidateOptionsMenu();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View view = inflater.inflate(R.layout.call_log_fragment, container, false);
        mVoicemailStatusHelper = new VoicemailStatusHelperImpl();
        mStatusMessageView = view.findViewById(R.id.voicemail_status);
        mStatusMessageText = (TextView) view.findViewById(R.id.voicemail_status_message);
        mStatusMessageAction = (TextView) view.findViewById(R.id.voicemail_status_action);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getListView().setEmptyView(view.findViewById(R.id.empty_list_view));
        getListView().setItemsCanFocus(true);
        maybeAddFooterView();

        updateEmptyMessage(mCallTypeFilter);
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

    private void updateVoicemailStatusMessage(Cursor statusCursor) {
        List<StatusMessage> messages = mVoicemailStatusHelper.getStatusMessages(statusCursor);
        if (messages.size() == 0) {
            mStatusMessageView.setVisibility(View.GONE);
        } else {
            mStatusMessageView.setVisibility(View.VISIBLE);
            // TODO: Change the code to show all messages. For now just pick the first message.
            final StatusMessage message = messages.get(0);
            if (message.showInCallLog()) {
                mStatusMessageText.setText(message.callLogMessageId);
            }
            if (message.actionMessageId != -1) {
                mStatusMessageAction.setText(message.actionMessageId);
            }
            if (message.actionUri != null) {
                mStatusMessageAction.setVisibility(View.VISIBLE);
                mStatusMessageAction.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getActivity().startActivity(
                                new Intent(Intent.ACTION_VIEW, message.actionUri));
                    }
                });
            } else {
                mStatusMessageAction.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    public void onStop() {
        super.onStop();
        updateOnExit();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        mAdapter.changeCursor(null);
        getActivity().getContentResolver().unregisterContentObserver(mCallLogObserver);
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
        getActivity().getContentResolver().unregisterContentObserver(mVoicemailStatusObserver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_FILTER_TYPE, mCallTypeFilter);
        outState.putInt(KEY_LOG_LIMIT, mLogLimit);
        outState.putLong(KEY_DATE_LIMIT, mDateLimit);
        outState.putBoolean(KEY_SHOW_FOOTER, mHasFooterView);
    }

    @Override
    public void fetchCalls() {
        mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit);
    }

    public void startCallsQuery() {
        mAdapter.setLoading(true);
        mCallLogQueryHandler.fetchCalls(mCallTypeFilter, mDateLimit);
    }

    private void startVoicemailStatusQuery() {
        mCallLogQueryHandler.fetchVoicemailStatus();
    }

    private void updateCallList(int filterType, long dateLimit) {
        mCallLogQueryHandler.fetchCalls(filterType, dateLimit);
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
                getListView().getEmptyView(), R.drawable.empty_call_log, messageId, getResources());
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
                updateOnExit();
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
            startCallsQuery();
            startVoicemailStatusQuery();
            updateOnEntry();
            mRefreshDataRequired = false;
        }
    }

    /** Updates call data and notification state while leaving the call log tab. */
    private void updateOnExit() {
        updateOnTransition(false);
    }

    /** Updates call data and notification state while entering the call log tab. */
    private void updateOnEntry() {
        updateOnTransition(true);
    }

    // TODO: Move to CallLogActivity
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

    /**
     * Enables/disables the showing of the view full call history footer
     *
     * @param hasFooterView Whether or not to show the footer
     */
    public void setHasFooterView(boolean hasFooterView) {
        mHasFooterView = hasFooterView;
        maybeAddFooterView();
    }

    /**
     * Determine whether or not the footer view should be added to the listview. If getView()
     * is null, which means onCreateView hasn't been called yet, defer the addition of the footer
     * until onViewCreated has been called.
     */
    private void maybeAddFooterView() {
        if (!mHasFooterView || getView() == null) {
            return;
        }

        if (mFooterView == null) {
            mFooterView = getActivity().getLayoutInflater().inflate(
                    R.layout.recents_list_footer, (ViewGroup) getView(), false);
            mFooterView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((HostInterface) getActivity()).showCallHistory();
                }
            });
        }

        final ListView listView = getListView();
        listView.removeFooterView(mFooterView);
        listView.addFooterView(mFooterView);

        ViewUtil.addBottomPaddingToListViewForFab(listView, getResources());
    }

    @Override
    public void onItemExpanded(final CallLogListItemView view) {
        final int startingHeight = view.getHeight();
        final CallLogListItemViews viewHolder = (CallLogListItemViews) view.getTag();
        final ViewTreeObserver observer = getListView().getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // We don't want to continue getting called for every draw.
                if (observer.isAlive()) {
                    observer.removeOnPreDrawListener(this);
                }
                // Calculate some values to help with the animation.
                final int endingHeight = view.getHeight();
                final int distance = Math.abs(endingHeight - startingHeight);
                final int baseHeight = Math.min(endingHeight, startingHeight);
                final boolean isExpand = endingHeight > startingHeight;

                // Set the views back to the start state of the animation
                view.getLayoutParams().height = startingHeight;
                if (!isExpand) {
                    viewHolder.actionsView.setVisibility(View.VISIBLE);
                }

                // If the day group header is shown, subtract the header from the outline of the
                // view. The outline is used for generating the shadow of the view, but we only want
                // a shadow on the call log list item and not the header. This is a slight hack, but
                // the hierarchy of the call log list items makes it hard to achieve the desired
                // shadow behavior otherwise.
                if (viewHolder.dayGroupHeader.isShown()) {
                    Outline outline = new Outline();
                    outline.setRect(
                            0 /* left */,
                            viewHolder.dayGroupHeader.getHeight() /* top */,
                            view.getWidth() /* right */,
                            view.getHeight() /* bottom */);
                    view.setOutline(outline);
                }

                // Set up the fade effect for the action buttons.
                if (isExpand) {
                    // Start the fade in after the expansion has partly completed, otherwise it
                    // will be mostly over before the expansion completes.
                    viewHolder.actionsView.setAlpha(0f);
                    viewHolder.actionsView.animate()
                            .alpha(1f)
                            .setStartDelay(mFadeInStartDelay)
                            .setDuration(mFadeInDuration)
                            .start();
                } else {
                    viewHolder.actionsView.setAlpha(1f);
                    viewHolder.actionsView.animate()
                            .alpha(0f)
                            .setDuration(mFadeOutDuration)
                            .start();
                }
                view.requestLayout();

                // Set up the animator to animate the expansion and shadow depth.
                ValueAnimator animator = isExpand ? ValueAnimator.ofFloat(0f, 1f)
                        : ValueAnimator.ofFloat(1f, 0f);

                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animator) {
                        Float value = (Float) animator.getAnimatedValue();

                        // For each value from 0 to 1, animate the various parts of the layout.
                        view.getLayoutParams().height = (int) (value * distance + baseHeight);
                        view.setTranslationZ(mExpandedItemTranslationZ * value);
                        view.requestLayout();
                    }
                });
                // Set everything to their final values when the animation's done.
                animator.addListener(new AnimatorListener() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.getLayoutParams().height = LayoutParams.WRAP_CONTENT;

                        if (!isExpand) {
                            viewHolder.actionsView.setVisibility(View.GONE);
                        } else {
                            // This seems like it should be unnecessary, but without this, after
                            // navigating out of the activity and then back, the action view alpha
                            // is defaulting to the value (0) at the start of the expand animation.
                            viewHolder.actionsView.setAlpha(1);
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) { }
                    @Override
                    public void onAnimationRepeat(Animator animation) { }
                    @Override
                    public void onAnimationStart(Animator animation) { }
                });

                animator.setDuration(mExpandCollapseDuration);
                animator.start();

                // Return false so this draw does not occur to prevent the final frame from
                // being drawn for the single frame before the animations start.
                return false;
            }
        });
    }

    /**
     * Retrieves the call log view for the specified call Id.  If the view is not currently
     * visible, returns null.
     *
     * @param callId The call Id.
     * @return The call log view.
     */
    @Override
    public CallLogListItemView getViewForCallId(long callId) {
        ListView listView = getListView();

        int firstPosition = listView.getFirstVisiblePosition();
        int lastPosition = listView.getLastVisiblePosition();

        for (int position = 0; position <= lastPosition - firstPosition; position++) {
            View view = listView.getChildAt(position);

            if (view != null) {
                final CallLogListItemViews viewHolder = (CallLogListItemViews) view.getTag();
                if (viewHolder != null && viewHolder.rowId == callId) {
                    return (CallLogListItemView)view;
                }
            }
        }

        return null;
    }
}
