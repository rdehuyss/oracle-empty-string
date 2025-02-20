## Oracle DB
### `select ... for update skip locked` returns empty CLOB values

> tldr; this repo shows an issue with Oracle where a CLOB value is returning an empty string although there is actual content


### Readme.md contents
- Overview - What is going on? 
- How to reproduce it locally
- JobRunr: how to change column types and test again
- Actual SQL query causing the issue
- Known facts

### Overview - What is going on?
This repo is a reproducer where Oracle under load return an empty CLOB when using `select for update skip locked`. Removing `for update skip locked` solves the issue (replace JobRunr version `7.4.0` in pom by `6.3.5`) or changing from `CLOB` to `VARCHAR2(4000)`.
This is reproducible on a Apple M3 Max and a bare-metal [Hetzner EX44](https://www.hetzner.com/dedicated-rootserver/ex44) 

The empty value is retrieved as follows:
- in [JobTable (line 211)](https://github.com/jobrunr/jobrunr/blob/master/core/src/main/java/org/jobrunr/storage/sql/common/JobTable.java#L211): `resultSet.asString("jobAsJson")`
- in [SqlResultSet (line 33)](https://github.com/jobrunr/jobrunr/blob/master/core/src/main/java/org/jobrunr/storage/sql/common/db/SqlResultSet.java#L33): `return autobox(val(name), String.class)`
- in [ReflectionUtils (line 280)](https://github.com/jobrunr/jobrunr/blob/master/core/src/main/java/org/jobrunr/utils/reflection/ReflectionUtils.java#L280): `return Autoboxer.autobox(value, type)`
- in [Autoboxer (line 37)](https://github.com/jobrunr/jobrunr/blob/master/core/src/main/java/org/jobrunr/utils/reflection/autobox/Autoboxer.java#L37): `autoboxer.autobox(value, type)`
- in [StringTypeAutoboxer](https://github.com/jobrunr/jobrunr/blob/master/core/src/main/java/org/jobrunr/utils/reflection/autobox/StringTypeAutoboxer.java) (line 22): `clob.getSubString(1, (int) clob.length())`

See the attached stacktrace where Jackson complains about an empty string.

```java
16:07:58.429 [backgroundjob-worker] INFO  com.oracle.jobrunr.MyJob - Progress: 47% completed (47001 of 100000 jobs).
16:07:58.835 [backgroundjob-worker] INFO  com.oracle.jobrunr.MyJob - Progress: 48% completed (47999 of 100000 jobs).
16:07:59.279 [backgroundjob-worker] INFO  com.oracle.jobrunr.MyJob - Progress: 49% completed (48999 of 100000 jobs).
16:08:09.118 [backgroundjob-zookeeper-pool-2-thread-2] WARN  org.jobrunr.server.JobSteward - JobRunr encountered a problematic exception. Please create a bug report (if possible, provide the code to reproduce this and the stacktrace) - Processing will continue.
org.jobrunr.JobRunrException: JobRunr encountered a problematic exception. Please create a bug report (if possible, provide the code to reproduce this and the stacktrace)
	at org.jobrunr.JobRunrException.shouldNotHappenException(JobRunrException.java:43)
	at org.jobrunr.utils.mapper.jackson.JacksonJsonMapper.deserialize(JacksonJsonMapper.java:87)
	at org.jobrunr.jobs.mappers.JobMapper.deserializeJob(JobMapper.java:20)
	at org.jobrunr.storage.sql.common.JobTable.toJob(JobTable.java:211)
	at java.base/java.util.stream.ReferencePipeline$3$1.accept(ReferencePipeline.java:197)
Caused by: com.fasterxml.jackson.databind.exc.MismatchedInputException: No content to map due to end-of-input
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1]
	at com.fasterxml.jackson.databind.exc.MismatchedInputException.from(MismatchedInputException.java:59)
	at com.fasterxml.jackson.databind.ObjectMapper._initForReading(ObjectMapper.java:5008)
	at com.fasterxml.jackson.databind.ObjectMapper._readMapAndClose(ObjectMapper.java:4910)
	at com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3860)
	at com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3828)
```

### How to reproduce it locally?
- Clone the project locally
- Open in Intellij or your preferred IDE
- Navigate to the Main class and run the main method
- The reproducer will automatically:
  - create the DB schema
  - insert all data (100k rows)
  - and reproduce the issue (typically around 50k rows or about 2 minutes)


### JobRunr - how to change column types and test again
- Navigate to [v001__create_job_table.sql](https://github.com/jobrunr/jobrunr/blob/master/core/src/main/resources/org/jobrunr/storage/sql/oracle/migrations/v001__create_job_table.sql#L5)
- Change `CLOB` to another column type (tested with `VARCHAR2(4000)` and `CLOB`)
- In the root of the project, run the command `./gradlew publishToMavenLocal`
- Change the JobRunr version in the `pom.xml` to `1.0.0-SNAPSHOT`
- Reload the maven dependencies
- Run the `main` method of the `Main` class again

### SQL Query

```sql
select jobAsJson from jobrunr_jobs where ROWNUM <= 256 and state = 'ENQUEUED' ORDER BY updatedAt ASC FOR UPDATE SKIP LOCKED
```
### Known facts
- the issue does not happen __without__ `FOR UPDATE SKIP LOCKED` and `CLOB` column type
- the issue does not happen __with__ `FOR UPDATE SKIP LOCKED` and `VARCHAR2(4000)` column type (which is often too small for failing jobs)