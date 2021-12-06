package io.github.automationreddy.slack;

import com.google.common.collect.Lists;
import com.slack.api.Slack;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.webhook.Payload;
import org.apache.commons.lang.RandomStringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Slack reporter class which is used to build the Slack message and
 * send it as a notification to the Slack channel
 *
 */
public class SlackReporter {

    public static Properties properties;
    public static List<LayoutBlock> blocksList = new ArrayList<>();

    static {
        try {
            properties = new Properties();
            properties.load(new FileInputStream(System.getProperty("user.dir") + "/src/main/resources/slack.properties"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final String URL = properties.getProperty("WEBHOOK_URL");
    private static int TOTAL_PASSED_TESTS = 0;
    private static int TOTAL_FAILED_TESTS = 0;
    public static boolean IS_SUIT_FAILED;
    private static final String resultUid = "ID-" + RandomStringUtils.randomAlphanumeric(10);
    private static final boolean showOnlyFailedResults = Objects.nonNull(properties.getProperty("SHOW_FAILED_RESULTS_ONLY")) && properties.getProperty("SHOW_FAILED_RESULTS_ONLY").equalsIgnoreCase("true");

    private SlackReporter() {
    }

    /**
     * Sends message to a Slack channel
     *
     * @param blocksList Message
     */
    public static void sendNotification(List<LayoutBlock> blocksList) {
        Slack slack = Slack.getInstance();
        splitBlocksInToChunks(blocksList).forEach(block -> {
            Payload payload = Payload.builder().blocks(block).build();
            try {
                System.out.println(slack.send(URL, payload) + "  " + resultUid);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Builds a header block
     *
     * @param headerText String
     * @return LayoutBlock
     */
    private static LayoutBlock buildBlockHeader(String headerText) {
        return Blocks.header(headerBlockBuilder -> HeaderBlock.builder().text(PlainTextObject.builder().emoji(true).text(headerText).build()));
    }

    /**
     * Builds a divider line
     *
     * @return LayoutBlock
     */
    private static LayoutBlock buildDivider() {
        return Blocks.divider();
    }

    /**
     * Builds a markdown section
     *
     * @param markDownText String
     * @return LayoutBlock
     */
    private static LayoutBlock buildMarkDownSection(String markDownText) {
        return Blocks.section(sectionBlockBuilder -> SectionBlock.builder().text(MarkdownTextObject.builder().text(markDownText).build()));
    }

    /**
     * Builds context
     *
     * @param contextBlockElements Context Block type
     * @return LayoutBlock
     */
    private static LayoutBlock buildContext(List<ContextBlockElement> contextBlockElements) {
        return Blocks.context(contextBlockBuilder -> ContextBlock.builder().elements(contextBlockElements));
    }

    /**
     * Builds context elements
     *
     * @param markDownText String
     * @return LayoutBlock
     */
    private static List<ContextBlockElement> buildContextBlockElements(String markDownText) {
        return BlockElements.asContextElements(MarkdownTextObject.builder().text(markDownText).build());
    }

    /**
     * This method helps in building/composing the Slack message layout for TestNG framework
     *
     * @param suiteInfo Test Suite details
     * @param testInfo Test Results
     * @return List of LayoutBlocks
     */
    public static List<LayoutBlock> buildTestNgSlackMessage(String suiteDuration, Map<String, ArrayList<Object[]>> suiteInfo, Map<String, ArrayList<Object[]>> testInfo) {

        for (Map.Entry<String, ArrayList<Object[]>> entry : suiteInfo.entrySet()) {
            String suiteName = entry.getKey();
            ArrayList<Object[]> testContextInfo = entry.getValue();
            IS_SUIT_FAILED = isSuiteFailed(testContextInfo);
            blocksList.add(buildContext(buildContextBlockElements("*Suite Info:*     Result ID: " + resultUid)));
            buildHeaderMessageWithStatus(IS_SUIT_FAILED, suiteName);
            buildDurationWithBranchName(suiteDuration);
            blocksList.add(buildDivider());
            testContextInfo.forEach(testContext -> {
                blocksList.add(buildContext(buildContextBlockElements("*Test Name:*     Result ID: " + resultUid)));
                buildHeaderMessageWithStatus(isTestContextFailed(testContext), testContext[0].toString());
                buildDurationWithIcon(testContext[1].toString());
                blocksList.add(buildDivider());
                for (Map.Entry<String, ArrayList<Object[]>> e : testInfo.entrySet()) {
                    String[] testClassDetails = e.getKey().split("-->");
                    ArrayList<Object[]> testResults = e.getValue();
                    if (testClassDetails[1].equals(testContext[0])) {
                        blocksList.add(buildContext(buildContextBlockElements("*Test Class:*")));
                        Object[] resultCount = buildTestClassHeaderWithStats(testClassDetails[0], testResults);
                        if (!(showOnlyFailedResults && (int) resultCount[1] == 0)) {
                            blocksList.add(buildContext(buildContextBlockElements("*Test Results:*")));
                            blocksList.add(buildDivider());
                        }
                        testResults.forEach(result -> {
                            String retriedMessage = result[2].equals(true) ? " [❗It was a retried test. Please check your logs for more info]" : "";
                            String methodName;
                            if (result[1].equals(1)) {
                                if(!showOnlyFailedResults) {
                                    methodName = "* ✅   " + result[0] + "*" + retriedMessage;
                                    blocksList.add(buildContext(buildContextBlockElements(methodName)));
                                }
                            } else {
                                methodName = "* ❌   " + result[0] + "*" + retriedMessage;
                                blocksList.add(buildContext(buildContextBlockElements(methodName)));
                            }
                        });
                        TOTAL_PASSED_TESTS += (int) resultCount[0];
                        TOTAL_FAILED_TESTS += (int) resultCount[1];
                        blocksList.add(buildDivider());
                    }
                }
                blocksList.add(buildContext(buildContextBlockElements("End of results for the test *" + testContext[0] + "*     Result ID: " + resultUid)));
                blocksList.add(buildDivider());
            });
            blocksList.add(buildMarkDownSection("🔵 *Total Tests:* " + (TOTAL_PASSED_TESTS + TOTAL_FAILED_TESTS)  + " " +
                    "     🟢 *Passed Tests:* " + TOTAL_PASSED_TESTS + "      🔴 *Failed Tests:* " + TOTAL_FAILED_TESTS));
            if (showOnlyFailedResults && IS_SUIT_FAILED) {
                blocksList.add(buildContext(buildContextBlockElements("*You have chosen to get only the failed results. However, the above total/pass/fail count is calculated on suite level*")));
            }
            blocksList.add(buildContext(buildContextBlockElements("End of results for the test suite *" + suiteName + "*     Result " + resultUid)));
            blocksList.add(buildDivider());
            TOTAL_PASSED_TESTS = 0;
            TOTAL_FAILED_TESTS = 0;
        }
        System.out.println(blocksList);
        return blocksList;
    }

    /**
     * Helps in identifying if a test suite is failed
     *
     * @param allTests Test Context Details
     * @return Suite is passed or failed - true/false
     */
    public static boolean isSuiteFailed(ArrayList<Object[]> allTests) {
        return allTests.stream().anyMatch(test -> test[2].equals(true));
    }

    /**
     * Helps in identifying if a test context is failed
     *
     * @param allTests Test Class Details
     * @return Suite is passed or failed - true/false
     */
    private static boolean isTestContextFailed(Object[] allTests) {
        return allTests[2].equals(true);
    }

    /**
     * To build a header message with pass/fail status
     *
     * @param resultStatus Feature/Suite status
     * @param headerName Name of the feature/suite
     */
    private static void buildHeaderMessageWithStatus(boolean resultStatus, String headerName) {
        blocksList.add(buildBlockHeader(!resultStatus ? "✅   " + headerName : "❌   " + headerName));
    }

    /**
     * Build test duration with icon
     *
     * @param duration Duration
     */
    private static void buildDurationWithIcon(String duration) {
        String[] time = duration.split(":");
        blocksList.add(buildContext(buildContextBlockElements("  🕒  " + time[0] + " min " + time[1] + " sec")));
    }

    /**
     * Build test duration with icon and branch name. Branch name needs to be provided
     * as a runtime variable
     *
     * @param duration Duration
     */
    private static void buildDurationWithBranchName(String duration) {
        String[] time = duration.split(":");
        String branchName = Objects.nonNull(System.getProperty("BRANCH_LINK")) && Objects.nonNull(System.getProperty("BRANCH_NAME")) ? "          " +
                "Executed on branch: <" + System.getProperty("BRANCH_LINK") + "|" + System.getProperty("BRANCH_NAME") + ">" : "";
        blocksList.add(buildContext(buildContextBlockElements("  🕒  " + time[0] + " min " + time[1] + " sec" + branchName)));
    }

    /**
     * To build a sub-header (TestClass) message with pass/fail status
     *
     * @param testClassName Name of the test class
     * @param testResults Test results
     * @return Object[] Pass/Fail count
     */
    private static Object[] buildTestClassHeaderWithStats(String testClassName, ArrayList<Object[]> testResults) {
        int passedTests = (int) testResults.stream().filter(result -> result[1].equals(1)).count();
        int failedTests = (int) testResults.stream().filter(result -> result[1].equals(2)).count();
        String passFailInfo = "        🟢 Passed: *" + passedTests + "*     🔴 Failed: *" + failedTests + "*";
        String testName;
        if (failedTests>0) {
            testName = "*❌   " + testClassName + "*";
        } else {
            testName = "*✅   " + testClassName + "*";
        }
        blocksList.add(buildMarkDownSection(testName + passFailInfo));
        return new Object[]{passedTests, failedTests};
    }

    /**
     * It helps to split the list into chunks of given size
     * @param blocksList message blocks
     * @return List of layout block lists
     */
    private static List<List<LayoutBlock>> splitBlocksInToChunks(List<LayoutBlock> blocksList) {
        return Lists.partition(blocksList, 50);
    }
}

