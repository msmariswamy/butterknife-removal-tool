# Enhanced ButterKnife Removal Tool for Android Studio

An IntelliJ IDEA/Android Studio plugin that automatically converts ButterKnife annotations to modern View Binding with intelligent XML ID validation and generation.

## 🎯 Key Features

### XML ID Validation & Generation
- **Automatic XML Scanning**: Analyzes layout files to check for missing `android:id` attributes
- **Smart ID Generation**: Creates appropriate IDs based on field names and view types
- **Naming Conventions**: Automatically applies Android naming conventions (btn_, tv_, et_, etc.)
- **Intelligent Placement**: Adds IDs to the first matching view type without an existing ID
- **Manual Fallback**: Creates TODO comments when automatic placement isn't possible

### ButterKnife Conversion
- Converts `@BindView` annotations to View Binding references
- Converts `@OnClick` annotations to appropriate click listeners
- Generates View Binding initialization code
- Adds proper cleanup in `onDestroy()`
- Removes ButterKnife imports and bind calls

## 🔧 How It Works

### XML ID Validation Process

1. **Extract Layout Name**: Determines the layout file from `setContentView()` or class name
2. **Scan Existing IDs**: Parses the XML layout to find all existing `android:id` attributes
3. **Identify Missing IDs**: Compares required IDs from `@BindView` annotations
4. **Generate Appropriate IDs**: Creates IDs following Android naming conventions:
   - `Button` → `btn_` prefix
   - `TextView` → `tv_` prefix
   - `EditText` → `et_` prefix
   - `ImageView` → `iv_` prefix
   - And many more...
5. **Auto-place IDs**: Adds missing IDs to matching view types
6. **Create Comments**: Adds TODO comments for manual placement when needed

### Before Conversion
```java
public class MainActivity extends AppCompatActivity {
    @BindView(R.id.username_input)
    EditText usernameInput;
    
    @BindView(R.id.login_button)
    Button loginButton;
    
    @OnClick(R.id.login_button)
    void onLoginClick() {
        // Handle login
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }
}
```

### After Conversion
```java
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        binding.loginButton.setOnClickListener(v -> onLoginClick());
    }
    
    void onLoginClick() {
        // Handle login
        String username = binding.usernameInput.getText().toString();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
```

### XML Layout Enhancement
If your layout is missing IDs, the plugin will automatically add them:

**Before:**
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <EditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Username" />
    
    <Button
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Login" />
        
</LinearLayout>
```

**After:**
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <EditText
        android:id="@+id/username_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Username" />
    
    <Button
        android:id="@+id/login_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Login" />
        
</LinearLayout>
```

## 📋 Usage

1. **Enable View Binding** in your app's `build.gradle`:
   ```gradle
   android {
       viewBinding {
           enabled = true
       }
   }
   ```

2. **Run the Plugin**:
   - Right-click on any Java file or package
   - Select "Remove Butterknife" from the context menu
   - The plugin will automatically handle both code conversion and XML validation

3. **Review Results**:
   - Check the console for XML ID validation results
   - Review any TODO comments added to your layout files
   - Test your app to ensure everything works correctly

## 🎨 ID Naming Conventions

The plugin follows Android naming conventions for generated IDs:

| View Type | Prefix | Example |
|-----------|--------|---------|
| Button | `btn_` | `btn_login` |
| TextView | `tv_` | `tv_title` |
| EditText | `et_` | `et_username` |
| ImageView | `iv_` | `iv_avatar` |
| RecyclerView | `rv_` | `rv_items` |
| ListView | `lv_` | `lv_contacts` |
| ScrollView | `sv_` | `sv_content` |
| CheckBox | `cb_` | `cb_remember` |
| RadioButton | `rb_` | `rb_option` |
| Switch | `sw_` | `sw_notifications` |
| And more... | | |

## ⚠️ Important Notes

- **Backup Your Code**: Always commit your changes before running the plugin
- **Enable View Binding**: Make sure View Binding is enabled in your module
- **Review Generated IDs**: Check that auto-generated IDs make sense for your UI
- **Test Thoroughly**: Verify that all functionality works after conversion

## 🔧 Requirements

- IntelliJ IDEA or Android Studio
- Java/Kotlin Android project
- ButterKnife annotations in your code

## 📦 Installation

1. Download the plugin from the releases page
2. Install via: File → Settings → Plugins → Install from disk
3. Restart your IDE
4. The plugin will be available in the context menu

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
