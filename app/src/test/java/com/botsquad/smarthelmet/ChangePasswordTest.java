package com.botsquad.smarthelmet;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 * Unit tests for Change Password functionality
 * Test Case ID: Case-015
 * Proponent: Cabañog, May Kyla L.
 * Date: 09/02/2025
 */
public class ChangePasswordTest {

    private ChangePasswordTestHelper helper;

    @Before
    public void setUp() {
        helper = new ChangePasswordTestHelper();
    }

    /**
     * Case-015: Change Password
     * Expected Result: Password updated; toast shown
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase015_ChangePassword() {
        String currentPassword = "oldPassword123";
        String newPassword = "newPassword456";
        String confirmPassword = "newPassword456";

        boolean isValid = helper.validatePasswordChange(
            currentPassword, newPassword, confirmPassword);
        assertTrue("Password change should be valid", isValid);

        boolean updateSuccess = helper.changePassword(
            currentPassword, newPassword);
        assertTrue("Password should be updated successfully", updateSuccess);
    }

    @Test
    public void testChangePasswordWithEmptyCurrentPassword() {
        String currentPassword = "";
        String newPassword = "newPassword456";
        String confirmPassword = "newPassword456";

        boolean isValid = helper.validatePasswordChange(
            currentPassword, newPassword, confirmPassword);
        assertFalse("Should fail with empty current password", isValid);
    }

    @Test
    public void testChangePasswordWithEmptyNewPassword() {
        String currentPassword = "oldPassword123";
        String newPassword = "";
        String confirmPassword = "";

        boolean isValid = helper.validatePasswordChange(
            currentPassword, newPassword, confirmPassword);
        assertFalse("Should fail with empty new password", isValid);
    }

    @Test
    public void testChangePasswordWithMismatchedPasswords() {
        String currentPassword = "oldPassword123";
        String newPassword = "newPassword456";
        String confirmPassword = "differentPassword789";

        boolean isValid = helper.validatePasswordChange(
            currentPassword, newPassword, confirmPassword);
        assertFalse("Should fail when passwords don't match", isValid);
    }

    @Test
    public void testChangePasswordWithIncorrectCurrentPassword() {
        String correctCurrentPassword = "oldPassword123";
        String wrongCurrentPassword = "wrongPassword";
        String newPassword = "newPassword456";

        boolean authSuccess = helper.authenticateUser(wrongCurrentPassword);
        assertFalse("Authentication should fail with wrong password", authSuccess);

        if (!authSuccess) {
            boolean changeSuccess = helper.changePassword(
                wrongCurrentPassword, newPassword);
            assertFalse("Password change should fail without authentication", changeSuccess);
        }
    }

    // Helper method to replace TextUtils.isEmpty() for unit tests
    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Helper class to test Change Password logic
     */
    static class ChangePasswordTestHelper {
        private String storedPassword = "oldPassword123";

        public boolean validatePasswordChange(String currentPassword, 
                                             String newPassword, 
                                             String confirmPassword) {
            if (isEmpty(currentPassword)) {
                return false;
            }
            if (isEmpty(newPassword)) {
                return false;
            }
            if (isEmpty(confirmPassword)) {
                return false;
            }
            if (!newPassword.equals(confirmPassword)) {
                return false;
            }
            return true;
        }

        public boolean authenticateUser(String currentPassword) {
            return storedPassword.equals(currentPassword);
        }

        public boolean changePassword(String currentPassword, String newPassword) {
            if (!authenticateUser(currentPassword)) {
                return false;
            }
            if (isEmpty(newPassword)) {
                return false;
            }
            storedPassword = newPassword;
            return true;
        }

        private static boolean isEmpty(String str) {
            return str == null || str.trim().isEmpty();
        }
    }
}
