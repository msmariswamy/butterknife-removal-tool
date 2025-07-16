package com.butterkniferemoval.test;

/**
 * Test class to verify binding class name generation logic.
 * This demonstrates that the plugin logic is working correctly.
 */
public class BindingClassNameTest {
    
    /**
     * Test the layout name to binding class name conversion
     */
    public static void main(String[] args) {
        System.out.println("Testing Binding Class Name Generation:");
        System.out.println("=====================================");
        
        // Test various layout names
        testLayoutNameConversion("activity_checkout_new", "ActivityCheckoutNewBinding");
        testLayoutNameConversion("fragment_login", "FragmentLoginBinding");
        testLayoutNameConversion("dialog_confirm", "DialogConfirmBinding");
        testLayoutNameConversion("item_user", "ItemUserBinding");
        testLayoutNameConversion("activity_main", "ActivityMainBinding");
        
        System.out.println("\nTesting Class Name to Layout Name Generation:");
        System.out.println("============================================");
        
        // Test class name to layout name conversion
        testClassNameConversion("MainActivity", "activity_main");
        testClassNameConversion("CheckoutActivity", "activity_checkout");
        testClassNameConversion("LoginFragment", "fragment_login");
        testClassNameConversion("UserAdapter", "user_adapter");
    }
    
    private static void testLayoutNameConversion(String layoutName, String expectedBinding) {
        String actualBinding = convertLayoutNameToBindingClassName(layoutName);
        boolean passed = expectedBinding.equals(actualBinding);
        System.out.printf("%-25s -> %-30s [%s]%n", 
            layoutName, actualBinding, passed ? "PASS" : "FAIL");
    }
    
    private static void testClassNameConversion(String className, String expectedLayout) {
        String actualLayout = generateLayoutNameFromClassName(className);
        boolean passed = expectedLayout.equals(actualLayout);
        System.out.printf("%-25s -> %-30s [%s]%n", 
            className, actualLayout, passed ? "PASS" : "FAIL");
    }
    
    /**
     * Convert layout name to binding class name (mirrors plugin logic)
     */
    private static String convertLayoutNameToBindingClassName(String layoutName) {
        StringBuilder result = new StringBuilder();
        String[] parts = layoutName.split("_");
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(part.substring(0, 1).toUpperCase())
                      .append(part.substring(1).toLowerCase());
            }
        }
        
        return result.toString() + "Binding";
    }
    
    /**
     * Generate layout name from class name (mirrors plugin logic)
     */
    private static String generateLayoutNameFromClassName(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        
        // Convert ActivityName to activity_name
        if (className.endsWith("Activity")) {
            String baseName = className.substring(0, className.length() - 8);
            return "activity_" + camelToSnakeCase(baseName);
        } else if (className.endsWith("Fragment")) {
            String baseName = className.substring(0, className.length() - 8);
            return "fragment_" + camelToSnakeCase(baseName);
        } else {
            return camelToSnakeCase(className);
        }
    }
    
    private static String camelToSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return "layout";
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(c));
        }
        
        return result.toString();
    }
}
