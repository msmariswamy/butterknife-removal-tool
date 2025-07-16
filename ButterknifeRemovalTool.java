import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ButterknifeRemovalTool {
    
    private static final Pattern BIND_VIEW_PATTERN = Pattern.compile(
        "@BindView\\s*\\(\\s*R\\.id\\.([^)]+)\\)\\s*([\\w<>]+)\\s+(\\w+);",
        Pattern.MULTILINE
    );
    
    private static final Pattern ON_CLICK_PATTERN = Pattern.compile(
        "@OnClick\\s*\\(\\s*R\\.id\\.([^)]+)\\)\\s*(?:public\\s+|private\\s+)?void\\s+(\\w+)\\s*\\([^)]*\\)",
        Pattern.MULTILINE
    );
    
    private static final Pattern BUTTERKNIFE_BIND = Pattern.compile(
        "ButterKnife\\.bind\\(this\\);?",
        Pattern.MULTILINE
    );
    
    private static final Pattern BUTTERKNIFE_IMPORT = Pattern.compile(
        "import\\s+butterknife\\..*;\\s*\\n",
        Pattern.MULTILINE
    );

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java ButterknifeRemovalTool <file_or_directory> [--file-only]");
            System.out.println("  --file-only: Process only the specified file");
            return;
        }
        
        String targetPath = args[0];
        boolean fileOnly = args.length > 1 && "--file-only".equals(args[1]);
        
        try {
            Path path = Paths.get(targetPath);
            if (Files.isDirectory(path) && !fileOnly) {
                processDirectory(path);
            } else if (Files.isRegularFile(path)) {
                processFile(path);
            } else {
                System.err.println("Invalid path: " + targetPath);
            }
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> {
                try {
                    processFile(path);
                } catch (IOException e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                }
            });
    }
    
    private static void processFile(Path filePath) throws IOException {
        String content = new String(Files.readAllBytes(filePath));
        String originalContent = content;
        
        if (!containsButterknife(content)) {
            return;
        }
        
        System.out.println("Processing: " + filePath);
        
        Map<String, FieldInfo> bindViewFields = extractBindViewFields(content);
        Map<String, String> onClickMethods = extractOnClickMethods(content);
        
        content = removeBindViewAnnotations(content);
        content = removeOnClickAnnotations(content);
        content = removeButterknifeImports(content);
        content = removeButterknifeBindCalls(content);
        content = addFindViewByIdCalls(content, bindViewFields);
        content = addOnClickListeners(content, bindViewFields, onClickMethods);
        
        if (!content.equals(originalContent)) {
            Files.write(filePath, content.getBytes());
            System.out.println("✓ Converted: " + filePath);
        }
    }
    
    private static boolean containsButterknife(String content) {
        return content.contains("@BindView") || 
               content.contains("@OnClick") || 
               content.contains("ButterKnife.bind");
    }
    
    private static Map<String, FieldInfo> extractBindViewFields(String content) {
        Map<String, FieldInfo> fields = new HashMap<>();
        Matcher matcher = BIND_VIEW_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String resourceId = matcher.group(1);
            String type = matcher.group(2);
            String fieldName = matcher.group(3);
            fields.put(resourceId, new FieldInfo(fieldName, type));
        }
        
        return fields;
    }
    
    private static Map<String, String> extractOnClickMethods(String content) {
        Map<String, String> methods = new HashMap<>();
        Matcher matcher = ON_CLICK_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String resourceId = matcher.group(1);
            String methodName = matcher.group(2);
            methods.put(resourceId, methodName);
        }
        
        return methods;
    }
    
    private static String removeBindViewAnnotations(String content) {
        return BIND_VIEW_PATTERN.matcher(content).replaceAll("$2 $3;");
    }
    
    private static String removeOnClickAnnotations(String content) {
        return ON_CLICK_PATTERN.matcher(content).replaceAll("public void $2() {");
    }
    
    private static String removeButterknifeImports(String content) {
        return BUTTERKNIFE_IMPORT.matcher(content).replaceAll("");
    }
    
    private static String removeButterknifeBindCalls(String content) {
        return BUTTERKNIFE_BIND.matcher(content).replaceAll("");
    }
    
    private static String addFindViewByIdCalls(String content, Map<String, FieldInfo> fields) {
        if (fields.isEmpty()) return content;
        
        StringBuilder findViewCalls = new StringBuilder();
        for (Map.Entry<String, FieldInfo> entry : fields.entrySet()) {
            String resourceId = entry.getKey();
            FieldInfo field = entry.getValue();
            findViewCalls.append("        ")
                .append(field.name)
                .append(" = findViewById(R.id.")
                .append(resourceId)
                .append(");\n");
        }
        
        Pattern onCreatePattern = Pattern.compile(
            "(protected\\s+void\\s+onCreate\\s*\\([^)]*\\)\\s*\\{[^}]*setContentView\\([^)]+\\);)",
            Pattern.DOTALL
        );
        
        Matcher matcher = onCreatePattern.matcher(content);
        if (matcher.find()) {
            return matcher.replaceFirst("$1\n\n" + findViewCalls.toString());
        }
        
        return content;
    }
    
    private static String addOnClickListeners(String content, Map<String, FieldInfo> fields, Map<String, String> onClickMethods) {
        if (onClickMethods.isEmpty()) return content;
        
        StringBuilder listeners = new StringBuilder();
        for (Map.Entry<String, String> entry : onClickMethods.entrySet()) {
            String resourceId = entry.getKey();
            String methodName = entry.getValue();
            FieldInfo field = fields.get(resourceId);
            
            if (field != null) {
                listeners.append("        ")
                    .append(field.name)
                    .append(".setOnClickListener(v -> ")
                    .append(methodName)
                    .append("());\n");
            }
        }
        
        Pattern onCreatePattern = Pattern.compile(
            "(protected\\s+void\\s+onCreate\\s*\\([^)]*\\)\\s*\\{[^}]*findViewById\\([^)]+\\);)",
            Pattern.DOTALL
        );
        
        Matcher matcher = onCreatePattern.matcher(content);
        if (matcher.find()) {
            return matcher.replaceFirst("$1\n\n" + listeners.toString());
        }
        
        return content;
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