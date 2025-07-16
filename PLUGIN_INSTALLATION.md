# Android Studio Plugin Installation Guide

## Building the Plugin

1. **Navigate to the plugin directory:**
   ```bash
   cd butterknife-removal-plugin
   ```

2. **Build the plugin:**
   ```bash
   ./gradlew buildPlugin
   ```

   The plugin will be built as a ZIP file in `build/distributions/butterknife-removal-plugin-1.0.0.zip`

## Installing in Android Studio

### Method 1: Install from Disk

1. Open Android Studio
2. Go to **File** → **Settings** (or **Android Studio** → **Preferences** on macOS)
3. Select **Plugins** from the left sidebar
4. Click the gear icon (⚙️) and select **Install Plugin from Disk...**
5. Navigate to and select `build/distributions/butterknife-removal-plugin-1.0.0.zip`
6. Click **OK** and restart Android Studio

### Method 2: Development Installation

1. Open the plugin project in IntelliJ IDEA
2. Run the plugin using the **Run Plugin** configuration
3. This will open a new IntelliJ/Android Studio instance with the plugin loaded

## Using the Plugin

Once installed, you have three options to remove Butterknife:

### 1. Remove from Single File
- Right-click on any Java file in the Project View or Editor
- Select **Butterknife Removal** → **Remove Butterknife from File**

### 2. Remove from Directory
- Right-click on any package/directory in the Project View
- Select **Butterknife Removal** → **Remove Butterknife from Directory**
- Confirms before processing all Java files in the directory and subdirectories

### 3. Remove from Entire Project
- Go to **Tools** menu → **Remove Butterknife from Project**
- Processes ALL Java files in your project (use with caution!)

## What the Plugin Does

The plugin automatically:

✅ Converts `@BindView(R.id.viewName)` annotations to `findViewById(R.id.viewName)` calls  
✅ Converts `@OnClick(R.id.buttonName)` annotations to `setOnClickListener()` calls  
✅ Removes `ButterKnife.bind(this)` calls  
✅ Removes all Butterknife imports  
✅ Maintains your existing code structure and formatting  

## Example Conversion

**Before:**
```java
@BindView(R.id.textView) TextView textView;
@BindView(R.id.button) Button button;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);
}

@OnClick(R.id.button)
public void onButtonClick() {
    textView.setText("Clicked!");
}
```

**After:**
```java
TextView textView;
Button button;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    textView = findViewById(R.id.textView);
    button = findViewById(R.id.button);
    
    button.setOnClickListener(v -> onButtonClick());
}

public void onButtonClick() {
    textView.setText("Clicked!");
}
```

## Troubleshooting

- **Plugin not visible:** Make sure you're right-clicking on Java files or directories
- **Build errors:** Ensure you have Java 17+ and Gradle 8.4+ installed
- **IDE compatibility:** This plugin supports Android Studio 2023.2 and later

## Safety Notes

⚠️ **Always commit your changes to version control before using this plugin!**  
⚠️ **Test thoroughly after conversion to ensure your app still works correctly**  
⚠️ **The "Remove from Project" option affects ALL Java files - use with caution**