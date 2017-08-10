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

import android.app.Fragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v13.app.FragmentCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import com.android.contacts.common.extensions.PhoneDirectoryExtenderAccessor;
import com.android.dialer.animation.AnimUtils;
import com.android.dialer.common.Assert;
import com.android.dialer.common.LogUtil;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.dialer.searchfragment.common.SearchCursor;
import com.android.dialer.searchfragment.cp2.SearchContactsCursorLoader;
import com.android.dialer.searchfragment.nearbyplaces.NearbyPlacesCursorLoader;
import com.android.dialer.searchfragment.remote.RemoteContactsCursorLoader;
import com.android.dialer.searchfragment.remote.RemoteDirectoriesCursorLoader;
import com.android.dialer.searchfragment.remote.RemoteDirectoriesCursorLoader.Directory;
import com.android.dialer.util.PermissionsUtil;
import com.android.dialer.util.ViewUtil;
import com.android.dialer.widget.EmptyContentView;
import com.android.dialer.widget.EmptyContentView.OnEmptyViewActionButtonClickedListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fragment used for searching contacts. */
public final class NewSearchFragment extends Fragment
    implements LoaderCallbacks<Cursor>, OnEmptyViewActionButtonClickedListener {

  // Since some of our queries can generate network requests, we should delay them until the user
  // stops typing to prevent generating too much network traffic.
  private static final int NETWORK_SEARCH_DELAY_MILLIS = 300;

  @VisibleForTesting public static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;

  private static final int CONTACTS_LOADER_ID = 0;
  private static final int NEARBY_PLACES_LOADER_ID = 1;
  private static final int REMOTE_DIRECTORIES_LOADER_ID = 2;
  private static final int REMOTE_CONTACTS_LOADER_ID = 3;

  private EmptyContentView emptyContentView;
  private RecyclerView recyclerView;
  private SearchAdapter adapter;
  private String query;
  private boolean remoteDirectoriesDisabledForTesting;

  private final List<Directory> directories = new ArrayList<>();
  private final Runnable loadNearbyPlacesRunnable =
      () -> getLoaderManager().restartLoader(NEARBY_PLACES_LOADER_ID, null, this);
  private final Runnable loadRemoteContactsRunnable =
      () -> getLoaderManager().restartLoader(REMOTE_CONTACTS_LOADER_ID, null, this);

  private Runnable updatePositionRunnable;

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle bundle) {
    View view = inflater.inflate(R.layout.fragment_search, parent, false);
    adapter = new SearchAdapter(getContext(), new SearchCursorManager());
    emptyContentView = view.findViewById(R.id.empty_view);
    recyclerView = view.findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    recyclerView.setAdapter(adapter);

    if (!PermissionsUtil.hasContactsReadPermissions(getContext())) {
      emptyContentView.setDescription(R.string.new_permission_no_search);
      emptyContentView.setActionLabel(R.string.permission_single_turn_on);
      emptyContentView.setActionClickedListener(this);
      emptyContentView.setImage(R.drawable.empty_contacts);
      emptyContentView.setVisibility(View.VISIBLE);
    } else {
      initLoaders();
    }

    if (updatePositionRunnable != null) {
      ViewUtil.doOnPreDraw(view, false, updatePositionRunnable);
    }
    return view;
  }

  private void initLoaders() {
    getLoaderManager().initLoader(CONTACTS_LOADER_ID, null, this);
    loadNearbyPlacesCursor();
    loadRemoteDirectoriesCursor();
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
    // TODO(calderwoodra) add enterprise loader
    if (id == CONTACTS_LOADER_ID) {
      return new SearchContactsCursorLoader(getContext());
    } else if (id == NEARBY_PLACES_LOADER_ID) {
      return new NearbyPlacesCursorLoader(getContext(), query);
    } else if (id == REMOTE_DIRECTORIES_LOADER_ID) {
      return new RemoteDirectoriesCursorLoader(getContext());
    } else if (id == REMOTE_CONTACTS_LOADER_ID) {
      return new RemoteContactsCursorLoader(getContext(), query, directories);
    } else {
      throw new IllegalStateException("Invalid loader id: " + id);
    }
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    if (cursor != null
        && !(loader instanceof RemoteDirectoriesCursorLoader)
        && !(cursor instanceof SearchCursor)) {
      throw Assert.createIllegalStateFailException("Cursors must implement SearchCursor");
    }

    if (loader instanceof SearchContactsCursorLoader) {
      adapter.setContactsCursor((SearchCursor) cursor);

    } else if (loader instanceof NearbyPlacesCursorLoader) {
      adapter.setNearbyPlacesCursor((SearchCursor) cursor);

    } else if (loader instanceof RemoteContactsCursorLoader) {
      adapter.setRemoteContactsCursor((SearchCursor) cursor);

    } else if (loader instanceof RemoteDirectoriesCursorLoader) {
      directories.clear();
      cursor.moveToPosition(-1);
      while (cursor.moveToNext()) {
        directories.add(RemoteDirectoriesCursorLoader.readDirectory(cursor));
      }
      loadRemoteContactsCursors();

    } else {
      throw new IllegalStateException("Invalid loader: " + loader);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    adapter.clear();
    recyclerView.setAdapter(null);
  }

  public void setQuery(String query) {
    this.query = query;
    if (adapter != null) {
      adapter.setQuery(query);
      loadNearbyPlacesCursor();
      loadRemoteContactsCursors();
    }
  }

  public void animatePosition(int start, int end, int duration) {
    // Called before the view is ready, prepare a runnable to run in onCreateView
    if (getView() == null) {
      updatePositionRunnable = () -> animatePosition(start, end, 0);
      return;
    }
    boolean slideUp = start > end;
    Interpolator interpolator = slideUp ? AnimUtils.EASE_IN : AnimUtils.EASE_OUT;
    getView().setTranslationY(start);
    getView().animate().translationY(end).setInterpolator(interpolator).setDuration(duration);
    updatePositionRunnable = null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadNearbyPlacesRunnable);
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadRemoteContactsRunnable);
  }

  private void loadNearbyPlacesCursor() {
    // Cancel existing load if one exists.
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadNearbyPlacesRunnable);

    // If nearby places is not enabled, do not try to load them.
    if (!PhoneDirectoryExtenderAccessor.get(getContext()).isEnabled(getContext())) {
      return;
    }
    ThreadUtil.getUiThreadHandler()
        .postDelayed(loadNearbyPlacesRunnable, NETWORK_SEARCH_DELAY_MILLIS);
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
      if (grantResults.length >= 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
        // Force a refresh of the data since we were missing the permission before this.
        emptyContentView.setVisibility(View.GONE);
        initLoaders();
      }
    }
  }

  @Override
  public void onEmptyViewActionButtonClicked() {
    String[] deniedPermissions =
        PermissionsUtil.getPermissionsCurrentlyDenied(
            getContext(), PermissionsUtil.allContactsGroupPermissionsUsedInDialer);
    if (deniedPermissions.length > 0) {
      LogUtil.i(
          "NewSearchFragment.onEmptyViewActionButtonClicked",
          "Requesting permissions: " + Arrays.toString(deniedPermissions));
      FragmentCompat.requestPermissions(
          this, deniedPermissions, READ_CONTACTS_PERMISSION_REQUEST_CODE);
    }
  }

  private void loadRemoteDirectoriesCursor() {
    if (!remoteDirectoriesDisabledForTesting) {
      getLoaderManager().initLoader(REMOTE_DIRECTORIES_LOADER_ID, null, this);
    }
  }

  private void loadRemoteContactsCursors() {
    if (remoteDirectoriesDisabledForTesting) {
      return;
    }

    // Cancel existing load if one exists.
    ThreadUtil.getUiThreadHandler().removeCallbacks(loadRemoteContactsRunnable);
    ThreadUtil.getUiThreadHandler()
        .postDelayed(loadRemoteContactsRunnable, NETWORK_SEARCH_DELAY_MILLIS);
  }

  // Currently, setting up multiple FakeContentProviders doesn't work and results in this fragment
  // being untestable while it can query multiple datasources. This is a temporary fix.
  // TODO(b/64099602): Remove this method and test this fragment with multiple data sources
  @VisibleForTesting
  public void setRemoteDirectoriesDisabled(boolean disabled) {
    remoteDirectoriesDisabledForTesting = disabled;
  }
}
