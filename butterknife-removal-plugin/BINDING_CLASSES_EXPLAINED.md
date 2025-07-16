# How View Binding Classes Work

## The Key Point: Binding Classes Are Generated, Not Written

The `ActivityCheckoutNewBinding` class is **automatically generated** by the Android build system when you:

1. Enable View Binding in your Android app's `build.gradle`
2. Have a layout file named `activity_checkout_new.xml`
3. Build your Android project

## Example: Complete Android Project Setup

### 1. Enable View Binding in `app/build.gradle`:
```gradle
android {
    ...
    viewBinding {
        enabled = true
    }
}
```

### 2. Create Layout File `res/layout/activity_checkout_new.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <EditText
        android:id="@+id/username_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Username" />

    <EditText
        android:id="@+id/password_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Password"
        android:inputType="textPassword" />

    <Button
        android:id="@+id/checkout_button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Checkout" />

</LinearLayout>
```

### 3. What Happens When You Build:

The Android Gradle Plugin automatically generates:

```java
// This class is AUTO-GENERATED - you never write it yourself!
public final class ActivityCheckoutNewBinding implements ViewBinding {
    private final LinearLayout rootView;
    
    public final EditText usernameInput;
    public final EditText passwordInput;
    public final Button checkoutButton;
    
    // Constructor and methods are auto-generated
    public static ActivityCheckoutNewBinding inflate(LayoutInflater inflater) {
        // Auto-generated implementation
    }
    
    public LinearLayout getRoot() {
        return rootView;
    }
}
```

## How the Plugin Works

1. **Plugin runs in IntelliJ/Android Studio**: Converts ButterKnife code to View Binding code
2. **Generated code references binding classes**: `ActivityCheckoutNewBinding`, `FragmentLoginBinding`, etc.
3. **Developer builds Android project**: Android Gradle Plugin generates the binding classes
4. **Everything compiles**: The generated binding classes match what the plugin generated

## Testing the Plugin

To test that your plugin is working:

1. **Install the plugin** in Android Studio
2. **Create a test Android project** with View Binding enabled
3. **Add some ButterKnife code** to an Activity
4. **Run the plugin** on that code
5. **Build the Android project** - the binding classes will be generated and everything will work

## Naming Convention

The plugin automatically converts layout names to binding class names:
- `activity_checkout_new.xml` → `ActivityCheckoutNewBinding`
- `fragment_login.xml` → `FragmentLoginBinding`
- `dialog_confirm.xml` → `DialogConfirmBinding`

This is exactly how Android's View Binding system works!
