/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.list;

import android.content.Context;
import android.content.CursorLoader;

import com.android.contacts.common.list.ContactEntryListAdapter;

/**
 * Used to display an empty contact list when we don't have the permissions to read contacts.
 */
public class EmptyContactsListAdapter extends ContactEntryListAdapter {

    public EmptyContactsListAdapter(Context context) {
        super(context);
    }

    @Override
    public String getContactDisplayName(int position) {
        return null;
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        loader.setUri(null);
    }

    @Override
    public int getCount() {
        return 0;
    }
}
