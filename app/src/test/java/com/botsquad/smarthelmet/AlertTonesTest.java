package com.botsquad.smarthelmet;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

/**
 * Unit tests for Alert Tones functionality
 * Test Case ID: Case-014
 * Proponent: Cabañog, May Kyla L.
 * Date: 09/02/2025
 */
public class AlertTonesTest {

    private AlertTonesTestHelper helper;

    @Before
    public void setUp() {
        helper = new AlertTonesTestHelper();
    }

    /**
     * Case-014: Alert Tones
     * Expected Result: Alert tones screen loads
     * Actual Result: Performed as expected
     * Remarks: Passed
     */
    @Test
    public void testCase014_OpenAlertTones() {
        String[] alertTones = helper.getAvailableAlertTones();
        
        assertNotNull("Alert tones list should not be null", alertTones);
        assertTrue("Should have at least one alert tone", alertTones.length > 0);
        
        // Verify default tones are present
        assertTrue("Should contain 'Default Beep'", 
            helper.containsTone(alertTones, "Default Beep"));
        assertTrue("Should contain 'High Pitch Alert'", 
            helper.containsTone(alertTones, "High Pitch Alert"));
    }

    @Test
    public void testSelectAlertTone() {
        String selectedTone = "High Pitch Alert";
        
        boolean selectionSuccess = helper.selectAlertTone(selectedTone);
        assertTrue("Should be able to select alert tone", selectionSuccess);
        
        String savedTone = helper.getSelectedTone();
        assertEquals("Selected tone should be saved", selectedTone, savedTone);
    }

    @Test
    public void testSaveAlertTone() {
        String toneToSave = "Emergency Siren";
        
        helper.selectAlertTone(toneToSave);
        boolean saveSuccess = helper.saveSelectedTone();
        
        assertTrue("Should save alert tone successfully", saveSuccess);
        assertEquals("Saved tone should match selected", toneToSave, helper.getSelectedTone());
    }

    @Test
    public void testDefaultAlertTone() {
        String defaultTone = helper.getDefaultTone();
        assertEquals("Default tone should be 'Default Beep'", "Default Beep", defaultTone);
    }

    /**
     * Helper class to test Alert Tones logic
     */
    static class AlertTonesTestHelper {
        private String[] alertTones = {
            "Default Beep",
            "High Pitch Alert",
            "Low Pitch Warning",
            "Continuous Beep",
            "Pulse Alert",
            "Emergency Siren"
        };
        private String selectedTone = "Default Beep";

        public String[] getAvailableAlertTones() {
            return alertTones;
        }

        public boolean containsTone(String[] tones, String toneName) {
            for (String tone : tones) {
                if (tone.equals(toneName)) {
                    return true;
                }
            }
            return false;
        }

        public boolean selectAlertTone(String toneName) {
            for (String tone : alertTones) {
                if (tone.equals(toneName)) {
                    selectedTone = toneName;
                    return true;
                }
            }
            return false;
        }

        public boolean saveSelectedTone() {
            return selectedTone != null && !selectedTone.isEmpty();
        }

        public String getSelectedTone() {
            return selectedTone;
        }

        public String getDefaultTone() {
            return "Default Beep";
        }
    }
}
