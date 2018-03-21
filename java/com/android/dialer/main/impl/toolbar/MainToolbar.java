/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.dialer.main.impl.toolbar;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import com.android.dialer.common.Assert;
import com.android.dialer.util.ViewUtil;
import com.google.common.base.Optional;

/** Toolbar for {@link com.android.dialer.main.impl.MainActivity}. */
public final class MainToolbar extends Toolbar implements PopupMenu.OnMenuItemClickListener {

  private static final int SLIDE_DURATION = 300;
  private static final AccelerateDecelerateInterpolator SLIDE_INTERPOLATOR =
      new AccelerateDecelerateInterpolator();

  private SearchBarView searchBar;
  private SearchBarListener listener;
  private MainToolbarMenu overflowMenu;
  private boolean isSlideUp;

  public MainToolbar(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    ImageButton optionsMenuButton = findViewById(R.id.main_options_menu_button);
    overflowMenu = new MainToolbarMenu(getContext(), optionsMenuButton);
    overflowMenu.inflate(R.menu.main_menu);
    overflowMenu.setOnMenuItemClickListener(this);
    optionsMenuButton.setOnClickListener(v -> overflowMenu.show());
    optionsMenuButton.setOnTouchListener(overflowMenu.getDragToOpenListener());

    searchBar = findViewById(R.id.search_view_container);
  }

  @Override
  public boolean onMenuItemClick(MenuItem menuItem) {
    return listener.onMenuItemClicked(menuItem);
  }

  public void setSearchBarListener(@NonNull SearchBarListener listener) {
    this.listener = Assert.isNotNull(listener);
    searchBar.setSearchBarListener(listener);
  }

  /** Slides the toolbar up and off the screen. */
  public void slideUp(boolean animate, View container) {
    Assert.checkArgument(!isSlideUp);
    if (getHeight() == 0) {
      ViewUtil.doOnGlobalLayout(this, view -> slideUp(animate, container));
      return;
    }
    isSlideUp = true;
    animate()
        .translationY(-getHeight())
        .setDuration(animate ? SLIDE_DURATION : 0)
        .setInterpolator(SLIDE_INTERPOLATOR)
        .start();
    container
        .animate()
        .translationY(-getHeight())
        .setDuration(animate ? SLIDE_DURATION : 0)
        .setInterpolator(SLIDE_INTERPOLATOR)
        .start();
  }

  /** Slides the toolbar down and back onto the screen. */
  public void slideDown(boolean animate, View container) {
    Assert.checkArgument(isSlideUp);
    isSlideUp = false;
    animate()
        .translationY(0)
        .setDuration(animate ? SLIDE_DURATION : 0)
        .setInterpolator(SLIDE_INTERPOLATOR)
        .start();
    container
        .animate()
        .translationY(0)
        .setDuration(animate ? SLIDE_DURATION : 0)
        .setInterpolator(SLIDE_INTERPOLATOR)
        .start();
  }

  /** @see SearchBarView#collapse(boolean) */
  public void collapse(boolean animate) {
    searchBar.collapse(animate);
  }

  /** @see SearchBarView#collapse(boolean) */
  public void expand(boolean animate, Optional<String> text) {
    searchBar.expand(animate, text);
  }

  public boolean isSlideUp() {
    return isSlideUp;
  }

  public boolean isExpanded() {
    return searchBar.isExpanded();
  }

  public String getQuery() {
    return searchBar.getQuery();
  }

  public void transferQueryFromDialpad(String query) {
    searchBar.setQueryWithoutUpdate(query);
  }

  public void hideKeyboard() {
    searchBar.hideKeyboard();
  }

  public void showKeyboard() {
    searchBar.showKeyboard();
  }

  public MainToolbarMenu getOverflowMenu() {
    return overflowMenu;
  }

  public void setHint(@StringRes int hint) {
    searchBar.setHint(hint);
  }

  public void showClearFrequents(boolean show) {
    overflowMenu.showClearFrequents(show);
  }

  public void maybeShowSimulator(AppCompatActivity appCompatActivity) {
    overflowMenu.maybeShowSimulator(appCompatActivity);
  }
}
