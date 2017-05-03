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

package com.android.voicemail.impl.scheduling;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.support.annotation.MainThread;
import com.android.dialer.constants.ScheduledJobIds;
import com.android.voicemail.impl.Assert;
import com.android.voicemail.impl.VvmLog;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link JobService} that will trigger the background execution of {@link TaskSchedulerService}.
 */
@TargetApi(VERSION_CODES.O)
public class TaskSchedulerJobService extends JobService implements TaskSchedulerService.Job {

  private static final String TAG = "TaskSchedulerJobService";

  private static final String EXTRA_TASK_EXTRAS_ARRAY = "extra_task_extras_array";

  private JobParameters jobParameters;
  private TaskSchedulerService scheduler;

  private final ServiceConnection mConnection =
      new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
          VvmLog.i(TAG, "TaskSchedulerService connected");
          scheduler = ((TaskSchedulerService.LocalBinder) binder).getService();
          scheduler.onStartJob(
              TaskSchedulerJobService.this,
              getBundleList(
                  jobParameters.getTransientExtras().getParcelableArray(EXTRA_TASK_EXTRAS_ARRAY)));
        }

        @Override
        public void onServiceDisconnected(ComponentName unused) {
          // local service, process should always be killed together.
          Assert.fail();
        }
      };

  @Override
  @MainThread
  public boolean onStartJob(JobParameters params) {
    jobParameters = params;
    bindService(
        new Intent(this, TaskSchedulerService.class), mConnection, Context.BIND_AUTO_CREATE);
    return true /* job still running in background */;
  }

  @Override
  @MainThread
  public boolean onStopJob(JobParameters params) {
    scheduler.onStopJob();
    jobParameters = null;
    return false /* don't reschedule. TaskScheduler service will post a new job */;
  }

  /**
   * Schedule a job to run the {@code pendingTasks}. If a job is already scheduled it will be
   * appended to the back of the queue and the job will be rescheduled.
   *
   * @param delayMillis delay before running the job. Must be 0 if{@code isNewJob} is true.
   * @param isNewJob a new job will be forced to run immediately.
   */
  @MainThread
  public static void scheduleJob(
      Context context, List<Bundle> pendingTasks, long delayMillis, boolean isNewJob) {
    Assert.isMainThread();
    JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
    JobInfo pendingJob = jobScheduler.getPendingJob(ScheduledJobIds.VVM_TASK_SCHEDULER_JOB);
    VvmLog.i(TAG, "scheduling job with " + pendingTasks.size() + " tasks");
    if (pendingJob != null) {
      if (isNewJob) {
        List<Bundle> existingTasks =
            getBundleList(
                pendingJob.getTransientExtras().getParcelableArray(EXTRA_TASK_EXTRAS_ARRAY));
        VvmLog.i(TAG, "merging job with " + existingTasks.size() + " existing tasks");
        TaskQueue queue = new TaskQueue();
        queue.fromBundles(context, existingTasks);
        for (Bundle pendingTask : pendingTasks) {
          queue.add(Tasks.createTask(context, pendingTask));
        }
        pendingTasks = queue.toBundles();
      }
      VvmLog.i(TAG, "canceling existing job.");
      jobScheduler.cancel(ScheduledJobIds.VVM_TASK_SCHEDULER_JOB);
    }
    Bundle extras = new Bundle();
    extras.putParcelableArray(
        EXTRA_TASK_EXTRAS_ARRAY, pendingTasks.toArray(new Bundle[pendingTasks.size()]));
    JobInfo.Builder builder =
        new JobInfo.Builder(
                ScheduledJobIds.VVM_TASK_SCHEDULER_JOB,
                new ComponentName(context, TaskSchedulerJobService.class))
            .setTransientExtras(extras)
            .setMinimumLatency(delayMillis)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
    if (isNewJob) {
      Assert.isTrue(delayMillis == 0);
      builder.setOverrideDeadline(0);
      VvmLog.i(TAG, "running job instantly.");
    }
    jobScheduler.schedule(builder.build());
    VvmLog.i(TAG, "job scheduled");
  }

  /**
   * The system will hold a wakelock when {@link #onStartJob(JobParameters)} is called to ensure the
   * device will not sleep when the job is still running. Finish the job so the system will release
   * the wakelock
   */
  @Override
  public void finish() {
    VvmLog.i(TAG, "finishing job and unbinding TaskSchedulerService");
    jobFinished(jobParameters, false);
    jobParameters = null;
    unbindService(mConnection);
  }

  private static List<Bundle> getBundleList(Parcelable[] parcelables) {
    List<Bundle> result = new ArrayList<>(parcelables.length);
    for (Parcelable parcelable : parcelables) {
      result.add((Bundle) parcelable);
    }
    return result;
  }
}
