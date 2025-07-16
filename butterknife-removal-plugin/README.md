# Enhanced ButterKnife Removal Tool for Android Studio

An IntelliJ IDEA/Android Studio plugin that automatically converts ButterKnife annotations to modern View Binding with intelligent XML ID validation and generation.

## 🎯 Key Features

### XML ID Validation & Generation
- **Automatic XML Scanning**: Analyzes layout files to check for missing `android:id` attributes
- **Smart ID Generation**: Creates appropriate IDs based on field names and view types
- **Naming Conventions**: Automatically applies Android naming conventions (btn_, tv_, et_, etc.)
- **Intelligent Placement**: Adds IDs to the first matching view type without an existing ID
- **Manual Fallback**: Creates TODO comments when automatic placement isn't possible

### Include Layout Support
- **Auto-ID Generation for Include Tags**: Automatically adds IDs to `<include>` tags that don't have them
- **Nested Binding Structure**: Generates correct nested binding references (e.g., `binding.layoutAddressForm.tilPhoneNumber`)
- **Mixed Layout Handling**: Supports views from both main layout and included layouts in the same class
- **Smart Layout Detection**: Detects which views belong to included layouts vs main layout
- **Consistent Structure**: Ensures all include tags have IDs for predictable binding behavior

### Advanced Layout Detection
- **Fragment Support**: Detects layout names from `onCreateView()` method for Fragments
- **Activity Support**: Detects layout names from `setContentView()` calls in Activities
- **Actual File Matching**: Checks actual layout files in project instead of just inferring from class names
- **Smart Class Name Handling**: Handles complex class name patterns (e.g., `AddEditBillingAddressFragment` → `fragment_billing_address_add_edit.xml`)
- **Multiple Naming Conventions**: Tries various common Android naming patterns

### Custom View Support
- **Custom View Detection**: Handles custom views like `CustomTextInputLayout`
- **Fallback Strategy**: Falls back to inner views when custom views don't generate binding fields
- **View Hierarchy Analysis**: Analyzes view hierarchies to find the correct binding references

### ButterKnife Conversion
- Converts `@BindView` annotations to View Binding references
- Converts `@OnClick` annotations to appropriate click listeners
- Generates View Binding initialization code with correct binding class names
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

## 🔄 Include Layout Handling

### Complex Include Layout Example

**Main Layout (`fragment_billing_address_add_edit.xml`):**
```xml
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android">
    <include layout="@layout/layout_address_form"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
    
    <Button
        android:id="@+id/btn_update"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</RelativeLayout>
```

**Included Layout (`layout_address_form.xml`):**
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
    <vitaminshoppe.consumerapp.utils.CustomTextInputLayout
        android:id="@+id/til_phone_number">
        <EditText
            android:id="@+id/et_phone_number" />
    </vitaminshoppe.consumerapp.utils.CustomTextInputLayout>
    
    <EditText
        android:id="@+id/act_shipp_adrs_et_postal" />
</LinearLayout>
```

**Java Class with Mixed @BindView:**
```java
public class AddEditBillingAddressFragment extends Fragment {
    @BindView(R.id.btn_update)  // From main layout
    Button btnUpdate;
    
    @BindView(R.id.til_phone_number)  // From included layout
    CustomTextInputLayout tilPhoneNumber;
    
    @BindView(R.id.act_shipp_adrs_et_postal)  // From included layout
    EditText etPostal;
}
```

**After Plugin Conversion:**

1. **XML Enhancement** - Include tag gets auto-generated ID:
```xml
<include 
    android:id="@+id/layout_address_form"
    layout="@layout/layout_address_form" />
```

2. **Java Conversion** - Mixed binding structure:
```java
public class AddEditBillingAddressFragment extends Fragment {
    private FragmentBillingAddressAddEditBinding binding;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBillingAddressAddEditBinding.inflate(inflater, container, false);
        
        // Main layout view - direct access
        binding.btnUpdate.setOnClickListener(v -> handleUpdate());
        
        // Included layout views - nested access
        binding.layoutAddressForm.tilPhoneNumber.setHint("Phone Number");
        binding.layoutAddressForm.actShippAdrsEtPostal.addTextChangedListener(this);
        
        return binding.getRoot();
    }
    
    @Override
    public void onDestroy() {
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
   - Check IntelliJ notifications for conversion progress and debug information
   - Review any TODO comments added to your layout files
   - Verify that include tags have been given IDs automatically
   - Check that binding class names match your actual layout file names
   - Test your app to ensure everything works correctly

## 🎯 Smart Features

### Automatic Include Tag ID Generation
- **Problem**: Include tags without IDs cause views to be merged directly into parent binding
- **Solution**: Plugin automatically adds meaningful IDs like `android:id="@+id/layout_address_form"`
- **Result**: Consistent nested binding structure (`binding.layoutAddressForm.viewName`)

### Intelligent Layout Name Detection
- **Fragment Support**: Detects layouts from `onCreateView()` method inflation calls
- **Activity Support**: Detects layouts from `setContentView()` calls
- **File Matching**: Checks actual project files instead of guessing from class names
- **Complex Names**: Handles patterns like `AddEditBillingAddressFragment` → `fragment_billing_address_add_edit.xml`

### Mixed Layout View Handling
- **Main Layout Views**: Direct binding access (`binding.mainViewId`)
- **Included Layout Views**: Nested binding access (`binding.includeId.includedViewId`)
- **Automatic Detection**: Plugin determines which views belong to which layout
- **Consistent Structure**: All include tags get IDs for predictable binding behavior

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
- **Include Tag IDs**: The plugin automatically adds IDs to include tags - review these for consistency
- **Binding Class Names**: Verify that generated binding class names match your actual layout files
- **Custom Views**: Check that custom views generate proper binding fields, plugin has fallback strategies
- **Nested Structure**: Understand the difference between direct binding (`binding.view`) and nested binding (`binding.include.view`)
- **Test Thoroughly**: Verify that all functionality works after conversion, especially with included layouts

## 🔍 Troubleshooting

### "Cannot resolve symbol" Errors
- **Check Binding Class Name**: Ensure the generated binding class matches your layout file name
- **Rebuild Project**: Clean and rebuild to regenerate binding classes
- **Include Tag IDs**: Verify that include tags now have proper IDs
- **View Binding Enabled**: Confirm View Binding is enabled in your module's build.gradle

### Nested Binding Issues
- **Include Tags**: All include tags should now have auto-generated IDs
- **Nested Access**: Use `binding.includeId.viewId` for views in included layouts
- **Direct Access**: Use `binding.viewId` for views in the main layout
- **Debug Notifications**: Check IntelliJ notifications for what the plugin detected

### Layout Detection Issues
- **Check Notifications**: The plugin shows debug information about layout detection
- **File Names**: Ensure your layout files follow Android naming conventions
- **Method Detection**: For Fragments, check that `onCreateView()` has proper layout inflation

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
