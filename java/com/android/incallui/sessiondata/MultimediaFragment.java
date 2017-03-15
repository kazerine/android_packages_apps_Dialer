/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.incallui.sessiondata;

import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.dialer.common.FragmentUtils;
import com.android.dialer.common.LogUtil;
import com.android.dialer.multimedia.MultimediaData;
import com.android.incallui.maps.MapsComponent;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

/**
 * Displays info from {@link MultimediaData MultimediaData}.
 *
 * <p>Currently displays image, location (as a map), and message that come bundled with
 * MultimediaData when calling {@link #newInstance(MultimediaData, boolean, boolean)}.
 */
public class MultimediaFragment extends Fragment implements AvatarPresenter {

  private static final String ARG_SUBJECT = "subject";
  private static final String ARG_IMAGE = "image";
  private static final String ARG_LOCATION = "location";
  private static final String ARG_INTERACTIVE = "interactive";
  private static final String ARG_SHOW_AVATAR = "show_avatar";
  private ImageView avatarImageView;

  private boolean showAvatar;

  public static MultimediaFragment newInstance(
      @NonNull MultimediaData multimediaData, boolean isInteractive, boolean showAvatar) {
    return newInstance(
        multimediaData.getText(),
        multimediaData.getImageUri(),
        multimediaData.getLocation(),
        isInteractive,
        showAvatar);
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  public static MultimediaFragment newInstance(
      @Nullable String subject,
      @Nullable Uri imageUri,
      @Nullable Location location,
      boolean isInteractive,
      boolean showAvatar) {
    Bundle args = new Bundle();
    args.putString(ARG_SUBJECT, subject);
    args.putParcelable(ARG_IMAGE, imageUri);
    args.putParcelable(ARG_LOCATION, location);
    args.putBoolean(ARG_INTERACTIVE, isInteractive);
    args.putBoolean(ARG_SHOW_AVATAR, showAvatar);
    MultimediaFragment fragment = new MultimediaFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  public void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    showAvatar = getArguments().getBoolean(ARG_SHOW_AVATAR);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater layoutInflater, @Nullable ViewGroup viewGroup, @Nullable Bundle bundle) {
    boolean hasImage = getImageUri() != null;
    boolean hasSubject = !TextUtils.isEmpty(getSubject());
    boolean hasMap = getLocation() != null;
    if (hasMap && MapsComponent.get(getContext()).getMaps().isAvailable()) {
      if (hasImage) {
        if (hasSubject) {
          return layoutInflater.inflate(
              R.layout.fragment_composer_text_image_frag, viewGroup, false);
        } else {
          return layoutInflater.inflate(R.layout.fragment_composer_image_frag, viewGroup, false);
        }
      } else if (hasSubject) {
        return layoutInflater.inflate(R.layout.fragment_composer_text_frag, viewGroup, false);
      } else {
        return layoutInflater.inflate(R.layout.fragment_composer_frag, viewGroup, false);
      }
    } else if (hasImage) {
      if (hasSubject) {
        return layoutInflater.inflate(R.layout.fragment_composer_text_image, viewGroup, false);
      } else {
        return layoutInflater.inflate(R.layout.fragment_composer_image, viewGroup, false);
      }
    } else {
      return layoutInflater.inflate(R.layout.fragment_composer_text, viewGroup, false);
    }
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle bundle) {
    super.onViewCreated(view, bundle);
    TextView messageText = (TextView) view.findViewById(R.id.answer_message_text);
    if (messageText != null) {
      messageText.setText(getSubject());
    }
    ImageView mainImage = (ImageView) view.findViewById(R.id.answer_message_image);
    if (mainImage != null) {
      Glide.with(this)
          .load(getImageUri())
          .transition(DrawableTransitionOptions.withCrossFade())
          .listener(
              new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(
                    @Nullable GlideException e,
                    Object model,
                    Target<Drawable> target,
                    boolean isFirstResource) {
                  view.findViewById(R.id.loading_spinner).setVisibility(View.GONE);
                  LogUtil.e("MultimediaFragment.onLoadFailed", null, e);
                  // TODO(b/34720074) handle error cases nicely
                  return false; // Let Glide handle the rest
                }

                @Override
                public boolean onResourceReady(
                    Drawable drawable,
                    Object model,
                    Target<Drawable> target,
                    DataSource dataSource,
                    boolean isFirstResource) {
                  view.findViewById(R.id.loading_spinner).setVisibility(View.GONE);
                  return false;
                }
              })
          .into(mainImage);
      mainImage.setClipToOutline(true);
    }
    FrameLayout fragmentHolder = (FrameLayout) view.findViewById(R.id.answer_message_frag);
    if (fragmentHolder != null) {
      fragmentHolder.setClipToOutline(true);
      Fragment mapFragment =
          MapsComponent.get(getContext()).getMaps().createStaticMapFragment(getLocation());
      getChildFragmentManager()
          .beginTransaction()
          .replace(R.id.answer_message_frag, mapFragment)
          .commitNow();
    }
    avatarImageView = ((ImageView) view.findViewById(R.id.answer_message_avatar));
    avatarImageView.setVisibility(showAvatar ? View.VISIBLE : View.GONE);

    Holder parent = FragmentUtils.getParent(this, Holder.class);
    if (parent != null) {
      parent.updateAvatar(this);
    }
  }

  @Nullable
  @Override
  public ImageView getAvatarImageView() {
    return avatarImageView;
  }

  @Override
  public int getAvatarSize() {
    return getResources().getDimensionPixelSize(R.dimen.answer_message_avatar_size);
  }

  @Override
  public boolean shouldShowAnonymousAvatar() {
    return showAvatar;
  }

  @Nullable
  public String getSubject() {
    return getArguments().getString(ARG_SUBJECT);
  }

  @Nullable
  public Uri getImageUri() {
    return getArguments().getParcelable(ARG_IMAGE);
  }

  @Nullable
  public Location getLocation() {
    return getArguments().getParcelable(ARG_LOCATION);
  }

  /** Interface for notifying the fragment parent of changes. */
  public interface Holder {
    void updateAvatar(AvatarPresenter sessionDataScreen);
  }
}
