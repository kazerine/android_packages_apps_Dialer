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

package com.android.dialer.calllog.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.android.dialer.calllog.database.contract.AnnotatedCallLogContract.AnnotatedCallLog;
import com.android.dialer.common.LogUtil;

/** {@link SQLiteOpenHelper} for the AnnotatedCallLog database. */
class AnnotatedCallLogDatabaseHelper extends SQLiteOpenHelper {

  AnnotatedCallLogDatabaseHelper(Context appContext) {
    super(appContext, "annotated_call_log.db", null, 1);
  }

  private static final String CREATE_SQL =
      new StringBuilder()
          .append("create table if not exists " + AnnotatedCallLog.TABLE + " (")
          .append(AnnotatedCallLog._ID + " integer primary key, ")
          .append(AnnotatedCallLog.TIMESTAMP + " integer, ")
          .append(AnnotatedCallLog.CONTACT_NAME + " string")
          .append(");")
          .toString();

  @Override
  public void onCreate(SQLiteDatabase db) {
    LogUtil.enterBlock("AnnotatedCallLogDatabaseHelper.onCreate");
    long startTime = System.currentTimeMillis();
    db.execSQL(CREATE_SQL);
    // TODO: Consider logging impression.
    LogUtil.i(
        "AnnotatedCallLogDatabaseHelper.onCreate",
        "took: %dms",
        System.currentTimeMillis() - startTime);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
}
