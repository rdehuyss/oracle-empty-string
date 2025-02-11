package com.oracle.jobrunr;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final OracleContainer oracleContainer = new OracleContainer(DockerImageName
            .parse("gvenzl/oracle-free:latest-faststart")
            .asCompatibleSubstituteFor("gvenzl/oracle-xe"))
            .withStartupTimeoutSeconds(900)
            .withConnectTimeoutSeconds(500)
            .withEnv("DB_SID", "ORCL")
            .withEnv("DB_PASSWD", "oracle");


    public static void main(String[] args) throws InterruptedException {
        oracleContainer.start();
        DataSource dataSource = getHikariDataSource(oracleContainer);
        JobRunr.configure()
                .useStorageProvider(SqlStorageProviderFactory.using(dataSource))
                .useBackgroundJobServer(usingStandardBackgroundJobServerConfiguration(), false)
                .initialize();

        loadJobs(100_000);

        JobRunr.getBackgroundJobServer().start();
        Thread.currentThread().join();
    }


    protected static int loadJobs(int totalAmountOfJobs) {
        if (totalAmountOfJobs < 1) return 0;

        MyJob myJob = new MyJob();
        Stream<Integer> jobStream = IntStream.range(0, totalAmountOfJobs).boxed();
        Instant start = Instant.now();
        BackgroundJob.enqueue(jobStream, index -> myJob.doWork(totalAmountOfJobs, index));
        Instant end = Instant.now();
        LOGGER.info("Created {} jobs in {}", totalAmountOfJobs, Duration.between(start, end));
        return totalAmountOfJobs;
    }


    protected static HikariDataSource getHikariDataSource(OracleContainer oracleContainer) {
        return getHikariDataSource(oracleContainer.getJdbcUrl().replace("xepdb1", "FREEPDB1"), oracleContainer.getUsername(), oracleContainer.getPassword());
    }

    protected static  HikariDataSource getHikariDataSource(String jdbcUrl, String userName, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(userName);
        config.setPassword(password);
        config.setDriverClassName("oracle.jdbc.OracleDriver");
        config.setMinimumIdle(4);
        config.setMaximumPoolSize(4);
        return new HikariDataSource(config);
    }
}
