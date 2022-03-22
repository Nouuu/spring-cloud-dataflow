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

import java.util.List;
import java.util.Map;

import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.dataflow.server.service.TaskExecutionService;
import org.springframework.scheduling.quartz.QuartzJobBean;

public class QuartsSchedulerJob extends QuartzJobBean {

    private static final Logger logger = LoggerFactory.getLogger(QuartsSchedulerJob.class);

    private TaskExecutionService taskService;
    private String scheduleName;

    private Map<String, String> taskDeploymentProperties;

    private List<String> commandLineArgs;

    @Autowired
    public void setTaskService(TaskExecutionService taskService) {
        this.taskService = taskService;
    }

    public void setScheduleName(String scheduleName) {
        this.scheduleName = scheduleName;
    }

    public void setTaskDeploymentProperties(Map<String, String> taskDeploymentProperties) {
        this.taskDeploymentProperties = taskDeploymentProperties;
    }

    public void setCommandLineArgs(List<String> commandLineArgs) {
        this.commandLineArgs = commandLineArgs;
    }

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        logger.debug("launching scheduled quartz job {}", scheduleName);
        taskService.executeTask(scheduleName, taskDeploymentProperties, commandLineArgs);
    }
}
