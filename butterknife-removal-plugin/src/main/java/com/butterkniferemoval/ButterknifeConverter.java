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
        
        collectBindViewFields(mainClass, bindViewFields);
        collectOnClickMethods(mainClass, onClickMethods);
        
        if (bindViewFields.isEmpty() && onClickMethods.isEmpty()) {
            return;
        }
        
        removeAnnotations(mainClass, bindViewFields, onClickMethods);
        addFindViewByIdCalls(project, mainClass, bindViewFields);
        addOnClickListeners(project, mainClass, bindViewFields, onClickMethods);
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

    private static void removeAnnotations(PsiClass psiClass, Map<String, FieldInfo> bindViewFields, Map<String, String> onClickMethods) {
        for (PsiField field : psiClass.getFields()) {
            PsiAnnotation annotation = field.getAnnotation("butterknife.BindView");
            if (annotation != null) {
                annotation.delete();
            }
        }
        
        for (PsiMethod method : psiClass.getMethods()) {
            PsiAnnotation annotation = method.getAnnotation("butterknife.OnClick");
            if (annotation != null) {
                annotation.delete();
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
        
        PsiStatement setContentViewStatement = findSetContentViewStatement(codeBlock);
        if (setContentViewStatement == null) return;
        
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
            codeBlock.addAfter(statement, setContentViewStatement);
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