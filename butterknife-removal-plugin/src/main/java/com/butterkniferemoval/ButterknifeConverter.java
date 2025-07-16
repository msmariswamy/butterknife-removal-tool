package com.butterkniferemoval;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ButterknifeConverter {
    
    private static final Pattern BIND_VIEW_PATTERN = Pattern.compile(
        "@BindView\\s*\\(\\s*R\\.id\\.([^)]+)\\)\\s*([\\w<>]+)\\s+(\\w+);",
        Pattern.MULTILINE
    );
    
    private static final Pattern ON_CLICK_PATTERN = Pattern.compile(
        "@OnClick\\s*\\(\\s*R\\.id\\.([^)]+)\\)\\s*(?:public\\s+|private\\s+)?void\\s+(\\w+)\\s*\\([^)]*\\)",
        Pattern.MULTILINE
    );

    public static void convertFile(Project project, PsiJavaFile javaFile) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            convertButterknifeAnnotations(project, javaFile);
        });
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
        
        removeAnnotations(mainClass, bindViewFields, onClickMethods, onCheckedChangedMethods, 
                         onEditorActionMethods, onFocusChangeMethods, onTextChangedMethods);
        addFindViewByIdCalls(project, mainClass, bindViewFields);
        addOnClickListeners(project, mainClass, bindViewFields, onClickMethods);
        addOnCheckedChangedListeners(project, mainClass, bindViewFields, onCheckedChangedMethods);
        addOnEditorActionListeners(project, mainClass, bindViewFields, onEditorActionMethods);
        addOnFocusChangeListeners(project, mainClass, bindViewFields, onFocusChangeMethods);
        addOnTextChangedListeners(project, mainClass, bindViewFields, onTextChangedMethods);
        removeButterknifeImports(javaFile);
        removeButterknifeBindCalls(mainClass);
    }

    private static void collectBindViewFields(PsiClass psiClass, Map<String, FieldInfo> bindViewFields) {
        PsiField[] fields = psiClass.getFields();
        for (PsiField field : fields) {
            PsiAnnotation bindViewAnnotation = field.getAnnotation("butterknife.BindView");
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
        
        PsiStatement lastFindViewStatement = findLastFindViewStatement(codeBlock);
        if (lastFindViewStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onClickMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            FieldInfo fieldInfo = bindViewFields.get(resourceId);
            
            String listenerStatement;
            if (fieldInfo != null) {
                // Use existing field if available
                listenerStatement = fieldInfo.name + ".setOnClickListener(v -> " + methodCall + ");";
            } else {
                // Create direct findViewById call for views without @BindView
                listenerStatement = "findViewById(R.id." + resourceId + ").setOnClickListener(v -> " + methodCall + ");";
            }
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastFindViewStatement);
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
        
        PsiStatement lastFindViewStatement = findLastFindViewStatement(codeBlock);
        if (lastFindViewStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onCheckedChangedMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            FieldInfo fieldInfo = bindViewFields.get(resourceId);
            
            String listenerStatement;
            if (fieldInfo != null) {
                listenerStatement = fieldInfo.name + ".setOnCheckedChangeListener((buttonView, isChecked) -> " + methodCall + ");";
            } else {
                listenerStatement = "findViewById(R.id." + resourceId + ").setOnCheckedChangeListener((buttonView, isChecked) -> " + methodCall + ");";
            }
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastFindViewStatement);
        }
    }

    private static void addOnEditorActionListeners(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onEditorActionMethods) {
        if (onEditorActionMethods.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement lastFindViewStatement = findLastFindViewStatement(codeBlock);
        if (lastFindViewStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onEditorActionMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            FieldInfo fieldInfo = bindViewFields.get(resourceId);
            
            String listenerStatement;
            if (fieldInfo != null) {
                listenerStatement = fieldInfo.name + ".setOnEditorActionListener((v, actionId, event) -> " + methodCall + ");";
            } else {
                listenerStatement = "findViewById(R.id." + resourceId + ").setOnEditorActionListener((v, actionId, event) -> " + methodCall + ");";
            }
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastFindViewStatement);
        }
    }

    private static void addOnFocusChangeListeners(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onFocusChangeMethods) {
        if (onFocusChangeMethods.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement lastFindViewStatement = findLastFindViewStatement(codeBlock);
        if (lastFindViewStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onFocusChangeMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            FieldInfo fieldInfo = bindViewFields.get(resourceId);
            
            String listenerStatement;
            if (fieldInfo != null) {
                listenerStatement = fieldInfo.name + ".setOnFocusChangeListener((v, hasFocus) -> " + methodCall + ");";
            } else {
                listenerStatement = "findViewById(R.id." + resourceId + ").setOnFocusChangeListener((v, hasFocus) -> " + methodCall + ");";
            }
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastFindViewStatement);
        }
    }

    private static void addOnTextChangedListeners(Project project, PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onTextChangedMethods) {
        if (onTextChangedMethods.isEmpty()) return;
        
        PsiMethod onCreateMethod = findOnCreateMethod(psiClass);
        if (onCreateMethod == null) return;
        
        PsiCodeBlock codeBlock = onCreateMethod.getBody();
        if (codeBlock == null) return;
        
        PsiStatement lastFindViewStatement = findLastFindViewStatement(codeBlock);
        if (lastFindViewStatement == null) return;
        
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        
        for (Map.Entry<String, String> entry : onTextChangedMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodCall = entry.getValue();
            FieldInfo fieldInfo = bindViewFields.get(resourceId);
            
            String listenerStatement;
            String actualMethodCall;
            
            // Check if this is an Editable parameter method
            if (methodCall.startsWith("EDITABLE:")) {
                String methodName = methodCall.substring(9); // Remove "EDITABLE:" prefix
                actualMethodCall = methodName + "(s)"; // Use Editable from afterTextChanged
                
                if (fieldInfo != null) {
                    listenerStatement = fieldInfo.name + ".addTextChangedListener(new TextWatcher() { " +
                        "@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} " +
                        "@Override public void onTextChanged(CharSequence s, int start, int before, int count) {} " +
                        "@Override public void afterTextChanged(Editable s) { " + actualMethodCall + " } });";
                } else {
                    listenerStatement = "findViewById(R.id." + resourceId + ").addTextChangedListener(new TextWatcher() { " +
                        "@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} " +
                        "@Override public void onTextChanged(CharSequence s, int start, int before, int count) {} " +
                        "@Override public void afterTextChanged(Editable s) { " + actualMethodCall + " } });";
                }
            } else {
                // CharSequence parameter - use onTextChanged
                if (fieldInfo != null) {
                    listenerStatement = fieldInfo.name + ".addTextChangedListener(new TextWatcher() { " +
                        "@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} " +
                        "@Override public void onTextChanged(CharSequence s, int start, int before, int count) { " + methodCall + " } " +
                        "@Override public void afterTextChanged(Editable s) {} });";
                } else {
                    listenerStatement = "findViewById(R.id." + resourceId + ").addTextChangedListener(new TextWatcher() { " +
                        "@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} " +
                        "@Override public void onTextChanged(CharSequence s, int start, int before, int count) { " + methodCall + " } " +
                        "@Override public void afterTextChanged(Editable s) {} });";
                }
            }
            
            PsiStatement statement = factory.createStatementFromText(listenerStatement, null);
            codeBlock.addAfter(statement, lastFindViewStatement);
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