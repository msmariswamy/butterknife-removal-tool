package com.butterkniferemoval;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

/**
 * Enhanced ButterKnife converter that validates XML IDs and generates view binding.
 */
public class EnhancedButterknifeConverter {
    
    private static final String BUTTERKNIFE_BIND_VIEW = "butterknife.BindView";
    private static final String R_ID_PREFIX = "R.id.";
    private static final String BINDING_PREFIX = "binding.";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String BINDING_SUFFIX = "Binding";
    
    private EnhancedButterknifeConverter() {
        // Utility class
    }

    public static void convertFile(Project project, PsiJavaFile javaFile) {
        WriteCommandAction.runWriteCommandAction(project, () -> convertButterknifeAnnotations(project, javaFile));
    }

    private static void convertButterknifeAnnotations(Project project, PsiJavaFile javaFile) {
        PsiClass[] classes = javaFile.getClasses();
        if (classes.length == 0) return;

        PsiClass mainClass = classes[0];
        Map<String, FieldInfo> bindViewFields = new HashMap<>();
        Map<String, String> onClickMethods = new HashMap<>();
        
        collectBindViewFields(mainClass, bindViewFields);
        collectOnClickMethods(mainClass, onClickMethods);
        
        if (bindViewFields.isEmpty() && onClickMethods.isEmpty()) {
            return;
        }
        
        // Validate and ensure XML IDs exist
        validateXmlIds(project, mainClass, bindViewFields);
        
        // Perform conversion
        removeAnnotations(mainClass);
        addViewBindingSupport(project, mainClass);
        replaceFieldReferences(project, mainClass, bindViewFields);
        removeFieldDeclarations(mainClass, bindViewFields);
        addOnClickListeners(project, mainClass, onClickMethods, bindViewFields);
        removeButterknifeImports(javaFile);
        removeButterknifeBindCalls(mainClass);
    }
    
    /**
     * Validates that all required resource IDs exist in XML layout files.
     * Creates missing IDs when needed.
     */
    private static void validateXmlIds(Project project, PsiClass mainClass, Map<String, FieldInfo> bindViewFields) {
        if (bindViewFields.isEmpty()) {
            return;
        }
        
        // Extract layout name from onCreate method or class name
        String layoutName = extractLayoutNameFromClass(mainClass);
        
        // Use XmlLayoutHandler to validate and ensure IDs exist
        XmlLayoutHandler xmlHandler = new XmlLayoutHandler(project);
        Map<String, String> validationResults = xmlHandler.validateAndEnsureIds(bindViewFields, layoutName);
        
        // Log validation results and show notification for missing IDs
        for (Map.Entry<String, String> entry : validationResults.entrySet()) {
            String resourceId = entry.getKey();
            String result = entry.getValue();
            // In a production implementation, this would use proper logging
            System.out.println("Resource ID '" + resourceId + "': " + result);
        }
    }
    
    /**
     * Extracts layout name from setContentView call or generates from class name.
     */
    private static String extractLayoutNameFromClass(PsiClass psiClass) {
        // First try to find layout name from onCreate method
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod != null) {
            PsiCodeBlock codeBlock = onCreateMethod.getBody();
            if (codeBlock != null) {
                PsiStatement[] statements = codeBlock.getStatements();
                for (PsiStatement statement : statements) {
                    if (statement.getText().contains(SET_CONTENT_VIEW)) {
                        String layoutName = XmlLayoutHandler.extractLayoutNameFromSetContentView(statement.getText());
                        if (layoutName != null) {
                            return layoutName;
                        }
                    }
                }
            }
        }
        
