# Butterknife Removal Tool

A Java utility to automatically remove Butterknife annotations from Android projects and convert them to standard findViewById calls.

## Features

- Converts `@BindView` annotations to `findViewById()` calls
- Converts `@OnClick` annotations to `setOnClickListener()` calls
- Removes Butterknife imports and bind calls
- Processes entire directories or single files
- Maintains code structure and formatting

## Usage

### Compile the tool:
```bash
javac ButterknifeRemovalTool.java
```

### Process entire directory:
```bash
java ButterknifeRemovalTool /path/to/android/project/src
```

### Process single file:
```bash
java ButterknifeRemovalTool /path/to/Activity.java --file-only
```

## Example Conversion

### Before:
```java
public class MainActivity extends AppCompatActivity {
    @BindView(R.id.textView)
    TextView textView;
    
    @BindView(R.id.button)
    Button button;
    
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
}
```

### After:
```java
public class MainActivity extends AppCompatActivity {
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
}
```

## Supported Annotations

- `@BindView` - Converted to findViewById()
- `@OnClick` - Converted to setOnClickListener()
- Removes ButterKnife.bind() calls
- Removes Butterknife imports# butterknife-removal-tool
