package com.butterkniferemoval;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

public class ButterknifeConverter {
    
    private static final String BUTTERKNIFE_BIND_VIEW = "butterknife.BindView";
    private static final String R_ID_PREFIX = "R.id.";
    private static final String BINDING_PREFIX = "binding.";
    private static final String SET_CONTENT_VIEW = "setContentView";
    
    private ButterknifeConverter() {
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
        Map<String, String> onCheckedChangedMethods = new HashMap<>();
        Map<String, String> onEditorActionMethods = new HashMap<>();
        Map<String, String> onFocusChangeMethods = new HashMap<>();
        Map<String, String> onTextChangedMethods = new HashMap<>();
        
        collectBindViewFields(mainClass, bindViewFields);
        collectOnClickMethods(mainClass, onClickMethods);
        collectOnCheckedChangedMethods(mainClass, onCheckedChangedMethods);
        collectOnEditorActionMethods(mainClass, onEditorActionMethods);
        collectOnFocusChangeMethods(mainClass, onFocusChangeMethods);
        collectOnTextChangedMethods(mainClass, onTextChangedMethods);
        
        if (bindViewFields.isEmpty() && onClickMethods.isEmpty() && 
            onCheckedChangedMethods.isEmpty() && onEditorActionMethods.isEmpty() &&
            onFocusChangeMethods.isEmpty() && onTextChangedMethods.isEmpty()) {
            return;
        }
        
        // Validate and ensure XML IDs exist
        validateXmlIds(project, mainClass, bindViewFields);
        
        removeAnnotations(mainClass, bindViewFields, onClickMethods, onCheckedChangedMethods, 
                         onEditorActionMethods, onFocusChangeMethods, onTextChangedMethods);
        addViewBindingSupport(project, mainClass, javaFile);
        removeFieldDeclarations(mainClass, bindViewFields);
        addOnClickListeners(project, mainClass, bindViewFields, onClickMethods);
        addOnCheckedChangedListeners(project, mainClass, bindViewFields, onCheckedChangedMethods);
        addOnEditorActionListeners(project, mainClass, bindViewFields, onEditorActionMethods);
        addOnFocusChangeListeners(project, mainClass, bindViewFields, onFocusChangeMethods);
        addOnTextChangedListeners(project, mainClass, bindViewFields, onTextChangedMethods);
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
        
        // Log validation results (could be enhanced to show user notification)
        for (Map.Entry<String, String> entry : validationResults.entrySet()) {
            String resourceId = entry.getKey();
            String result = entry.getValue();
            // In a real implementation, you might want to log this or show to user
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
                    // Check if method has parameters to determine how to call it
                    boolean hasParameters = method.getParameterList().getParametersCount() > 0;
                    String methodCall = hasParameters ? method.getName() + "(v)" : method.getName() + "()";
                    
                    // Add each resource ID separately
                    for (String resourceId : resourceIds) {
                        onClickMethods.put(resourceId, methodCall);
                    }
                }
            }
        }
    }

    private static void collectOnCheckedChangedMethods(PsiClass psiClass, Map<String, String> onCheckedChangedMethods) {
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            PsiAnnotation annotation = method.getAnnotation("butterknife.OnCheckedChanged");
            if (annotation != null) {
                List<String> resourceIds = extractResourceIds(annotation);
                if (!resourceIds.isEmpty()) {
                    String methodCall = method.getName() + "()";
                    for (String resourceId : resourceIds) {
                        onCheckedChangedMethods.put(resourceId, methodCall);
                    }
                }
            }
        }
    }

    private static void collectOnEditorActionMethods(PsiClass psiClass, Map<String, String> onEditorActionMethods) {
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            PsiAnnotation annotation = method.getAnnotation("butterknife.OnEditorAction");
            if (annotation != null) {
                List<String> resourceIds = extractResourceIds(annotation);
                if (!resourceIds.isEmpty()) {
                    String methodCall = method.getName() + "(v, actionId, event)";
                    for (String resourceId : resourceIds) {
                        onEditorActionMethods.put(resourceId, methodCall);
                    }
                }
            }
        }
    }

    private static void collectOnFocusChangeMethods(PsiClass psiClass, Map<String, String> onFocusChangeMethods) {
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            PsiAnnotation annotation = method.getAnnotation("butterknife.OnFocusChange");
            if (annotation != null) {
                List<String> resourceIds = extractResourceIds(annotation);
                if (!resourceIds.isEmpty()) {
                    String methodCall = method.getName() + "(hasFocus)";
                    for (String resourceId : resourceIds) {
                        onFocusChangeMethods.put(resourceId, methodCall);
                    }
                }
            }
        }
    }

    private static void collectOnTextChangedMethods(PsiClass psiClass, Map<String, String> onTextChangedMethods) {
        PsiMethod[] methods = psiClass.getMethods();
        for (PsiMethod method : methods) {
            PsiAnnotation annotation = method.getAnnotation("butterknife.OnTextChanged");
            if (annotation != null) {
                List<String> resourceIds = extractResourceIds(annotation);
                if (!resourceIds.isEmpty()) {
                    // Check parameter type to determine how to call the method
                    PsiParameter[] parameters = method.getParameterList().getParameters();
                    String methodCall;
                    if (parameters.length > 0) {
                        String paramType = parameters[0].getType().getCanonicalText();
                        if (paramType.contains("Editable")) {
                            methodCall = "EDITABLE:" + method.getName(); // Special marker for Editable
                        } else {
                            methodCall = method.getName() + "(s)"; // CharSequence is fine
                        }
                    } else {
                        methodCall = method.getName() + "()";
                    }
                    for (String resourceId : resourceIds) {
                        onTextChangedMethods.put(resourceId, methodCall);
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
                
                // Handle array syntax: {R.id.view1, R.id.view2, ...}
                if (text.startsWith("{") && text.endsWith("}")) {
                    String content = text.substring(1, text.length() - 1);
                    String[] parts = content.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (trimmed.contains("R.id.")) {
                            String id = trimmed.substring(trimmed.indexOf("R.id.") + 5);
                            resourceIds.add(id);
                        }
                    }
                }
                // Handle single ID: R.id.view1
                else if (text.contains("R.id.")) {
                    String id = text.substring(text.indexOf("R.id.") + 5);
                    resourceIds.add(id);
                }
            }
        }
        
        return resourceIds;
    }

    private static void removeAnnotations(PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onClickMethods,
                                         Map<String, String> onCheckedChangedMethods, Map<String, String> onEditorActionMethods,
                                         Map<String, String> onFocusChangeMethods, Map<String, String> onTextChangedMethods) {
        for (PsiField field : psiClass.getFields()) {
            PsiAnnotation annotation = field.getAnnotation("butterknife.BindView");
            if (annotation != null) {
                annotation.delete();
            }
        }
        
        for (PsiMethod method : psiClass.getMethods()) {
            PsiAnnotation onClickAnnotation = method.getAnnotation("butterknife.OnClick");
            if (onClickAnnotation != null) {
                onClickAnnotation.delete();
            }
            
            PsiAnnotation onCheckedChangedAnnotation = method.getAnnotation("butterknife.OnCheckedChanged");
            if (onCheckedChangedAnnotation != null) {
                onCheckedChangedAnnotation.delete();
            }
            
            PsiAnnotation onEditorActionAnnotation = method.getAnnotation("butterknife.OnEditorAction");
            if (onEditorActionAnnotation != null) {
                onEditorActionAnnotation.delete();
            }
            
            PsiAnnotation onFocusChangeAnnotation = method.getAnnotation("butterknife.OnFocusChange");
            if (onFocusChangeAnnotation != null) {
                onFocusChangeAnnotation.delete();
            }
            
            PsiAnnotation onTextChangedAnnotation = method.getAnnotation("butterknife.OnTextChanged");
            if (onTextChangedAnnotation != null) {
                onTextChangedAnnotation.delete();
            }
        }
    }

    private static void addFindViewByIdCalls(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields) {
        if (bindViewFields.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement setContentViewStatement = findSetContentViewStatement(codeBlock);
        if (setContentViewStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, FieldInfo> entry : bindViewFields.entrySet()) {
            String resourceId = entry.getKey();
            FieldInfo fieldInfo = entry.getValue();
            
            String findViewStatement = fieldInfo.name + " = findViewById(R.id." + resourceId + ");";
            PsiStatement statement = factory.createStatementFromText(findViewStatement, null);
            
            codeBlock.addAfter(statement, setContentViewStatement);
        }
    }

    private static void addOnClickListeners(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onClickMethods) {
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
            
            // Convert resource ID to binding field name (R.id.my_button -> myButton)
            String bindingFieldName = convertResourceIdToBindingFieldName(resourceId);
            String listenerStatement = "binding." + bindingFieldName + ".setOnClickListener(v -> " + methodCall + ");";
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastBindingStatement);
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

    private static PsiStatement findSetContentViewStatement(PsiCodeBlock codeBlock) {
        PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement : statements) {
            if (statement.getText().contains("setContentView")) {
                return statement;
            }
        }
        return null;
    }

    private static PsiStatement findLastFindViewStatement(PsiCodeBlock codeBlock) {
        PsiStatement[] statements = codeBlock.getStatements();
        PsiStatement lastFindViewStatement = null;
        
        for (PsiStatement statement : statements) {
            if (statement.getText().contains("findViewById")) {
                lastFindViewStatement = statement;
            }
        }
        
        // If no findViewById statements found, fall back to setContentView
        if (lastFindViewStatement == null) {
            lastFindViewStatement = findSetContentViewStatement(codeBlock);
        }
        
        return lastFindViewStatement;
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
        // Convert snake_case to camelCase (et_promotion_code -> etPromotionCode)
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
    }

    private static String getBindingPackageName(PsiJavaFile javaFile) {
        String packageName = javaFile.getPackageName();
        return packageName + ".databinding";
    }

    private static void addViewBindingSupport(Project project, PsiClass psiClass, PsiJavaFile javaFile) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        // First, try to determine the correct binding class name from setContentView
        String bindingClassName = extractBindingClassNameFromOnCreate(psiClass);
        
        // If we can't find it, fall back to generating from class name
        if (bindingClassName == null) {
            String className = psiClass.getName();
            bindingClassName = generateBindingClassName(className);
        }
        
        // Add binding field declaration
        String bindingFieldDeclaration = "private " + bindingClassName + " binding;";
        PsiField bindingField = factory.createFieldFromText(bindingFieldDeclaration, null);
        psiClass.add(bindingField);
        
        // Skip adding import for now - View Binding classes are generated at compile time
        // The import will be added automatically by the IDE when the binding class is generated
        
        // Update onCreate method
        updateOnCreateForViewBinding(project, psiClass, bindingClassName);
        
        // Add onDestroy method if it doesn't exist
        addOnDestroyMethod(project, psiClass);
    }
    
    private static String extractBindingClassNameFromOnCreate(PsiClass psiClass) {
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return null;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return null;
        
        PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement : statements) {
            if (statement.getText().contains("setContentView")) {
                return extractBindingClassNameFromSetContentView(statement.getText());
            }
        }
        return null;
    }
    
    private static String generateBindingClassName(String activityName) {
        // Convert ActivityName to ActivityNameBinding
        if (activityName.endsWith("Activity")) {
            return activityName.substring(0, activityName.length() - 8) + "Binding";
        } else if (activityName.endsWith("Fragment")) {
            return activityName.substring(0, activityName.length() - 8) + "Binding";
        } else {
            return activityName + "Binding";
        }
    }
    
    private static void updateOnCreateForViewBinding(Project project, PsiClass psiClass, String bindingClassName) {
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        // Find and replace setContentView
        PsiStatement[] statements = codeBlock.getStatements();
        for (PsiStatement statement : statements) {
            if (statement.getText().contains("setContentView")) {
                // Extract layout name from setContentView call
                String actualBindingClassName = extractBindingClassNameFromSetContentView(statement.getText());
                if (actualBindingClassName != null) {
                    bindingClassName = actualBindingClassName;
                }
                
                // Create binding initialization and new setContentView statements
                PsiStatement bindingStatement = factory.createStatementFromText("binding = " + bindingClassName + ".inflate(getLayoutInflater());", null);
                PsiStatement setContentStatement = factory.createStatementFromText("setContentView(binding.getRoot());", null);
                
                // Add binding initialization before the original setContentView
                codeBlock.addBefore(bindingStatement, statement);
                
                // Replace original setContentView with new binding-based setContentView
                statement.replace(setContentStatement);
                break;
            }
        }
    }
    
    private static String extractBindingClassNameFromSetContentView(String setContentViewStatement) {
        // Extract layout name from setContentView(R.layout.activity_checkout_new)
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
        // Convert activity_checkout_new to ActivityCheckoutNewBinding
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
    
    private static void addOnDestroyMethod(Project project, PsiClass psiClass) {
        // Check if onDestroy already exists
        PsiMethod[] methods = psiClass.findMethodsByName("onDestroy", false);
        if (methods.length > 0) {
            // Add binding = null to existing onDestroy
            PsiMethod onDestroyMethod = methods[0];
            PsiCodeBlock codeBlock = onDestroyMethod.getBody();
            if (codeBlock != null) {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                PsiStatement bindingNullStatement = factory.createStatementFromText("binding = null;", null);
                codeBlock.addBefore(bindingNullStatement, codeBlock.getLastBodyElement());
            }
        } else {
            // Create new onDestroy method
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            String onDestroyMethod = "@Override\n" +
                                    "protected void onDestroy() {\n" +
                                    "    super.onDestroy();\n" +
                                    "    binding = null;\n" +
                                    "}";
            PsiMethod newOnDestroyMethod = factory.createMethodFromText(onDestroyMethod, null);
            psiClass.add(newOnDestroyMethod);
        }
    }
    
    private static void removeFieldDeclarations(PsiClass psiClass, Map<String, FieldInfo> bindViewFields) {
        for (PsiField field : psiClass.getFields()) {
            PsiAnnotation bindViewAnnotation = field.getAnnotation("butterknife.BindView");
            if (bindViewAnnotation != null) {
                field.delete();
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

    private static void addOnCheckedChangedListeners(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onCheckedChangedMethods) {
        if (onCheckedChangedMethods.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement lastBindingStatement = findLastBindingStatement(codeBlock);
        if (lastBindingStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onCheckedChangedMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            
            String bindingFieldName = convertResourceIdToBindingFieldName(resourceId);
            String listenerStatement = "binding." + bindingFieldName + ".setOnCheckedChangeListener((buttonView, isChecked) -> " + methodCall + ");";
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastBindingStatement);
        }
    }

    private static void addOnEditorActionListeners(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onEditorActionMethods) {
        if (onEditorActionMethods.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement lastBindingStatement = findLastBindingStatement(codeBlock);
        if (lastBindingStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onEditorActionMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            
            String bindingFieldName = convertResourceIdToBindingFieldName(resourceId);
            String listenerStatement = "binding." + bindingFieldName + ".setOnEditorActionListener((v, actionId, event) -> " + methodCall + ");";
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastBindingStatement);
        }
    }

    private static void addOnFocusChangeListeners(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onFocusChangeMethods) {
        if (onFocusChangeMethods.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement lastBindingStatement = findLastBindingStatement(codeBlock);
        if (lastBindingStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onFocusChangeMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            
            String bindingFieldName = convertResourceIdToBindingFieldName(resourceId);
            String listenerStatement = "binding." + bindingFieldName + ".setOnFocusChangeListener((v, hasFocus) -> " + methodCall + ");";
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastBindingStatement);
        }
    }

    private static void addOnTextChangedListeners(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onTextChangedMethods) {
        if (onTextChangedMethods.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement lastBindingStatement = findLastBindingStatement(codeBlock);
        if (lastBindingStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onTextChangedMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            
            String bindingFieldName = convertResourceIdToBindingFieldName(resourceId);
            String listenerStatement;
            String actualMethodCall;
            
            // Check if this is an Editable parameter method
            if (methodCall.startsWith("EDITABLE:")) {
                String methodName = methodCall.substring(9); // Remove "EDITABLE:" prefix
                actualMethodCall = methodName + "(s)"; // Use Editable from afterTextChanged
                
                listenerStatement = "binding." + bindingFieldName + ".addTextChangedListener(new TextWatcher() { " +
                    "@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} " +
                    "@Override public void onTextChanged(CharSequence s, int start, int before, int count) {} " +
                    "@Override public void afterTextChanged(Editable s) { " + actualMethodCall + " } });";
            } else {
                // CharSequence parameter - use onTextChanged
                listenerStatement = "binding." + bindingFieldName + ".addTextChangedListener(new TextWatcher() { " +
                    "@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} " +
                    "@Override public void onTextChanged(CharSequence s, int start, int before, int count) { " + methodCall + " } " +
                    "@Override public void afterTextChanged(Editable s) {} });";
            }
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastBindingStatement);
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