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
                // Generate appropriate ID based on field name and type
                String suggestedId = generateIdForFieldObject(fieldInfoObj, resourceId);
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
     * Recursively extracts IDs from XML tags.
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
        
        // Recursively check child tags
        XmlTag[] childTags = tag.getSubTags();
        for (XmlTag childTag : childTags) {
            extractIdsFromTag(childTag, existingIds);
        }
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
                        XmlTag targetTag = findBestMatchingTag(rootTag, fieldInfoObj);
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
     * Recursively finds tags matching the specified type.
     */
    private void findTagsByType(XmlTag tag, String targetType, List<XmlTag> results) {
        String tagName = tag.getName();
        
        // Check if tag type matches (handle both simple names and fully qualified names)
        if (matchesViewType(tagName, targetType)) {
            results.add(tag);
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
        } catch (Exception e) {
            // If comment creation fails, ignore silently
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
}
