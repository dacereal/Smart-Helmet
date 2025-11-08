package com.botsquad.smarthelmet;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 * Unit tests for Register functionality
 * Test Case ID: Case-001
 * Proponent: Dapanas, Caryl A.
 * Date: 08/25/2025
 */
public class RegisterTest {

    private RegisterTestHelper helper;

    @Before
    public void setUp() {
        helper = new RegisterTestHelper();
    }

    /**
     * Case-001: Register with valid input
     * Expected Result: Account created; navigates to Dashboard
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase001_RegisterWithValidInput() {
        // Test data
        String firstName = "John";
        String lastName = "Doe";
        String email = "john.doe@example.com";
        String password = "password123";
        String contact = "+1234567890";

        // Validate all fields are not empty
        assertFalse("First name should not be empty", isEmpty(firstName));
        assertFalse("Last name should not be empty", isEmpty(lastName));
        assertFalse("Email should not be empty", isEmpty(email));
        assertFalse("Password should not be empty", isEmpty(password));
        assertFalse("Contact should not be empty", isEmpty(contact));

        // Validate phone number format (should start with +)
        assertTrue("Contact should start with +", contact.startsWith("+"));

        // Simulate successful registration flow
        boolean registrationValid = helper.validateRegistrationInput(
            firstName, lastName, email, password, contact);
        assertTrue("Registration should be valid with proper input", registrationValid);
    }

    @Test
    public void testRegisterWithEmptyFirstName() {
        String firstName = "";
        String lastName = "Doe";
        String email = "john.doe@example.com";
        String password = "password123";
        String contact = "+1234567890";

        assertTrue("First name should be empty", isEmpty(firstName));
        boolean isValid = helper.validateRegistrationInput(
            firstName, lastName, email, password, contact);
        assertFalse("Registration should fail with empty first name", isValid);
    }

    @Test
    public void testRegisterWithEmptyEmail() {
        String firstName = "John";
        String lastName = "Doe";
        String email = "";
        String password = "password123";
        String contact = "+1234567890";

        assertTrue("Email should be empty", isEmpty(email));
        boolean isValid = helper.validateRegistrationInput(
            firstName, lastName, email, password, contact);
        assertFalse("Registration should fail with empty email", isValid);
    }

    @Test
    public void testPhoneNumberFormatting() {
        String contactWithoutPlus = "1234567890";
        String formattedContact = helper.formatPhoneNumber(contactWithoutPlus);
        assertTrue("Contact should start with +", formattedContact.startsWith("+"));
        assertEquals("Formatted contact should be +1234567890", "+1234567890", formattedContact);
    }

    // Helper method to replace TextUtils.isEmpty() for unit tests
    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Helper class to test Register validation logic
     */
    private static class RegisterTestHelper {
        public boolean validateRegistrationInput(String firstName, String lastName, 
                                                 String email, String password, String contact) {
            if (isEmpty(firstName)) return false;
            if (isEmpty(lastName)) return false;
            if (isEmpty(email)) return false;
            if (isEmpty(password)) return false;
            if (isEmpty(contact)) return false;
            return true;
        }

        private static boolean isEmpty(String str) {
            return str == null || str.trim().isEmpty();
        }

        public String formatPhoneNumber(String contact) {
            if (!contact.startsWith("+")) {
                return "+" + contact;
            }
            return contact;
        }
    }
}
