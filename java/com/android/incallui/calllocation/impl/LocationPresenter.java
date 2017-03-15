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

package com.android.incallui.calllocation.impl;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.AsyncTask;
import com.android.dialer.common.LogUtil;
import com.android.incallui.baseui.Presenter;
import com.android.incallui.baseui.Ui;
import com.google.android.gms.location.LocationListener;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * Presenter for the {@code LocationFragment}.
 *
 * <p>Performs lookup for the address and map image to show.
 */
public class LocationPresenter extends Presenter<LocationPresenter.LocationUi>
    implements LocationListener {

  private Location mLastLocation;
  private AsyncTask mDownloadMapTask;
  private AsyncTask mReverseGeocodeTask;

  LocationPresenter() {}

  @Override
  public void onUiReady(LocationUi ui) {
    LogUtil.i("LocationPresenter.onUiReady", "");
    super.onUiReady(ui);
    updateLocation(mLastLocation, true);
  }

  @Override
  public void onUiUnready(LocationUi ui) {
    LogUtil.i("LocationPresenter.onUiUnready", "");
    super.onUiUnready(ui);

    if (mDownloadMapTask != null) {
      mDownloadMapTask.cancel(true);
    }
    if (mReverseGeocodeTask != null) {
      mReverseGeocodeTask.cancel(true);
    }
  }

  @Override
  public void onLocationChanged(Location location) {
    LogUtil.i("LocationPresenter.onLocationChanged", "");
    updateLocation(location, false);
  }

  private void updateLocation(Location location, boolean forceUpdate) {
    LogUtil.i("LocationPresenter.updateLocation", "location: " + location);
    if (forceUpdate || !Objects.equals(mLastLocation, location)) {
      mLastLocation = location;
      if (LocationHelper.isValidLocation(location)) {
        LocationUi ui = getUi();
        mDownloadMapTask = new DownloadMapImageTask(new WeakReference<>(ui)).execute(location);
        mReverseGeocodeTask = new ReverseGeocodeTask(new WeakReference<>(ui)).execute(location);
        if (ui != null) {
          ui.setLocation(location);
        } else {
          LogUtil.i("LocationPresenter.updateLocation", "no Ui");
        }
      }
    }
  }

  /** UI interface */
  public interface LocationUi extends Ui {

    void setAddress(String address);

    void setMap(Drawable mapImage);

    void setLocation(Location location);

    Context getContext();
  }
}
