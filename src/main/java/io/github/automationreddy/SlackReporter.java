package io.github.automationreddy;

import com.slack.api.Slack;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.webhook.Payload;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Slack reporter class which is used to build the slack message and
 * send it as a notification to the slack channel
 *
 */
public class SlackReporter {

    public static Properties properties;

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

    private SlackReporter() {
    }

    /**
     * Sends message to a Slack channel
     *
     * @param blocksList Message
     * @throws IOException IOException
     */
    public static void sendNotification(List<LayoutBlock> blocksList) throws IOException {
        Slack slack = Slack.getInstance();
        Payload payload = Payload.builder().blocks(blocksList).build();
        slack.send(URL, payload);
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
     * @param contextBlockElements
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
     * This method helps in building/composing the Slack message layout
     *
     * @param allTestInfo
     * @param allMethodInfo
     * @return List of LayoutBlocks
     */
    public static List<LayoutBlock> buildSlackMessage(Map<Object[], ArrayList<Object[]>> allTestInfo, Map<Object[], ArrayList<Object[]>> allMethodInfo) {
        List<LayoutBlock> blocksList = new ArrayList<>();
        for (Map.Entry<Object[], ArrayList<Object[]>> entry : allTestInfo.entrySet()) {
            blocksList.add(buildContext(buildContextBlockElements("*Suite Info:*")));
            Object[] suiteInfo = entry.getKey();
            ArrayList<Object[]> testNames = entry.getValue();
            IS_SUIT_FAILED = isSuiteFailed(testNames);
            String suiteNameWithStatus = !IS_SUIT_FAILED ? "✅   " + suiteInfo[0] : "❌   " + suiteInfo[0];
            blocksList.add(buildBlockHeader(suiteNameWithStatus));
            String[] time = suiteInfo[1].toString().split(":");
            blocksList.add(buildContext(buildContextBlockElements("  🕒  " + time[0] + " min " + time[1] + " sec")));
            blocksList.add(buildDivider());
            for (Map.Entry<Object[], ArrayList<Object[]>> e : allMethodInfo.entrySet()) {
                blocksList.add(buildContext(buildContextBlockElements("*Test Name:*")));
                Object[] testDetails = e.getKey();
                ArrayList<Object[]> testResults = e.getValue();
                String passFailInfo = "        🟢 Passed: *" + testDetails[2] + "*     🔴 Failed: *" + testDetails[3] + "*";
                String testName;
                if (testDetails[1].equals(true)) {
                    testName = "*❌   " + testDetails[0] + "*";
                } else {
                    testName = "*✅   " + testDetails[0] + "*";
                }
                blocksList.add(buildMarkDownSection(testName + passFailInfo));
                blocksList.add(buildContext(buildContextBlockElements("*Test Results:*")));
                blocksList.add(buildDivider());
                testResults.forEach(result -> {
                    String retriedMessage = result[2].equals(true) ? " [❗This is a retried test. Please check your logs for more info]" : "";
                    if (result[1].equals(1)) {
                        ++TOTAL_PASSED_TESTS;
                        String methodName = "* ✅   " + result[0] + "*" + retriedMessage;
                        blocksList.add(buildContext(buildContextBlockElements(methodName)));
                    } else {
                        ++TOTAL_FAILED_TESTS;
                        String methodName = "* ❌   " + result[0] + "*" + retriedMessage;
                        blocksList.add(buildContext(buildContextBlockElements(methodName)));
                    }
                });
                blocksList.add(buildDivider());
            }
            blocksList.add(buildMarkDownSection("🔵 *Total Tests:* " + (TOTAL_PASSED_TESTS + TOTAL_FAILED_TESTS)  + " " +
                    "     🟢 *Passed Tests:* " + TOTAL_PASSED_TESTS + "      🔴 *Failed Tests:* " + TOTAL_FAILED_TESTS));
            blocksList.add(buildContext(buildContextBlockElements("End of results for the test suite *" + suiteInfo[0] + "*")));
            blocksList.add(buildDivider());
            TOTAL_PASSED_TESTS = 0;
            TOTAL_FAILED_TESTS = 0;
        }
        return blocksList;
    }

    /**
     * Helps in identifying if a test suite is failed
     *
     * @param allTests
     * @return Suite is passed or failed - true/false
     */
    public static boolean isSuiteFailed(ArrayList<Object[]> allTests) {
        return allTests.stream().anyMatch(test -> test[1].equals(true));
    }

}
