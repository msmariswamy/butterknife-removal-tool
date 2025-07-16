package com.butterkniferemoval;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ide.highlighter.XmlFileType;

import java.util.*;

/**
 * Handles XML layout file operations for ButterKnife conversion.
 * Validates existing IDs and generates new ones when needed.
 */
public class XmlLayoutHandler {
    
    private static final String ANDROID_ID = "android:id";
    private static final String LAYOUT_PREFIX = "layout";
    private static final String ID_PREFIX = "@+id/";
    
    private final Project project;
    
    public XmlLayoutHandler(Project project) {
        this.project = project;
    }
    
    /**
     * Validates that all required resource IDs exist in the corresponding layout files.
     * Creates missing IDs with appropriate names based on view types.
     */
    public Map<String, String> validateAndEnsureIds(Map<String, ?> bindViewFields, String layoutName) {
        Map<String, String> idValidationResults = new HashMap<>();
        
        XmlFile layoutFile = findLayoutFile(layoutName);
        if (layoutFile == null) {
            // If layout file not found, assume IDs are valid (might be in different module)
            for (String resourceId : bindViewFields.keySet()) {
                idValidationResults.put(resourceId, "Layout file not found - assuming ID exists");
            }
            return idValidationResults;
        }
        
        Set<String> existingIds = extractExistingIds(layoutFile);
        Map<String, String> missingIds = new HashMap<>();
        
        // Check which IDs are missing
        for (Map.Entry<String, ?> entry : bindViewFields.entrySet()) {
            String resourceId = entry.getKey();
            Object fieldInfoObj = entry.getValue();
            
            if (existingIds.contains(resourceId)) {
                idValidationResults.put(resourceId, "ID exists");
            } else {
                // Generate appropriate ID - prefer to use the original resourceId as-is
                String suggestedId = resourceId;
                missingIds.put(resourceId, suggestedId);
                idValidationResults.put(resourceId, "ID missing - will be created as: " + suggestedId);
            }
        }
        
        // Create missing IDs in the layout file
        if (!missingIds.isEmpty()) {
            createMissingIds(layoutFile, missingIds, bindViewFields);
        }
        
        return idValidationResults;
    }
    
