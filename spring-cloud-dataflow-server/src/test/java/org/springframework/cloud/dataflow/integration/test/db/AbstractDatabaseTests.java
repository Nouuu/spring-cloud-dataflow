/*
 * Copyright 2021-2022 the original author or authors.
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

package org.springframework.cloud.dataflow.integration.test.db;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.integration.test.tags.Database;
import org.springframework.cloud.dataflow.integration.test.tags.DataflowMain;
import org.springframework.cloud.dataflow.integration.test.tags.TagNames;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Base test class for a spesific database and defines actual tests we should
 * have for all databases.
 *
 * @author Janne Valkealahti
 */
@Database
public abstract class AbstractDatabaseTests extends AbstractDataflowTests {

	private final Logger log = LoggerFactory.getLogger(AbstractDatabaseTests.class);
	protected abstract String getDatabaseTag();

	/**
	 * Simply tests a bootstrap of servers with latest versions using a shared
	 * database.
	 */
	@Test
	@DataflowMain
	public void testLatestSharedDb() {
		log.info("Running testLatestSharedDb()");
		// start defined database
		this.dataflowCluster.startSkipperDatabase(getDatabaseTag());
		this.dataflowCluster.startDataflowDatabase(getDatabaseTag());

		// start defined skipper server and check it started
		this.dataflowCluster.startSkipper(TagNames.SKIPPER_main);
		assertSkipperServerRunning(this.dataflowCluster);

		// start defined dataflow server and check it started
		this.dataflowCluster.startDataflow(TagNames.DATAFLOW_main);
		assertDataflowServerRunning(this.dataflowCluster);
	}

	protected Integer runCountQuery(String sql) {
		try {
			return runQuery(sql, Integer.class);
		} catch (Exception e) {
		}
		// return negative if error, ie table doesn't exist, etc.
		return -1;
	}

	protected <T> T runQuery(String sql, Class<T> requiredType) {
		JdbcTemplate jdbcTemplate = getJdbcTemplate();
		return jdbcTemplate.queryForObject(sql, requiredType);
	}

	protected void runExecute(String sql) {
		JdbcTemplate jdbcTemplate = getJdbcTemplate();
		jdbcTemplate.execute(sql);
	}

	protected JdbcTemplate getJdbcTemplate() {
		String jdbcUrl = this.dataflowCluster.getDataflowDatabaseHostJdbcUrl();
		SingleConnectionDataSource dataSource = new SingleConnectionDataSource(jdbcUrl, "spring", "spring", false);
		return new JdbcTemplate(dataSource);
	}

	/**
	 * Test full migration flow with defined versions going through from a defined
	 * expected skipper/dataflow combinations.
	 */
	protected void testFullMigrationFlow() {
		log.info("Running testFullMigrationFlow()");
		this.dataflowCluster.startSkipperDatabase(getDatabaseTag());
		this.dataflowCluster.startDataflowDatabase(getDatabaseTag());

		this.dataflowCluster.startSkipper(TagNames.SKIPPER_2_0);
		assertSkipperServerRunning(this.dataflowCluster);

		this.dataflowCluster.startDataflow(TagNames.DATAFLOW_2_0);
		assertDataflowServerRunning(this.dataflowCluster);

		this.dataflowCluster.replaceDataflow(TagNames.DATAFLOW_2_1);
		assertDataflowServerRunning(this.dataflowCluster);

		this.dataflowCluster.replaceSkipperAndDataflow(TagNames.SKIPPER_2_1, TagNames.DATAFLOW_2_2);
		assertSkipperServerRunning(this.dataflowCluster);
		assertDataflowServerRunning(this.dataflowCluster);

		this.dataflowCluster.replaceSkipperAndDataflow(TagNames.SKIPPER_2_2, TagNames.DATAFLOW_2_3);
		assertSkipperServerRunning(this.dataflowCluster);
		assertDataflowServerRunning(this.dataflowCluster);

		this.dataflowCluster.replaceSkipperAndDataflow(TagNames.SKIPPER_2_3, TagNames.DATAFLOW_2_4);
		assertSkipperServerRunning(this.dataflowCluster);
		assertDataflowServerRunning(this.dataflowCluster);

		this.dataflowCluster.replaceSkipperAndDataflow(TagNames.SKIPPER_2_4, TagNames.DATAFLOW_2_5);
		assertSkipperServerRunning(this.dataflowCluster);
		assertDataflowServerRunning(this.dataflowCluster);

		this.dataflowCluster.replaceSkipperAndDataflow(TagNames.SKIPPER_2_5, TagNames.DATAFLOW_2_6);
		assertSkipperServerRunning(this.dataflowCluster);
		assertDataflowServerRunning(this.dataflowCluster);

		this.dataflowCluster.replaceSkipperAndDataflow(TagNames.SKIPPER_2_6, TagNames.DATAFLOW_2_7);
		assertSkipperServerRunning(this.dataflowCluster);
		assertDataflowServerRunning(this.dataflowCluster);
	}
}
