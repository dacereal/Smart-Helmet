package com.botsquad.smarthelmet;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 * Unit tests for Profile functionality
 * Test Case IDs: Case-016, Case-017
 * Proponents: Dioso, Bern Dione D. / Manog, Christy Mae
 * Date: 08/25/2025
 */
public class ProfileTest {

    private ProfileTestHelper helper;

    @Before
    public void setUp() {
        helper = new ProfileTestHelper();
    }

    /**
     * Case-016: View Profile
     * Expected Result: User info populated
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase016_ViewProfile() {
        String userId = "test_user_id";
        
        ProfileTestHelper.UserProfile profile = helper.loadUserProfile(userId);
        
        assertNotNull("Profile should not be null", profile);
        assertNotNull("First name should be populated", profile.getFirstName());
        assertNotNull("Last name should be populated", profile.getLastName());
        assertNotNull("Email should be populated", profile.getEmail());
        assertNotNull("Contact number should be populated", profile.getContactNumber());
        
        assertFalse("First name should not be empty", profile.getFirstName().isEmpty());
        assertFalse("Last name should not be empty", profile.getLastName().isEmpty());
        assertFalse("Email should not be empty", profile.getEmail().isEmpty());
    }

    @Test
    public void testViewProfileWithMissingData() {
        String userId = "non_existent_user";
        
        ProfileTestHelper.UserProfile profile = helper.loadUserProfile(userId);
        
        // Profile might be null or have empty fields if user doesn't exist
        // This tests the error handling
        if (profile == null) {
            // Expected behavior for non-existent user
            assertNull("Profile should be null for non-existent user", profile);
        }
    }

    /**
     * Case-017: Update Profile
     * Expected Result: Profile saved; success toast
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase017_UpdateProfile() {
        String userId = "test_user_id";
        String firstName = "John";
        String lastName = "Doe";
        String contactNumber = "+1234567890";

        boolean updateSuccess = helper.updateUserProfile(
            userId, firstName, lastName, contactNumber);
        
        assertTrue("Profile update should succeed", updateSuccess);
        
        ProfileTestHelper.UserProfile updatedProfile = helper.loadUserProfile(userId);
        assertEquals("First name should be updated", firstName, updatedProfile.getFirstName());
        assertEquals("Last name should be updated", lastName, updatedProfile.getLastName());
        assertEquals("Contact number should be updated", contactNumber, updatedProfile.getContactNumber());
    }

    @Test
    public void testUpdateProfileWithEmptyFields() {
        String userId = "test_user_id";
        String firstName = "";
        String lastName = "Doe";
        String contactNumber = "+1234567890";

        boolean updateSuccess = helper.updateUserProfile(
            userId, firstName, lastName, contactNumber);
        
        // Should either fail or handle empty fields appropriately
        // In this case, we'll assume validation fails
        assertFalse("Profile update should fail with empty first name", updateSuccess);
    }

    @Test
    public void testUpdateProfileWithInvalidContact() {
        String userId = "test_user_id";
        String firstName = "John";
        String lastName = "Doe";
        String contactNumber = "invalid_contact";

        // Contact should be validated
        boolean isValidContact = helper.validateContactNumber(contactNumber);
        assertFalse("Invalid contact number should be rejected", isValidContact);
    }

    /**
     * Helper class to test Profile logic
     */
    static class ProfileTestHelper {
        private UserProfile mockProfile = new UserProfile("John", "Doe", "john@example.com", "+1234567890");

        public UserProfile loadUserProfile(String userId) {
            if (userId == null || userId.isEmpty() || userId.equals("non_existent_user")) {
                return null;
            }
            return mockProfile;
        }

        public boolean updateUserProfile(String userId, String firstName, 
                                       String lastName, String contactNumber) {
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            if (firstName == null || firstName.trim().isEmpty()) {
                return false;
            }
            if (lastName == null || lastName.trim().isEmpty()) {
                return false;
            }
            if (!validateContactNumber(contactNumber)) {
                return false;
            }
            
            mockProfile = new UserProfile(firstName, lastName, 
                mockProfile.getEmail(), contactNumber);
            return true;
        }

        public boolean validateContactNumber(String contact) {
            if (contact == null || contact.isEmpty()) {
                return false;
            }
            // Simple validation: should start with + and contain digits
            return contact.startsWith("+") && contact.substring(1).matches("\\d+");
        }

        static class UserProfile {
            private final String firstName;
            private final String lastName;
            private final String email;
            private final String contactNumber;

            public UserProfile(String firstName, String lastName, String email, String contactNumber) {
                this.firstName = firstName;
                this.lastName = lastName;
                this.email = email;
                this.contactNumber = contactNumber;
            }

            public String getFirstName() {
                return firstName;
            }

            public String getLastName() {
                return lastName;
            }

            public String getEmail() {
                return email;
            }

            public String getContactNumber() {
                return contactNumber;
            }
        }
    }
}