        // Fall back to generating layout name from class name
        return XmlLayoutHandler.generateLayoutNameFromClassName(psiClass.getName());
    }

    private static void collectBindViewFields(PsiClass psiClass, Map<String, FieldInfo> bindViewFields) {
        PsiField[] fields = psiClass.getFields();
        for (PsiField field : fields) {
            PsiAnnotation bindViewAnnotation = field.getAnnotation(BUTTERKNIFE_BIND_VIEW);
            if (bindViewAnnotation != null) {
                String resourceId = extractResourceId(bindViewAnnotation);
                if (resourceId != null) {
                    bindViewFields.put(resourceId, new FieldInfo(field.getName(), field.getType().getCanonicalText()));
                }
            }
        }
    }

    private static void collectOnClickMethods(PsiClass psiClass, Map<String, String> onClickMethods) {
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            PsiAnnotation onClickAnnotation = method.getAnnotation("butterknife.OnClick");
            if (onClickAnnotation != null) {
                List<String> resourceIds = extractResourceIds(onClickAnnotation);
                if (!resourceIds.isEmpty()) {
                    boolean hasParameters = method.getParameterList().getParametersCount() > 0;
                    String methodCall = hasParameters ? method.getName() + "(v)" : method.getName() + "()";
                    
                    for (String resourceId : resourceIds) {
                        onClickMethods.put(resourceId, methodCall);
                    }
                }
            }
        }
    }

    private static String extractResourceId(PsiAnnotation annotation) {
        List<String> ids = extractResourceIds(annotation);
        return ids.isEmpty() ? null : ids.get(0);
    }
    
    private static List<String> extractResourceIds(PsiAnnotation annotation) {
        List<String> resourceIds = new ArrayList<>();
        PsiAnnotationParameterList parameterList = annotation.getParameterList();
        PsiNameValuePair[] attributes = parameterList.getAttributes();
        
        for (PsiNameValuePair attribute : attributes) {
            PsiAnnotationMemberValue value = attribute.getValue();
            if (value != null) {
                String text = value.getText();
                
                if (text.startsWith("{") && text.endsWith("}")) {
                    String content = text.substring(1, text.length() - 1);
                    String[] parts = content.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (trimmed.contains(R_ID_PREFIX)) {
                            String id = trimmed.substring(trimmed.indexOf(R_ID_PREFIX) + 5);
                            resourceIds.add(id);
                        }
                    }
                } else if (text.contains(R_ID_PREFIX)) {
                    String id = text.substring(text.indexOf(R_ID_PREFIX) + 5);
                    resourceIds.add(id);
                }
            }
        }
        
        return resourceIds;
    }

    private static void removeAnnotations(PsiClass psiClass) {
        for (PsiField field : psiClass.getFields()) {
            PsiAnnotation annotation = field.getAnnotation(BUTTERKNIFE_BIND_VIEW);
            if (annotation != null) {
                annotation.delete();
            }
        }
        
        for (PsiMethod method : psiClass.getMethods()) {
            PsiAnnotation onClickAnnotation = method.getAnnotation("butterknife.OnClick");
            if (onClickAnnotation != null) {
                onClickAnnotation.delete();
            }
        }
    }

    private static void addOnClickListeners(Project project, PsiClass psiClass, Map<String, String> onClickMethods, Map<String, FieldInfo> bindViewFields) {
        if (onClickMethods.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement lastBindingStatement = findLastBindingStatement(codeBlock);
        if (lastBindingStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onClickMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            
            String bindingFieldName = convertResourceIdToBindingFieldName(resourceId);
            
            // Check if this is an include tag and use the root view reference
            String layoutName = extractLayoutNameFromClass(psiClass);
            XmlLayoutHandler xmlHandler = new XmlLayoutHandler(project);
            System.out.println("DEBUG: Checking resourceId '" + resourceId + "' in layout '" + layoutName + "'");
            boolean isInclude = xmlHandler.isIncludeTagId(resourceId, layoutName);
            System.out.println("DEBUG: isIncludeTagId result: " + isInclude);
            if (isInclude) {
                // For include tags, find the main clickable element inside the included layout
                String includedLayoutName = xmlHandler.getIncludedLayoutName(resourceId, layoutName);
                String clickableElementId = xmlHandler.findClickableElementInIncludedLayout(includedLayoutName);
                
                if (clickableElementId != null) {
                    // Access the specific clickable element inside the included layout
                    String clickableFieldName = convertResourceIdToBindingFieldName(clickableElementId);
                    String listenerStatement = BINDING_PREFIX + bindingFieldName + "." + clickableFieldName + ".setOnClickListener(v -> " + methodCall + ");";
                    PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
                    codeBlock.addAfter(statement, lastBindingStatement);
                } else {
                    // Fallback to getRoot() if no specific clickable element found
                    String listenerStatement = BINDING_PREFIX + bindingFieldName + ".getRoot().setOnClickListener(v -> " + methodCall + ");";
                    PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
                    codeBlock.addAfter(statement, lastBindingStatement);
                }
            } else {
                // For regular views, check if this could be an include tag by checking field type
                FieldInfo fieldInfo = bindViewFields.get(resourceId);
                if (fieldInfo != null && isLikelyIncludeTag(fieldInfo)) {
                    // This is likely an include tag, find the root element ID in the included layout
                    String includedLayoutRootId = findIncludedLayoutRootId(project, psiClass, resourceId);
                    if (includedLayoutRootId != null) {
                        // Use the actual root element ID from the included layout
                        String rootFieldName = convertResourceIdToBindingFieldName(includedLayoutRootId);
                        String listenerStatement = BINDING_PREFIX + bindingFieldName + "." + rootFieldName + ".setOnClickListener(v -> " + methodCall + ");";
                        PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
                        codeBlock.addAfter(statement, lastBindingStatement);
                    } else {
                        // Fallback to .getRoot() if root ID not found
                        String listenerStatement = BINDING_PREFIX + bindingFieldName + ".getRoot().setOnClickListener(v -> " + methodCall + ");";
                        PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
                        codeBlock.addAfter(statement, lastBindingStatement);
                    }
                } else {
                    // Regular view, use direct binding field
                    String listenerStatement = BINDING_PREFIX + bindingFieldName + ".setOnClickListener(v -> " + methodCall + ");";
                    PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
                    codeBlock.addAfter(statement, lastBindingStatement);
                }
            }
            
            // Additionally, check if this is an include tag and add click handling to the included layout
            handleIncludeClickEvent(project, psiClass, resourceId, methodCall);
        }
    }
    
    /**
     * Checks if a field is likely an include tag based on heuristics.
     * Include tags often use layout types like RelativeLayout, LinearLayout, etc.
     */
    private static boolean isLikelyIncludeTag(FieldInfo fieldInfo) {
        String fieldType = fieldInfo.type;
        if (fieldType == null) return false;
        
        // Check if the field type suggests it's likely an include tag
        // Include tags are often declared as layout types
        return fieldType.contains("RelativeLayout") || 
               fieldType.contains("LinearLayout") || 
               fieldType.contains("ConstraintLayout") || 
               fieldType.contains("FrameLayout") || 
               fieldType.contains("Layout") ||
               fieldType.contains("ViewGroup");
    }
    
    /**
     * Finds the root element ID in an included layout.
     * This method will also ensure the root element has an ID if it doesn't have one.
     */
    private static String findIncludedLayoutRootId(Project project, PsiClass psiClass, String includeResourceId) {
        try {
            String layoutName = extractLayoutNameFromClass(psiClass);
            XmlLayoutHandler xmlHandler = new XmlLayoutHandler(project);
            
            // First try to get the included layout name from the include tag
            String includedLayoutName = xmlHandler.getIncludedLayoutName(includeResourceId, layoutName);
            if (includedLayoutName != null) {
                // Find or create the root element ID in the included layout
                String rootId = xmlHandler.findOrCreateRootElementId(includedLayoutName);
                return rootId;
            }
            
            // If we can't find the included layout, try to infer from the include ID
            // This is a fallback mechanism
            return inferIncludedLayoutRootId(includeResourceId);
        } catch (Exception e) {
            System.err.println("Error finding included layout root ID: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Infers the root element ID from the include tag ID as a fallback.
     */
    private static String inferIncludedLayoutRootId(String includeResourceId) {
        if (includeResourceId == null) return null;
        
        // Generate a likely root ID based on the include ID
        // For example: googlePayButton -> googlePayButtonRoot
        return includeResourceId + "Root";
    }
    
    /**
     * Handles click events for include tags by adding click handling to the included layout's root view.
     */
    private static void handleIncludeClickEvent(Project project, PsiClass psiClass, String resourceId, String methodCall) {
        // Extract layout name from the class
        String layoutName = extractLayoutNameFromClass(psiClass);
        
        // Check if this resourceId corresponds to an include tag
        XmlLayoutHandler xmlHandler = new XmlLayoutHandler(project);
        if (xmlHandler.isIncludeTagId(resourceId, layoutName)) {
            // Add click handling to the included layout's root view
            xmlHandler.addClickHandlingToIncludedLayout(resourceId, layoutName, methodCall);
        }
    }

    private static PsiMethod findOnCreateMethod(PsiClass psiClass) {
        PsiMethod[] methods = psiClass.findMethodsByName("onCreate", false);
        for (PsiMethod method : methods) {
            if (method.getParameterList().getParametersCount() == 1) {
                return method;
            }
        }
        return null;
    }

    private static PsiStatement findLastBindingStatement(PsiCodeBlock codeBlock) {
        PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement : statements) {
            if (statement.getText().contains("binding =") && statement.getText().contains("inflate")) {
                return statement;
            }
        }
        return null;
    }

    private static String convertResourceIdToBindingFieldName(String resourceId) {
        // Android View Binding preserves camelCase IDs as-is
        // and converts snake_case IDs to camelCase
        if (resourceId.contains("_")) {
            // Convert snake_case to camelCase
            StringBuilder result = new StringBuilder();
            String[] parts = resourceId.split("_");
            
            for (int i = 0; i < parts.length; i++) {
                if (i == 0) {
                    result.append(parts[i].toLowerCase());
                } else {
                    result.append(parts[i].substring(0, 1).toUpperCase())
                          .append(parts[i].substring(1).toLowerCase());
                }
            }
            
            return result.toString();
        } else {
            // If no underscores, preserve the original casing
            return resourceId;
        }
    }

    private static void addViewBindingSupport(Project project, PsiClass psiClass) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        String bindingClassName = extractBindingClassNameFromOnCreate(psiClass);
        
        if (bindingClassName == null) {
            // Use layout name to generate binding class name (correct Android way)
            String layoutName = extractLayoutNameFromClass(psiClass);
            bindingClassName = generateBindingClassNameFromLayout(layoutName);
        }
        
        // Add binding field declaration
        String bindingFieldDeclaration = "private " + bindingClassName + " binding;";
        PsiField bindingField = factory.createFieldFromText(bindingFieldDeclaration, null);
        psiClass.add(bindingField);
        
        updateOnCreateForViewBinding(project, psiClass, bindingClassName);
        addOnDestroyMethod(project, psiClass);
    }
    
    private static String extractBindingClassNameFromOnCreate(PsiClass psiClass) {
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return null;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return null;
        
        PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement : statements) {
            if (statement.getText().contains(SET_CONTENT_VIEW)) {
                return extractBindingClassNameFromSetContentView(statement.getText());
            }
        }
        return null;
    }
    
    /**
     * Generates binding class name from layout file name (Android View Binding standard).
     * Examples:
     * - activity_checkout_new.xml → ActivityCheckoutNewBinding
     * - checkout_activity_new.xml → CheckoutActivityNewBinding  
     * - fragment_profile.xml → FragmentProfileBinding
     */
    private static String generateBindingClassNameFromLayout(String layoutName) {
        if (layoutName == null || layoutName.isEmpty()) {
            return "ActivityMainBinding"; // Default fallback
        }
        
        // Convert layout name to binding class name
        // Example: activity_checkout_new → ActivityCheckoutNewBinding
        String[] parts = layoutName.split("_");
        StringBuilder className = new StringBuilder();
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                // Capitalize first letter of each part
                className.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    className.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return className.toString() + BINDING_SUFFIX;
    }
    
    private static void updateOnCreateForViewBinding(Project project, PsiClass psiClass, String bindingClassName) {
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement : statements) {
            if (statement.getText().contains(SET_CONTENT_VIEW)) {
                String actualBindingClassName = extractBindingClassNameFromSetContentView(statement.getText());
                if (actualBindingClassName != null) {
                    bindingClassName = actualBindingClassName;
                }
                
                PsiStatement bindingStatement = factory.createStatementFromText("binding = " + bindingClassName + ".inflate(getLayoutInflater());", null);
                PsiStatement setContentStatement = factory.createStatementFromText("setContentView(binding.getRoot());", null);
                
                codeBlock.addBefore(bindingStatement, statement);
                statement.replace(setContentStatement);
                break;
            }
        }
    }
    
    private static String extractBindingClassNameFromSetContentView(String setContentViewStatement) {
        if (setContentViewStatement.contains("R.layout.")) {
            int startIndex = setContentViewStatement.indexOf("R.layout.") + 9;
            int endIndex = setContentViewStatement.indexOf(")", startIndex);
            if (endIndex > startIndex) {
                String layoutName = setContentViewStatement.substring(startIndex, endIndex);
                return convertLayoutNameToBindingClassName(layoutName);
            }
        }
        return null;
    }
    
    private static String convertLayoutNameToBindingClassName(String layoutName) {
        StringBuilder result = new StringBuilder();
        String[] parts = layoutName.split("_");
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(part.substring(0, 1).toUpperCase())
                      .append(part.substring(1).toLowerCase());
            }
        }
        
        return result.toString() + BINDING_SUFFIX;
    }
    
    private static void addOnDestroyMethod(Project project, PsiClass psiClass) {
        PsiMethod[] methods = psiClass.findMethodsByName("onDestroy", false);
        if (methods.length > 0) {
            PsiMethod onDestroyMethod = methods[0];
            PsiCodeBlock codeBlock = onDestroyMethod.getBody();
            if (codeBlock != null) {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                PsiStatement bindingNullStatement = factory.createStatementFromText("binding = null;", null);
                codeBlock.addBefore(bindingNullStatement, codeBlock.getLastBodyElement());
            }
        } else {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            String onDestroyMethod = """
                @Override
                protected void onDestroy() {
                    super.onDestroy();
                    binding = null;
                }""";
            PsiMethod newOnDestroyMethod = factory.createMethodFromText(onDestroyMethod, null);
            psiClass.add(newOnDestroyMethod);
        }
    }
    
    /**
     * Replaces all field references throughout the class with binding references.
     * Example: tvEditAddress.setText() → binding.actShipTvEditAddress.setText()
     */
    private static void replaceFieldReferences(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields) {
        if (bindViewFields.isEmpty()) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        // Find all references to the @BindView fields and replace them
        for (Map.Entry<String, FieldInfo> entry : bindViewFields.entrySet()) {
            String resourceId = entry.getKey();
            FieldInfo fieldInfo = entry.getValue();
            String fieldName = fieldInfo.name;
            
            // Convert resource ID to binding field name
            String bindingFieldName = convertResourceIdToBindingFieldName(resourceId);
            
            // Find all references to this field in the class
            Collection<PsiReferenceExpression> references = PsiTreeUtil.findChildrenOfType(psiClass, PsiReferenceExpression.class);
            
            for (PsiReferenceExpression reference : references) {
                if (fieldName.equals(reference.getReferenceName())) {
                    // Replace with binding.fieldName for any field reference
                    // Skip only if this is part of the field declaration itself
                    PsiElement parent = reference.getParent();
                    if (!(parent instanceof PsiField)) {
                        // Check if this is an include tag reference
                        String layoutName = extractLayoutNameFromClass(psiClass);
                        XmlLayoutHandler xmlHandler = new XmlLayoutHandler(project);
                        if (xmlHandler.isIncludeTagId(resourceId, layoutName)) {
                            // For include tags, find the main clickable element inside the included layout
                            String includedLayoutName = xmlHandler.getIncludedLayoutName(resourceId, layoutName);
                            String clickableElementId = xmlHandler.findClickableElementInIncludedLayout(includedLayoutName);
                            
                            if (clickableElementId != null) {
                                // Access the specific clickable element inside the included layout
                                String clickableFieldName = convertResourceIdToBindingFieldName(clickableElementId);
                                String replacementText = BINDING_PREFIX + bindingFieldName + "." + clickableFieldName;
                                PsiExpression newExpression = factory.createExpressionFromText(replacementText, reference);
                                reference.replace(newExpression);
                            } else {
                                // Fallback to getRoot() if no specific clickable element found
                                String replacementText = BINDING_PREFIX + bindingFieldName + ".getRoot()";
                                PsiExpression newExpression = factory.createExpressionFromText(replacementText, reference);
                                reference.replace(newExpression);
                            }
                        } else {
                            // Check if this could be an include tag by checking field type
                            if (isLikelyIncludeTag(fieldInfo)) {
                                // This is likely an include tag, find the root element ID in the included layout
                                String includedLayoutRootId = findIncludedLayoutRootId(project, psiClass, resourceId);
                                if (includedLayoutRootId != null) {
                                    // Use the actual root element ID from the included layout
                                    String rootFieldName = convertResourceIdToBindingFieldName(includedLayoutRootId);
                                    String replacementText = BINDING_PREFIX + bindingFieldName + "." + rootFieldName;
                                    PsiExpression newExpression = factory.createExpressionFromText(replacementText, reference);
                                    reference.replace(newExpression);
                                } else {
                                    // Fallback to .getRoot() if root ID not found
                                    String replacementText = BINDING_PREFIX + bindingFieldName + ".getRoot()";
                                    PsiExpression newExpression = factory.createExpressionFromText(replacementText, reference);
                                    reference.replace(newExpression);
                                }
                            } else {
                                // For regular views, use direct binding field
                                String replacementText = BINDING_PREFIX + bindingFieldName;
                                PsiExpression newExpression = factory.createExpressionFromText(replacementText, reference);
                                reference.replace(newExpression);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void removeFieldDeclarations(PsiClass psiClass, Map<String, FieldInfo> bindViewFields) {
        for (PsiField field : psiClass.getFields()) {
            // Check if this field was a @BindView field by comparing with our collected data
            for (FieldInfo fieldInfo : bindViewFields.values()) {
                if (fieldInfo.name.equals(field.getName())) {
                    field.delete();
                    break;
                }
            }
        }
    }

    private static void removeButterknifeImports(PsiJavaFile javaFile) {
        PsiImportList importList = javaFile.getImportList();
        if (importList == null) return;
        
        PsiImportStatement[] importStatements = importList.getImportStatements();
        for (PsiImportStatement importStatement : importStatements) {
            String qualifiedName = importStatement.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("butterknife")) {
                importStatement.delete();
            }
        }
    }

    private static void removeButterknifeBindCalls(PsiClass psiClass) {
        Collection<PsiMethodCallExpression> methodCalls = PsiTreeUtil.findChildrenOfType(psiClass, PsiMethodCallExpression.class);
        for (PsiMethodCallExpression methodCall : methodCalls) {
            if (methodCall.getText().contains("ButterKnife.bind")) {
                PsiStatement statement = PsiTreeUtil.getParentOfType(methodCall, PsiStatement.class);
                if (statement != null) {
                    statement.delete();
                }
            }
        }
    }

    static class FieldInfo {
        String name;
        String type;
        
        FieldInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }
}
