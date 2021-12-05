package io.github.automationreddy.testng;

import com.slack.api.model.block.LayoutBlock;
import org.testng.*;
import org.testng.xml.XmlSuite;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.github.automationreddy.slack.SlackReporter.*;

/**
 * This class is used to generate the test suite results that is required
 * for slack reporter and builds the message
 */
public class TestNGSlackReporter implements IReporter {

    private final Map<String, ArrayList<Object[]>> testSuite = new HashMap<>();
    private final Map<String, ArrayList<Object[]>> testClass = new HashMap<>();
    private String suiteDuration;
    private long totalTime = 0;

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDirectory) {

        suites.stream().filter(suite -> !suite.getResults().isEmpty()).forEachOrdered(suite -> {
            String suiteName = suite.getName();
            ArrayList<Object[]> testNames = buildTestContextInfo(suite);
            suiteDuration = TimeUnit.MILLISECONDS.toMinutes(totalTime) + ":" + (TimeUnit.MILLISECONDS.toSeconds(totalTime) % 60);
            testSuite.put(suiteName, testNames);
        });

        List<LayoutBlock> slackPayload = buildTestNgSlackMessage(suiteDuration, testSuite, testClass);

        try {
            boolean notifyOnlyOnFailure = Objects.nonNull(properties.getProperty("NOTIFY_ONLY_ON_FAILURE")) && properties.getProperty("NOTIFY_ONLY_ON_FAILURE").equalsIgnoreCase("true");
            if (IS_SUIT_FAILED && notifyOnlyOnFailure) {
                sendNotification(slackPayload);
            } else if (!notifyOnlyOnFailure) {
                sendNotification(slackPayload);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This method is used to build the test context information i.e. <test>
     * tag in testng.xml file
     *
     * @param suite Test Suite
     * @return List of test tag names
     */
    private ArrayList<Object[]> buildTestContextInfo(ISuite suite) {
        ArrayList<Object[]> testNames = new ArrayList<>();
        for (ISuiteResult suiteResult : suite.getResults().values()) {
            ITestContext iTestContext = suiteResult.getTestContext();
            String testContextName = iTestContext.getName();
            long testContextDuration = calculateTestDuration(iTestContext);
            String formattedDuration = TimeUnit.MILLISECONDS.toMinutes(testContextDuration) + ":" + (TimeUnit.MILLISECONDS.toSeconds(testContextDuration) % 60);
            boolean isTestContextFailed = iTestContext.getFailedTests().size() > 0;
            totalTime += testContextDuration;
            testNames.add(new Object[]{testContextName, formattedDuration, isTestContextFailed});
            buildTestClassInfo(iTestContext);
        }
        return testNames;
    }

    /**
     * This method is used to build test class details
     *
     * @param iTestContext Test Context details (<test> tag in testng.xml)
     */
    private void buildTestClassInfo(ITestContext iTestContext) {
        Set<ITestResult> passedTests = iTestContext.getPassedTests().getAllResults();
        Set<ITestResult> failedTests = iTestContext.getFailedTests().getAllResults();
        Set<ITestResult> skippedTests = iTestContext.getSkippedTests().getAllResults();
        boolean isTestFail = failedTests.size() > 0;
        passedTests.forEach(this::buildTestMethodInfo);
        if (isTestFail) failedTests.forEach(this::buildTestMethodInfo);
        skippedTests.stream().filter(ITestResult::wasRetried).forEachOrdered(result -> {
            String methodName = Objects.nonNull(result.getTestName()) ? result.getTestName() : result.getName();
            testClass.get(getTestClassName(result)).stream().filter(test -> test[0].equals(methodName)).forEach(test -> test[2] = result.wasRetried());
        });
    }

    /**
     * Builds the test method information
     *
     * @param result ITestResult
     */
    private void buildTestMethodInfo(ITestResult result) {
        String testClassName = getTestClassName(result);
        String methodName = Objects.nonNull(result.getTestName()) ? result.getTestName() : result.getName();
        int testStatus = result.getStatus();
        boolean wasRetried = result.wasRetried();
        if(testClass.containsKey(testClassName)) {
            testClass.get(testClassName).add(new Object[]{methodName, testStatus, wasRetried});
        }
        else {
            ArrayList<Object[]> methodInfo = new ArrayList<>();
            methodInfo.add(new Object[]{methodName, testStatus, wasRetried});
            testClass.put(testClassName, methodInfo);
        }
    }

    /**
     * This method is used to get a formatted test class name
     * @param result Test Method result
     * @return Test Class name and Test Context Name
     */
    private String getTestClassName(ITestResult result) {
        String[] testClassFullName = result.getInstanceName().split("\\.");
        return testClassFullName[testClassFullName.length - 1] + "-->" + result.getTestContext().getName();
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

