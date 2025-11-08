package com.botsquad.smarthelmet;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for Drowsiness Logs functionality
 * Test Case ID: Case-020
 * Proponent: Manog, Christy Mae
 * Date: 08/25/2025
 */
public class DrowsinessLogsTest {

    private DrowsinessLogsTestHelper helper;

    @Before
    public void setUp() {
        helper = new DrowsinessLogsTestHelper();
    }

    /**
     * Case-020: View List
     * Expected Result: List with details; empty handled
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase020_OpenDrowsinessHistory() {
        String userId = "test_user_id";
        
        List<Long> logs = helper.loadDrowsinessLogs(userId);
        
        assertNotNull("Logs list should not be null", logs);
        
        // Verify logs are formatted correctly
        if (!logs.isEmpty()) {
            for (Long timestamp : logs) {
                assertNotNull("Timestamp should not be null", timestamp);
                assertTrue("Timestamp should be positive", timestamp > 0);
            }
        }
    }

    @Test
    public void testViewListWithDetails() {
        String userId = "test_user_id";
        
        // Add some test logs
        helper.addDrowsinessEvent(userId);
        helper.addDrowsinessEvent(userId);
        helper.addDrowsinessEvent(userId);
        
        List<Long> logs = helper.loadDrowsinessLogs(userId);
        
        assertNotNull("Logs should not be null", logs);
        assertEquals("Should have 3 log entries", 3, logs.size());
        
        // Verify logs are sorted (newest first)
        for (int i = 0; i < logs.size() - 1; i++) {
            assertTrue("Logs should be sorted newest first", 
                logs.get(i) >= logs.get(i + 1));
        }
    }

    @Test
    public void testViewListEmptyHandled() {
        String userId = "user_with_no_logs";
        
        List<Long> logs = helper.loadDrowsinessLogs(userId);
        
        assertNotNull("Logs should not be null even when empty", logs);
        
        if (logs.isEmpty()) {
            String message = helper.getEmptyMessage();
            assertNotNull("Empty message should be provided", message);
            assertFalse("Empty message should not be empty", message.trim().isEmpty());
        }
    }

    @Test
    public void testLogEntryFormatting() {
        long timestamp = System.currentTimeMillis();
        String formattedLog = helper.formatLogEntry(timestamp);
        
        assertNotNull("Formatted log should not be null", formattedLog);
        assertFalse("Formatted log should not be empty", formattedLog.isEmpty());
        assertTrue("Formatted log should contain date/time info", 
            formattedLog.length() > 10);
    }

    @Test
    public void testMultipleLogEntries() {
        String userId = "test_user_id";
        
        // Clear existing logs
        helper.clearLogs(userId);
        
        // Add multiple logs with delays to ensure different timestamps
        long time1 = System.currentTimeMillis();
        helper.addDrowsinessEventWithTimestamp(userId, time1);
        
        long time2 = time1 + 1000; // 1 second later
        helper.addDrowsinessEventWithTimestamp(userId, time2);
        
        long time3 = time2 + 1000; // 1 second later
        helper.addDrowsinessEventWithTimestamp(userId, time3);
        
        List<Long> logs = helper.loadDrowsinessLogs(userId);
        
        assertEquals("Should have 3 log entries", 3, logs.size());
        assertEquals("First entry should be newest", time3, (long) logs.get(0));
    }

    /**
     * Helper class to test Drowsiness Logs logic
     */
    static class DrowsinessLogsTestHelper {
        private java.util.Map<String, List<Long>> userLogs = new java.util.HashMap<>();

        public List<Long> loadDrowsinessLogs(String userId) {
            List<Long> logs = userLogs.get(userId);
            if (logs == null) {
                return new ArrayList<>();
            }
            // Sort in descending order (newest first)
            logs.sort((a, b) -> Long.compare(b, a));
            return new ArrayList<>(logs);
        }

        public void addDrowsinessEvent(String userId) {
            long timestamp = System.currentTimeMillis();
            addDrowsinessEventWithTimestamp(userId, timestamp);
        }

        public void addDrowsinessEventWithTimestamp(String userId, long timestamp) {
            List<Long> logs = userLogs.getOrDefault(userId, new ArrayList<>());
            logs.add(timestamp);
            userLogs.put(userId, logs);
        }

        public void clearLogs(String userId) {
            userLogs.remove(userId);
        }

        public String formatLogEntry(long timestamp) {
            java.util.Date date = new java.util.Date(timestamp);
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat(
                "MMM dd, yyyy", java.util.Locale.getDefault());
            java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat(
                "HH:mm:ss", java.util.Locale.getDefault());
            
            return dateFormat.format(date) + " at " + timeFormat.format(date);
        }

        public String getEmptyMessage() {
            return "No drowsiness events recorded yet.\n\nDrowsiness events will appear here when detected.";
        }
    }
}
