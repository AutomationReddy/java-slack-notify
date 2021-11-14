package io.github.automationreddy;

import com.slack.api.model.block.LayoutBlock;
import org.testng.*;
import org.testng.xml.XmlSuite;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.github.automationreddy.SlackReporter.*;

/**
 * This class is used to generate the test suite results that is required
 * for slack reporter and builds the message
 *
 */
public class TestNGReporter implements IReporter {

    Map<Object[], ArrayList<Object[]>> allTestInfo = new HashMap<>();
    Map<Object[], ArrayList<Object[]>> allMethodInfo = new HashMap<>();

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {
        suites.stream().filter(suite -> !suite.getResults().isEmpty()).forEachOrdered(suite -> {
            ArrayList<Object[]> testNames = new ArrayList<>();
            long totalTime = 0;
            String suiteName = suite.getName();
            for (ISuiteResult suiteResult : suite.getResults().values()) {
                ArrayList<Object[]> methodResults = new ArrayList<>();
                ITestContext testContext = suiteResult.getTestContext();
                totalTime += calculateTestDuration(testContext);
                String testName = testContext.getName();
                Set<ITestResult> passedTests = testContext.getPassedTests().getAllResults();
                Set<ITestResult> failedTests = testContext.getFailedTests().getAllResults();
                Set<ITestResult> skippedTests = testContext.getSkippedTests().getAllResults();
                boolean isTestFail = failedTests.size() > 0;
                Object[] testDetails = new Object[]{testName, isTestFail, passedTests.size(), failedTests.size(), skippedTests.size()};
                testNames.add(testDetails);
                passedTests.forEach(result -> methodResults.add(buildTestInfo(result)));
                if (isTestFail) failedTests.forEach(result -> methodResults.add(buildTestInfo(result)));
                skippedTests.stream().filter(ITestResult::wasRetried).forEachOrdered(result -> {
                    String methodName = Objects.nonNull(result.getTestName()) ? result.getTestName() : result.getName();
                    methodResults.stream().filter(test -> test[0].equals(methodName)).forEach(test -> test[2] = result.wasRetried());
                });
                allMethodInfo.put(testDetails, methodResults);
            }
            String suiteDuration = TimeUnit.MILLISECONDS.toMinutes(totalTime) + ":" + (TimeUnit.MILLISECONDS.toSeconds(totalTime)%60);
            allTestInfo.put(new Object[]{suiteName, suiteDuration}, testNames);
        });
        List<LayoutBlock> slackPayload = buildSlackMessage(allTestInfo, allMethodInfo);
        try {
            boolean notifyOnlyOnFailure = Objects.nonNull(properties.getProperty("NOTIFY_ONLY_ON_FAILURE")) && properties.getProperty("NOTIFY_ONLY_ON_FAILURE").equalsIgnoreCase("true");
            if (IS_SUIT_FAILED && notifyOnlyOnFailure) {
                sendNotification(slackPayload);
            } else if (!notifyOnlyOnFailure) {
                sendNotification(slackPayload);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Builds the test method information
     *
     * @param result ITestResult
     * @return Test/Method name, status and isRetried
     */
    private Object[] buildTestInfo(ITestResult result) {
        String methodName = Objects.nonNull(result.getTestName()) ? result.getTestName() : result.getName();
        int testStatus = result.getStatus();
        boolean isRetried = result.wasRetried();
        return new Object[]{methodName, testStatus, isRetried};
    }

    /**
     * It calculates the test duration
     *
     * @param iTestContext ITestContext
     * @return test duration
     */
    private long calculateTestDuration(ITestContext iTestContext) {
        Date start = iTestContext.getStartDate();
        Date end = iTestContext.getEndDate();
        return end.getTime() - start.getTime();
    }


}
