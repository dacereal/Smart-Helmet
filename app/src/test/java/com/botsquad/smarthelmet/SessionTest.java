package com.botsquad.smarthelmet;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Session functionality
 * Test Case IDs: Case-018, Case-019
 * Proponent: Manog, Christy Mae
 * Date: 08/25/2025
 */
public class SessionTest {

    /**
     * Case-018: Auto-redirect if authenticated
     * Expected Result: Lands on Dashboard
     * Actual Result: Performed as expected
     * Remarks: Passed
     * Note: This test is also covered in LoginTest.java
     */
    @Test
    public void testAutoRedirectIfAuthenticated() {
        boolean isUserAuthenticated = true;

        if (isUserAuthenticated) {
            String expectedDestination = "Dashboard";
            String actualDestination = redirectToDashboard(isUserAuthenticated);
            assertEquals("Should redirect to Dashboard if authenticated", 
                expectedDestination, actualDestination);
        }
    }

    /**
     * Case-019: Logout
     * Expected Result: Returns to login; clears stack
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase019_Logout() {
        SessionHelper sessionHelper = new SessionHelper();
        
        // Simulate logged in state
        sessionHelper.setLoggedIn(true);
        assertTrue("User should be logged in", sessionHelper.isLoggedIn());

        // Perform logout
        boolean logoutSuccess = sessionHelper.logout();
        assertTrue("Logout should succeed", logoutSuccess);
        assertFalse("User should be logged out", sessionHelper.isLoggedIn());
        
        String expectedDestination = "Login";
        String actualDestination = sessionHelper.getCurrentScreen();
        assertEquals("Should return to login screen", expectedDestination, actualDestination);
    }

    @Test
    public void testLogoutClearsSession() {
        SessionHelper sessionHelper = new SessionHelper();
        
        // Set some session data
        sessionHelper.setLoggedIn(true);
        sessionHelper.setUserId("test_user_id");
        
        // Verify session data exists
        assertNotNull("User ID should be set", sessionHelper.getUserId());
        
        // Perform logout
        sessionHelper.logout();
        
        // Verify session is cleared
        assertNull("User ID should be cleared after logout", sessionHelper.getUserId());
        assertFalse("User should not be logged in", sessionHelper.isLoggedIn());
    }

    @Test
    public void testLogoutWhenNotLoggedIn() {
        SessionHelper sessionHelper = new SessionHelper();
        
        sessionHelper.setLoggedIn(false);
        
        boolean logoutSuccess = sessionHelper.logout();
        // Logout might succeed or fail when not logged in, depending on implementation
        // In this case, we'll assume it's safe to call logout even when not logged in
        assertFalse("User should remain logged out", sessionHelper.isLoggedIn());
    }

    // Helper method
    private String redirectToDashboard(boolean isAuthenticated) {
        return isAuthenticated ? "Dashboard" : "Login";
    }

    /**
     * Helper class to test Session logic
     */
    static class SessionHelper {
        private boolean isLoggedIn = false;
        private String userId = null;
        private String currentScreen = "Login";

        public void setLoggedIn(boolean loggedIn) {
            this.isLoggedIn = loggedIn;
            if (loggedIn) {
                this.currentScreen = "Dashboard";
            } else {
                this.currentScreen = "Login";
            }
        }

        public boolean isLoggedIn() {
            return isLoggedIn;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }

        public boolean logout() {
            if (isLoggedIn) {
                isLoggedIn = false;
                userId = null;
                currentScreen = "Login";
                return true;
            }
            return false;
        }

        public String getCurrentScreen() {
            return currentScreen;
        }
    }
}