    /**
     * Finds the layout XML file based on the layout name.
     */
    private XmlFile findLayoutFile(String layoutName) {
        if (layoutName == null) {
            return null;
        }
        
        String fileName = layoutName + ".xml";
        Collection<VirtualFile> files = FileTypeIndex.getFiles(XmlFileType.INSTANCE, GlobalSearchScope.projectScope(project));
        
        for (VirtualFile virtualFile : files) {
            if (virtualFile.getName().equals(fileName)) {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile instanceof XmlFile xmlFile && isLayoutFile(xmlFile)) {
                    return xmlFile;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if the XML file is a layout file (contains layout as root or is in layout directory).
     */
    private boolean isLayoutFile(XmlFile xmlFile) {
        VirtualFile virtualFile = xmlFile.getVirtualFile();
        if (virtualFile != null) {
            VirtualFile parent = virtualFile.getParent();
            if (parent != null && parent.getName().startsWith(LAYOUT_PREFIX)) {
                return true;
            }
        }
        
        XmlTag rootTag = xmlFile.getRootTag();
        return rootTag != null && isLayoutTag(rootTag.getName());
    }
    
    /**
     * Checks if the tag name represents a layout element.
     */
    private boolean isLayoutTag(String tagName) {
        return tagName != null && (
            tagName.equals("LinearLayout") ||
            tagName.equals("RelativeLayout") ||
            tagName.equals("ConstraintLayout") ||
            tagName.equals("FrameLayout") ||
            tagName.equals("androidx.constraintlayout.widget.ConstraintLayout") ||
            tagName.contains("Layout") ||
            tagName.equals("ScrollView") ||
            tagName.equals("merge") ||
            tagName.equals("include")
        );
    }
    
    /**
     * Extracts all existing android:id values from the layout file.
     */
    private Set<String> extractExistingIds(XmlFile layoutFile) {
        Set<String> existingIds = new HashSet<>();
        XmlTag rootTag = layoutFile.getRootTag();
        
        if (rootTag != null) {
            extractIdsFromTag(rootTag, existingIds);
        }
        
        return existingIds;
    }
    
    /**
     * Recursively extracts IDs from XML tags, including IDs from included layouts.
     */
    private void extractIdsFromTag(XmlTag tag, Set<String> existingIds) {
        // Check for android:id attribute
        XmlAttribute idAttribute = tag.getAttribute(ANDROID_ID);
        if (idAttribute != null) {
            String idValue = idAttribute.getValue();
            if (idValue != null && idValue.startsWith(ID_PREFIX)) {
                String idName = idValue.substring(5); // Remove "@+id/"
                existingIds.add(idName);
            }
        }
        
        // Handle <include> tags - extract IDs from included layouts
        if ("include".equals(tag.getName())) {
            String includedLayoutName = extractIncludedLayoutName(tag);
            if (includedLayoutName != null) {
                XmlFile includedFile = findLayoutFile(includedLayoutName);
                if (includedFile != null) {
                    Set<String> includedIds = extractExistingIds(includedFile);
                    existingIds.addAll(includedIds);
                }
            }
            
            // Also check if the <include> tag itself has an android:id
            // This allows referencing the included layout as a whole via the include's ID
            XmlAttribute includeIdAttribute = tag.getAttribute(ANDROID_ID);
            if (includeIdAttribute != null) {
                String includeIdValue = includeIdAttribute.getValue();
                if (includeIdValue != null && includeIdValue.startsWith(ID_PREFIX)) {
                    String includeIdName = includeIdValue.substring(5); // Remove "@+id/"
                    existingIds.add(includeIdName);
                }
            }
        }
        
        // Recursively check child tags
        XmlTag[] childTags = tag.getSubTags();
        for (XmlTag childTag : childTags) {
            extractIdsFromTag(childTag, existingIds);
        }
    }
    
    /**
     * Extracts the layout name from an <include> tag.
     */
    private String extractIncludedLayoutName(XmlTag includeTag) {
        XmlAttribute layoutAttribute = includeTag.getAttribute("layout");
        if (layoutAttribute != null) {
            String layoutValue = layoutAttribute.getValue();
            if (layoutValue != null && layoutValue.startsWith("@layout/")) {
                return layoutValue.substring(8); // Remove "@layout/"
            }
        }
        return null;
    }
    
    /**
     * Generates an appropriate ID for a field based on its type and name.
     */
    private String generateIdForFieldObject(Object fieldInfoObj, String originalId) {
        // Use reflection to get field info from either FieldInfo class
        String fieldName = null;
        String fieldType = null;
        
        try {
            // Use reflection to access name and type fields
            java.lang.reflect.Field nameField = fieldInfoObj.getClass().getDeclaredField("name");
            java.lang.reflect.Field typeField = fieldInfoObj.getClass().getDeclaredField("type");
            fieldName = (String) nameField.get(fieldInfoObj);
            fieldType = (String) typeField.get(fieldInfoObj);
        } catch (Exception e) {
            // Fall back to using the original ID if reflection fails
            return originalId != null && isValidIdName(originalId) ? originalId : "view";
        }
        
        return generateIdForFieldInfo(fieldName, fieldType, originalId);
    }
    
    /**
     * Generates an appropriate ID for a field based on its type and name.
     */
    private String generateIdForFieldInfo(String fieldName, String fieldType, String originalId) {
        // If the original ID seems reasonable, keep it
        if (originalId != null && isValidIdName(originalId)) {
            return originalId;
        }
        
        // Generate based on field name and type
        if (fieldName == null) {
            fieldName = "view";
        }
        
        // Convert camelCase to snake_case
        String baseId = camelToSnakeCase(fieldName);
        
        // Add type prefix if not already present
        String typePrefix = getTypePrefixForView(fieldType);
        if (typePrefix != null && !baseId.startsWith(typePrefix)) {
            baseId = typePrefix + "_" + baseId;
        }
        
        return baseId;
    }
    
    /**
     * Converts camelCase to snake_case.
     */
    private String camelToSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return "view";
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
    
    /**
     * Gets appropriate prefix for view types.
     */
    private String getTypePrefixForView(String viewType) {
        if (viewType == null) return null;
        
        Map<String, String> typeToPrefix = createViewTypePrefixMap();
        viewType = viewType.toLowerCase();
        
        for (Map.Entry<String, String> entry : typeToPrefix.entrySet()) {
            if (viewType.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return "view"; // Default prefix
    }
    
    private Map<String, String> createViewTypePrefixMap() {
        Map<String, String> map = new HashMap<>();
        map.put("button", "btn");
        map.put("textview", "tv");
        map.put("edittext", "et");
        map.put("imageview", "iv");
        map.put("recyclerview", "rv");
        map.put("listview", "lv");
        map.put("scrollview", "sv");
        map.put("checkbox", "cb");
        map.put("radiobutton", "rb");
        map.put("switch", "sw");
        map.put("spinner", "sp");
        map.put("progressbar", "pb");
        map.put("seekbar", "sb");
        map.put("webview", "wv");
        map.put("cardview", "cv");
        map.put("toolbar", "tb");
        map.put(LAYOUT_PREFIX, LAYOUT_PREFIX);
        return map;
    }
    
    /**
     * Validates if an ID name follows Android conventions.
     */
    private boolean isValidIdName(String idName) {
        if (idName == null || idName.isEmpty()) {
            return false;
        }
        
        // Check if it contains only valid characters (letters, numbers, underscores)
        return idName.matches("^[a-z][a-z0-9_]*$");
    }
    
    /**
     * Creates missing IDs in the layout file by finding appropriate views and adding android:id attributes.
     * Also searches in included layouts for potential matches.
     */
    private void createMissingIds(XmlFile layoutFile, Map<String, String> missingIds, Map<String, ?> bindViewFields) {
        WriteCommandAction.runWriteCommandAction(project, "Add missing IDs", null, () -> {
            XmlTag rootTag = layoutFile.getRootTag();
            if (rootTag != null) {
                for (Map.Entry<String, String> entry : missingIds.entrySet()) {
                    String originalId = entry.getKey();
                    String suggestedId = entry.getValue();
                    Object fieldInfoObj = bindViewFields.get(originalId);
                    
                    if (fieldInfoObj != null) {
                        // First try to find in main layout
                        XmlTag targetTag = findBestMatchingTag(rootTag, fieldInfoObj);
                        
                        // If not found in main layout, search in included layouts
                        if (targetTag == null) {
                            targetTag = findBestMatchingTagInIncludes(rootTag, fieldInfoObj);
                        }
                        
                        if (targetTag != null) {
                            addIdToTag(targetTag, suggestedId);
                        } else {
                            // If no matching tag found, add a comment with suggestion
                            addIdSuggestionComment(rootTag, suggestedId, fieldInfoObj);
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Finds the best matching tag in included layouts.
     */
    private XmlTag findBestMatchingTagInIncludes(XmlTag rootTag, Object fieldInfoObj) {
        List<XmlTag> includeTags = new ArrayList<>();
        findIncludeTags(rootTag, includeTags);
        
        for (XmlTag includeTag : includeTags) {
            String includedLayoutName = extractIncludedLayoutName(includeTag);
            if (includedLayoutName != null) {
                XmlFile includedFile = findLayoutFile(includedLayoutName);
                if (includedFile != null) {
                    XmlTag includedRootTag = includedFile.getRootTag();
                    if (includedRootTag != null) {
                        XmlTag targetTag = findBestMatchingTag(includedRootTag, fieldInfoObj);
                        if (targetTag != null) {
                            return targetTag;
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Finds all <include> tags in the layout.
     */
    private void findIncludeTags(XmlTag tag, List<XmlTag> results) {
        if ("include".equals(tag.getName())) {
            results.add(tag);
        }
        
        XmlTag[] childTags = tag.getSubTags();
        for (XmlTag childTag : childTags) {
            findIncludeTags(childTag, results);
        }
    }
    
    /**
     * Finds the best matching XML tag for a field based on type and name.
     */
    private XmlTag findBestMatchingTag(XmlTag rootTag, Object fieldInfoObj) {
        List<XmlTag> candidateTags = new ArrayList<>();
        String fieldType = extractFieldType(fieldInfoObj);
        findTagsByType(rootTag, fieldType, candidateTags);
        
        // First, try to find tags without IDs that match the type
        for (XmlTag tag : candidateTags) {
            if (tag.getAttribute(ANDROID_ID) == null) {
                return tag; // Return first unidentified tag of matching type
            }
        }
        
        // If all tags of this type have IDs, return null (don't modify existing tags)
        return null;
    }
    
    /**
     * Extracts field type from fieldInfo object using reflection.
     */
    private String extractFieldType(Object fieldInfoObj) {
        try {
            java.lang.reflect.Field typeField = fieldInfoObj.getClass().getDeclaredField("type");
            return (String) typeField.get(fieldInfoObj);
        } catch (Exception e) {
            return "View"; // Default fallback
        }
    }
    
    /**
     * Extracts field name from fieldInfo object using reflection.
     */
    private String extractFieldName(Object fieldInfoObj) {
        try {
            java.lang.reflect.Field nameField = fieldInfoObj.getClass().getDeclaredField("name");
            return (String) nameField.get(fieldInfoObj);
        } catch (Exception e) {
            return "view"; // Default fallback
        }
    }
    
    /**
     * Recursively finds tags matching the specified type, including in included layouts.
     */
    private void findTagsByType(XmlTag tag, String targetType, List<XmlTag> results) {
        String tagName = tag.getName();
        
        // Check if tag type matches (handle both simple names and fully qualified names)
        if (matchesViewType(tagName, targetType)) {
            results.add(tag);
        }
        
        // Handle <include> tags - search in included layouts
        if ("include".equals(tagName)) {
            String includedLayoutName = extractIncludedLayoutName(tag);
            if (includedLayoutName != null) {
                XmlFile includedFile = findLayoutFile(includedLayoutName);
                if (includedFile != null) {
                    XmlTag includedRootTag = includedFile.getRootTag();
                    if (includedRootTag != null) {
                        findTagsByType(includedRootTag, targetType, results);
                    }
                }
            }
        }
        
        // Recursively search child tags
        XmlTag[] childTags = tag.getSubTags();
        for (XmlTag childTag : childTags) {
            findTagsByType(childTag, targetType, results);
        }
    }
    
    /**
     * Checks if XML tag name matches the Java view type.
     */
    private boolean matchesViewType(String tagName, String javaType) {
        if (tagName == null || javaType == null) {
            return false;
        }
        
        // Remove package names for comparison
        String simpleTagName = tagName.substring(tagName.lastIndexOf('.') + 1);
        String simpleJavaType = javaType.substring(javaType.lastIndexOf('.') + 1);
        
        return simpleTagName.equalsIgnoreCase(simpleJavaType);
    }
    
    /**
     * Adds an android:id attribute to the specified tag.
     */
    private void addIdToTag(XmlTag tag, String idName) {
        tag.setAttribute(ANDROID_ID, ID_PREFIX + idName);
        System.out.println("Added ID '" + idName + "' to " + tag.getName() + " tag");
    }
    
    /**
     * Adds a comment suggesting where to add the missing ID.
     */
    private void addIdSuggestionComment(XmlTag rootTag, String suggestedId, Object fieldInfoObj) {
        PsiElementFactory factory = PsiElementFactory.getInstance(project);
        
        String fieldType = extractFieldType(fieldInfoObj);
        String fieldName = extractFieldName(fieldInfoObj);
        
        String commentText = String.format(
            " TODO: Add android:id=\"@+id/%s\" to a %s view for field '%s' ",
            suggestedId, fieldType, fieldName
        );
        
        try {
            PsiComment comment = factory.createCommentFromText("<!--" + commentText + "-->", null);
            rootTag.addAfter(comment, rootTag.getFirstChild());
            System.out.println("Added comment for missing ID: " + suggestedId + " (type: " + fieldType + ")");
        } catch (Exception e) {
            System.err.println("Failed to add comment for missing ID: " + suggestedId + " - " + e.getMessage());
        }
    }
    
    /**
     * Extracts layout name from a setContentView statement or class name.
     */
    public static String extractLayoutNameFromSetContentView(String setContentViewStatement) {
        if (setContentViewStatement != null && setContentViewStatement.contains("R.layout.")) {
            int startIndex = setContentViewStatement.indexOf("R.layout.") + 9;
            int endIndex = setContentViewStatement.indexOf(")", startIndex);
            if (endIndex > startIndex) {
                return setContentViewStatement.substring(startIndex, endIndex);
            }
        }
        return null;
    }
    
    /**
     * Extracts layout name from class name (e.g., "MainActivity" -> "activity_main").
     */
    public static String generateLayoutNameFromClassName(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        
        // Convert ActivityName to activity_name
        if (className.endsWith("Activity")) {
            String baseName = className.substring(0, className.length() - 8);
            return "activity_" + camelToSnakeCaseStatic(baseName);
        } else if (className.endsWith("Fragment")) {
            String baseName = className.substring(0, className.length() - 8);
            return "fragment_" + camelToSnakeCaseStatic(baseName);
        } else {
            return camelToSnakeCaseStatic(className);
        }
    }
    
    private static String camelToSnakeCaseStatic(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return LAYOUT_PREFIX;
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
    
    /**
     * Checks if a resource ID corresponds to an include tag.
     */
    public boolean isIncludeTagId(String resourceId, String layoutName) {
        System.out.println("DEBUG: isIncludeTagId called with resourceId='" + resourceId + "', layoutName='" + layoutName + "'");
        XmlFile layoutFile = findLayoutFile(layoutName);
        if (layoutFile == null) {
            System.out.println("DEBUG: Layout file not found for: " + layoutName);
            return false;
        }
        
        XmlTag rootTag = layoutFile.getRootTag();
        if (rootTag == null) {
            System.out.println("DEBUG: Root tag is null for layout: " + layoutName);
            return false;
        }
        
        XmlTag includeTag = findIncludeTagById(rootTag, resourceId);
        boolean result = includeTag != null;
        System.out.println("DEBUG: findIncludeTagById result: " + result + (includeTag != null ? " (found tag: " + includeTag.getName() + ")" : ""));
        return result;
    }
    
    /**
     * Finds an include tag by its ID.
     */
    private XmlTag findIncludeTagById(XmlTag tag, String resourceId) {
        if ("include".equals(tag.getName())) {
            XmlAttribute idAttribute = tag.getAttribute(ANDROID_ID);
            if (idAttribute != null) {
                String idValue = idAttribute.getValue();
                if (idValue != null && idValue.equals(ID_PREFIX + resourceId)) {
                    return tag;
                }
            }
        }
        
        // Recursively search child tags
        XmlTag[] childTags = tag.getSubTags();
        for (XmlTag childTag : childTags) {
            XmlTag found = findIncludeTagById(childTag, resourceId);
            if (found != null) {
                return found;
            }
        }
        
        return null;
    }
    
    /**
     * Adds click handling to the root view of an included layout.
     */
    public void addClickHandlingToIncludedLayout(String includeId, String mainLayoutName, String methodCall) {
        XmlFile mainLayoutFile = findLayoutFile(mainLayoutName);
        if (mainLayoutFile == null) {
            return;
        }
        
        XmlTag rootTag = mainLayoutFile.getRootTag();
        if (rootTag == null) {
            return;
        }
        
        XmlTag includeTag = findIncludeTagById(rootTag, includeId);
        if (includeTag == null) {
            return;
        }
        
        String includedLayoutName = extractIncludedLayoutName(includeTag);
        if (includedLayoutName == null) {
            return;
        }
        
        XmlFile includedLayoutFile = findLayoutFile(includedLayoutName);
        if (includedLayoutFile == null) {
            return;
        }
        
        WriteCommandAction.runWriteCommandAction(project, "Add click handling to included layout", null, () -> {
            XmlTag includedRootTag = includedLayoutFile.getRootTag();
            if (includedRootTag != null) {
                // Add android:id to the root tag if it doesn't have one
                XmlAttribute rootIdAttribute = includedRootTag.getAttribute(ANDROID_ID);
                if (rootIdAttribute == null) {
                    // Generate a unique ID for the root tag based on the include ID
                    String rootId = includeId + "Root";
                    addIdToTag(includedRootTag, rootId);
                    System.out.println("Added ID '" + rootId + "' to root tag of included layout: " + includedLayoutName);
                }
                
                // Ensure the root tag is clickable
                ensureClickableAttributes(includedRootTag);
                
                System.out.println("Enhanced included layout '" + includedLayoutName + "' for click handling");
            }
        });
    }
    
    /**
     * Ensures the tag has the necessary attributes for click handling.
     */
    private void ensureClickableAttributes(XmlTag tag) {
        // Add clickable="true" if not present
        if (tag.getAttribute("android:clickable") == null) {
            tag.setAttribute("android:clickable", "true");
        }
        
        // Add focusable="true" if not present (for accessibility)
        if (tag.getAttribute("android:focusable") == null) {
            tag.setAttribute("android:focusable", "true");
        }
    }
    
    /**
     * Gets the included layout name for a given include tag ID.
     */
    public String getIncludedLayoutName(String includeId, String mainLayoutName) {
        XmlFile mainLayoutFile = findLayoutFile(mainLayoutName);
        if (mainLayoutFile == null) {
            return null;
        }
        
        XmlTag rootTag = mainLayoutFile.getRootTag();
        if (rootTag == null) {
            return null;
        }
        
        XmlTag includeTag = findIncludeTagById(rootTag, includeId);
        if (includeTag == null) {
            return null;
        }
        
        return extractIncludedLayoutName(includeTag);
    }
    
    /**
     * Finds the main clickable element in an included layout and ensures it has an ID.
     * If no ID exists, creates appropriate IDs for internal elements.
     */
    public String findClickableElementInIncludedLayout(String includedLayoutName) {
        if (includedLayoutName == null) {
            return null;
        }
        
        XmlFile includedLayoutFile = findLayoutFile(includedLayoutName);
        if (includedLayoutFile == null) {
            return null;
        }
        
        XmlTag rootTag = includedLayoutFile.getRootTag();
        if (rootTag == null) {
            return null;
        }
        
        // First check if the root tag already has an ID
        XmlAttribute rootIdAttribute = rootTag.getAttribute(ANDROID_ID);
        if (rootIdAttribute != null) {
            String idValue = rootIdAttribute.getValue();
            if (idValue != null && idValue.startsWith(ID_PREFIX)) {
                // Root tag has ID, ensure it's clickable and return it
                WriteCommandAction.runWriteCommandAction(project, "Ensure root tag is clickable", null, () -> {
                    ensureClickableAttributes(rootTag);
                });
                return idValue.substring(5); // Remove "@+id/"
            }
        }
        
        // Root tag doesn't have ID, let's add one and make it clickable
        WriteCommandAction.runWriteCommandAction(project, "Add clickable ID to included layout", null, () -> {
            // Generate a meaningful ID based on the layout name
            String rootId = generateRootIdFromLayoutName(includedLayoutName);
            addIdToTag(rootTag, rootId);
            
            // Ensure the root tag is clickable
            ensureClickableAttributes(rootTag);
            
            // Also add IDs to any internal elements that don't have them
            addIdsToInternalElements(rootTag, includedLayoutName);
        });
        
        // Return the ID we just added
        XmlAttribute idAttribute = rootTag.getAttribute(ANDROID_ID);
        if (idAttribute != null) {
            String idValue = idAttribute.getValue();
            if (idValue != null && idValue.startsWith(ID_PREFIX)) {
                return idValue.substring(5); // Remove "@+id/"
            }
        }
        
        return null;
    }
    
    /**
     * Generates a meaningful root ID from the layout name.
     */
    private String generateRootIdFromLayoutName(String layoutName) {
        // Convert layout name to camelCase and add "Root"
        // Example: buy_with_googlepay_button -> buyWithGooglepayButtonRoot
        String camelCase = convertLayoutNameToCamelCase(layoutName);
        return camelCase + "Root";
    }
    
    /**
     * Converts layout name to camelCase.
     */
    private String convertLayoutNameToCamelCase(String layoutName) {
        if (layoutName == null || layoutName.isEmpty()) {
            return "layout";
        }
        
        String[] parts = layoutName.split("_");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            if (i == 0) {
                result.append(parts[i].toLowerCase());
            } else {
                if (!parts[i].isEmpty()) {
                    result.append(parts[i].substring(0, 1).toUpperCase())
                          .append(parts[i].substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * Adds IDs to internal elements that don't have them.
     */
    private void addIdsToInternalElements(XmlTag rootTag, String layoutName) {
        addIdsToElementsRecursively(rootTag, layoutName, new HashSet<>());
    }
    
    /**
     * Recursively adds IDs to elements that don't have them.
     */
    private void addIdsToElementsRecursively(XmlTag tag, String layoutName, Set<String> usedIds) {
        XmlTag[] childTags = tag.getSubTags();
        int elementCount = 0;
        
        for (XmlTag childTag : childTags) {
            // Skip include tags - they have their own handling
            if ("include".equals(childTag.getName())) {
                continue;
            }
            
            // Check if this element already has an ID
            XmlAttribute idAttribute = childTag.getAttribute(ANDROID_ID);
            if (idAttribute == null) {
                // Generate a unique ID for this element
                String elementId = generateElementId(childTag, layoutName, elementCount, usedIds);
                if (elementId != null) {
                    addIdToTag(childTag, elementId);
                    usedIds.add(elementId);
                    System.out.println("Added ID '" + elementId + "' to " + childTag.getName() + " in layout: " + layoutName);
                }
            } else {
                // Track existing IDs to avoid duplicates
                String existingId = idAttribute.getValue();
                if (existingId != null && existingId.startsWith(ID_PREFIX)) {
                    usedIds.add(existingId.substring(5));
                }
            }
            
            // Recursively process child elements
            addIdsToElementsRecursively(childTag, layoutName, usedIds);
            elementCount++;
        }
    }
    
    /**
     * Generates a unique ID for an element based on its type and context.
     */
    private String generateElementId(XmlTag tag, String layoutName, int elementCount, Set<String> usedIds) {
        String tagName = tag.getName();
        String baseLayoutName = layoutName.replace("_", "");
        
        // Generate ID based on element type
        String baseId;
        if (tagName.contains("Button")) {
            baseId = baseLayoutName + "Button";
        } else if (tagName.contains("TextView")) {
            baseId = baseLayoutName + "Text";
        } else if (tagName.contains("ImageView")) {
            baseId = baseLayoutName + "Image";
        } else if (tagName.contains("LinearLayout")) {
            baseId = baseLayoutName + "Linear";
        } else if (tagName.contains("RelativeLayout")) {
            baseId = baseLayoutName + "Relative";
        } else if (tagName.contains("Layout")) {
            baseId = baseLayoutName + "Layout";
        } else {
            baseId = baseLayoutName + "View";
        }
        
        // Make sure the ID is unique
        String finalId = baseId;
        int counter = 1;
        while (usedIds.contains(finalId)) {
            finalId = baseId + counter;
            counter++;
        }
        
        return finalId;
    }
    
    /**
     * Checks if a tag is clickable (has clickable="true" or focusable="true").
     */
    private boolean isClickable(XmlTag tag) {
        XmlAttribute clickableAttr = tag.getAttribute("android:clickable");
        XmlAttribute focusableAttr = tag.getAttribute("android:focusable");
        
        return (clickableAttr != null && "true".equals(clickableAttr.getValue())) ||
               (focusableAttr != null && "true".equals(focusableAttr.getValue()));
    }
    
    /**
     * Finds or creates a root element ID for an included layout.
     */
    public String findOrCreateRootElementId(String layoutName) {
        System.out.println("DEBUG: findOrCreateRootElementId called for layout: " + layoutName);
        XmlFile layoutFile = findLayoutFile(layoutName);
        if (layoutFile == null) {
            System.out.println("DEBUG: Layout file not found for: " + layoutName);
            return null;
        }
        System.out.println("DEBUG: Found layout file: " + layoutFile.getName());
        
        XmlTag rootTag = layoutFile.getRootTag();
        if (rootTag == null) {
            return null;
        }
        
        // Check if root tag already has an ID
        XmlAttribute idAttribute = rootTag.getAttribute(ANDROID_ID);
        if (idAttribute != null) {
            String idValue = idAttribute.getValue();
            if (idValue != null && idValue.startsWith(ID_PREFIX)) {
                return idValue.substring(5); // Remove "@+id/"
            }
        }
        
        // Root tag doesn't have an ID, create one
        System.out.println("DEBUG: Root tag has no ID, creating one...");
        WriteCommandAction.runWriteCommandAction(project, "Add root ID to included layout", null, () -> {
            try {
                String rootId = generateRootIdFromLayoutName(layoutName);
                System.out.println("DEBUG: Generated root ID: " + rootId);
                addIdToTag(rootTag, rootId);
                System.out.println("Created root ID '" + rootId + "' for included layout: " + layoutName);
                
                // Also ensure all internal elements have IDs
                addIdsToInternalElements(rootTag, layoutName);
                System.out.println("DEBUG: Finished adding IDs to internal elements");
            } catch (Exception e) {
                System.err.println("ERROR: Failed to add root ID: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // Return the newly created ID
        return generateRootIdFromLayoutName(layoutName);
    }
}
