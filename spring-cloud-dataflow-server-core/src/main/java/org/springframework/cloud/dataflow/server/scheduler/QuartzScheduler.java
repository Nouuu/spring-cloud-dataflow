/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.dataflow.server.scheduler;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;

import org.springframework.cloud.deployer.spi.scheduler.CreateScheduleException;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleInfo;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleRequest;
import org.springframework.cloud.deployer.spi.scheduler.Scheduler;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerException;
import org.springframework.cloud.deployer.spi.scheduler.SchedulerPropertyKeys;
import org.springframework.cloud.deployer.spi.scheduler.UnScheduleException;
import org.springframework.util.Assert;


/**
 * A Quartz Scheduler implementation of the {@link Scheduler} SPI.
 *
 * @author Manokethan Parameswaran & Noé Larrieu-Lacoste
 */
public class QuartzScheduler implements Scheduler {

    private static final String JOB_DATA_TASK_NAME_KEY = "scheduleName";

    private static final String JOB_DATA_TASK_DEPLOYMENT_PROPERTIES_KEY = "taskDeploymentProperties";

    private static final String JOB_DATA_TASK_COMMAND_LINE_ARGS_KEY = "commandLineArgs";

    private static final String CRON_EXPRESSION = "scheduler.cron.expression";

    private static final Log logger = LogFactory.getLog(QuartzScheduler.class);

    private final org.quartz.Scheduler scheduler;

    public QuartzScheduler(
            org.quartz.Scheduler scheduler) {
        Assert.notNull(scheduler, "scheduler must not be null");

        this.scheduler = scheduler;
    }

    @Override
    public void schedule(ScheduleRequest scheduleRequest) {
        String appName = scheduleRequest.getDefinition().getName();
        String scheduleName = scheduleRequest.getScheduleName();
        logger.debug(String.format("Scheduling: %s", scheduleName));

         // String cronExpression = scheduleRequest.getSchedulerProperties().get(SchedulerPropertyKeys.CRON_EXPRESSION);
        String cronExpression = scheduleRequest.getDeploymentProperties().get(CRON_EXPRESSION);
        Assert.hasText(
                cronExpression,
                String.format(
                        "request's scheduleProperties must have a %s that is not null nor empty",CRON_EXPRESSION));
        try {
            new CronExpression(cronExpression);
        } catch (ParseException pe) {
            throw new IllegalArgumentException("Cron Expression is invalid: " + pe.getMessage());
        }

        scheduleTask(appName, scheduleName, cronExpression, scheduleRequest.getDeploymentProperties(),
                scheduleRequest.getCommandlineArguments());
    }

    @Override
    public void unschedule(String scheduleName) {
        logger.debug("Unscheduling: " + scheduleName);
        try {
            boolean unscheduled = scheduler.deleteJob(getJobKey(scheduleName));
            if (!unscheduled) {
                throw new UnScheduleException(
                        String.format("Failed to unschedule schedule %s may not exist.", scheduleName));
            }
        } catch (org.quartz.SchedulerException e) {
            throw new UnScheduleException(scheduleName, e);
        }
    }

    @Override
    public List<ScheduleInfo> list(String taskDefinitionName) throws SchedulerException {
        List<ScheduleInfo> result = new ArrayList<>();
        try {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(taskDefinitionName))) {

                String jobName = jobKey.getName();

                ScheduleInfo scheduleInfo = new ScheduleInfo();
                scheduleInfo.setScheduleProperties(new HashMap<>());
                scheduleInfo.setScheduleName(jobName);
                scheduleInfo.setTaskDefinitionName(taskDefinitionName);
                List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
                if (triggers != null && !triggers.isEmpty()) {
                    CronTrigger cronTrigger = (CronTrigger) triggers.get(0);
                    scheduleInfo
                            .getScheduleProperties()
                            .put(SchedulerPropertyKeys.CRON_EXPRESSION, cronTrigger.getCronExpression());
                    result.add(scheduleInfo);
                } else {
                    logger.warn(String.format("Job %s does not have an associated schedule", jobName));
                }
            }
        } catch (org.quartz.SchedulerException e) {
            throw new SchedulerException(
                    "An error occurred while generating schedules list for the task " + taskDefinitionName,
                    e);
        }
        return result;
    }

    @Override
    public List<ScheduleInfo> list() throws SchedulerException {
        List<ScheduleInfo> result = new ArrayList<>();
        try {
            for (String groupName : scheduler.getJobGroupNames()) {
                result.addAll(list(groupName));
            }
        } catch (org.quartz.SchedulerException e) {
            throw new SchedulerException("An error occurred while generating schedules list", e);
        }
        return result;
    }

    /**
     * Schedules the Quartz job for the application.
     *
     * @param appName                  The name of the task app to be scheduled.
     * @param scheduleName             the name of the schedule.
     * @param expression               the cron expression.
     * @param taskDeploymentProperties optional task properties before launching the task.
     * @param commandLineArgs          optional task arguments before launching the task.
     */
    private void scheduleTask(
            String appName, String scheduleName, String expression,
            Map<String, String> taskDeploymentProperties, List<String> commandLineArgs) {
        logger.debug(("Scheduling Task: " + appName));
        JobDetail jobDetail = JobBuilder.newJob()
                .ofType(QuartsSchedulerJob.class)
                .storeDurably()
                .withIdentity(scheduleName, appName)
                .build();

        jobDetail.getJobDataMap().put(JOB_DATA_TASK_NAME_KEY, appName);
        jobDetail.getJobDataMap().put(JOB_DATA_TASK_DEPLOYMENT_PROPERTIES_KEY, taskDeploymentProperties);
        jobDetail.getJobDataMap().put(JOB_DATA_TASK_COMMAND_LINE_ARGS_KEY, commandLineArgs);

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(scheduleName, appName)
                .withSchedule(CronScheduleBuilder.cronSchedule(expression))
                .build();

        try {
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (org.quartz.SchedulerException e) {
            throw new CreateScheduleException(scheduleName, e);
        }
    }

    /**
     * Retrieve the job key for the specified Schedule Name.
     *
     * @param scheduleName the name of the schedule to search.
     * @return The job associated with the schedule.
     */
    private JobKey getJobKey(String scheduleName) throws SchedulerException {
        try {
            for (String groupName : scheduler.getJobGroupNames()) {
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                    if (jobKey.getName().equals(scheduleName)) {
                        return jobKey;
                    }
                }
            }
        } catch (org.quartz.SchedulerException e) {
            throw new SchedulerException(
                    "An error occurred while search for schedule " + scheduleName, e);
        }
        throw new SchedulerException(String.format("schedule %s does not exist.", scheduleName));
    }
}
