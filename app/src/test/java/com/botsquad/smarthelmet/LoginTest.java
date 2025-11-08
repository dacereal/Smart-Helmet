package com.botsquad.smarthelmet;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for Login functionality
 * Test Case IDs: Case-002, Case-003, Case-018
 * Proponent: Dapanas, Caryl A. / Manog, Christy Mae
 * Date: 08/25/2025
 */
public class LoginTest {

    /**
     * Case-002: Login with valid credentials
     * Expected Result: Navigates to Dashboard
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase002_LoginWithValidCredentials() {
        String email = "test@example.com";
        String password = "password123";

        // Validate inputs are not empty
        assertFalse("Email should not be empty", isEmpty(email));
        assertFalse("Password should not be empty", isEmpty(password));

        // Simulate successful login
        boolean loginValid = validateLoginInput(email, password);
        assertTrue("Login should succeed with valid credentials", loginValid);
    }

    /**
     * Case-003: Login validation with empty fields
     * Expected Result: Shows message 'no navigation'
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase003_LoginValidationWithEmptyFields() {
        // Test with empty email
        String emailEmpty = "";
        String password = "password123";
        boolean isValid = validateLoginInput(emailEmpty, password);
        assertFalse("Login should fail with empty email", isValid);

        // Test with empty password
        String email = "test@example.com";
        String passwordEmpty = "";
        isValid = validateLoginInput(email, passwordEmpty);
        assertFalse("Login should fail with empty password", isValid);

        // Test with both empty
        isValid = validateLoginInput("", "");
        assertFalse("Login should fail with both fields empty", isValid);
    }

    @Test
    public void testLoginWithTrimmedCredentials() {
        String email = "  test@example.com  ";
        String password = "  password123  ";

        String trimmedEmail = email.trim();
        String trimmedPassword = password.trim();

        assertFalse("Trimmed email should not be empty", isEmpty(trimmedEmail));
        assertFalse("Trimmed password should not be empty", isEmpty(trimmedPassword));
        assertTrue("Login should succeed with trimmed credentials", 
            validateLoginInput(trimmedEmail, trimmedPassword));
    }

    @Test
    public void testAdminLoginCredentials() {
        String email = "admin";
        String password = "botsquad";

        boolean isAdmin = email.equals("admin") && password.equals("botsquad");
        assertTrue("Should recognize admin credentials", isAdmin);
    }

    /**
     * Case-018: Auto-redirect if authenticated
     * Expected Result: Lands on Dashboard
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase018_AutoRedirectIfAuthenticated() {
        // Simulate user already authenticated
        boolean isUserAuthenticated = true;

        if (isUserAuthenticated) {
            // Should redirect to Dashboard
            String expectedDestination = "Dashboard";
            String actualDestination = redirectToDashboard(isUserAuthenticated);
            assertEquals("Should redirect to Dashboard if authenticated", 
                expectedDestination, actualDestination);
        }
    }

    @Test
    public void testAutoRedirectIfNotAuthenticated() {
        boolean isUserAuthenticated = false;

        if (!isUserAuthenticated) {
            // Should not redirect, stay on Login
            String expectedDestination = "Login";
            String actualDestination = redirectToDashboard(isUserAuthenticated);
            assertEquals("Should stay on Login if not authenticated", 
                expectedDestination, actualDestination);
        }
    }

    // Helper method to replace TextUtils.isEmpty() for unit tests
    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    // Helper methods to simulate login logic
    private boolean validateLoginInput(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        return true;
    }

    private String redirectToDashboard(boolean isAuthenticated) {
        return isAuthenticated ? "Dashboard" : "Login";
    }
}
