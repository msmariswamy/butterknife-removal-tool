// Example of what the plugin converts TO (View Binding code):

public class CheckoutActivity extends AppCompatActivity {
    private ActivityCheckoutNewBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCheckoutNewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        binding.checkoutButton.setOnClickListener(v -> onCheckoutClick());
    }
    
    void onCheckoutClick() {
        String username = binding.usernameInput.getText().toString();
        String password = binding.passwordInput.getText().toString();
        // Handle checkout logic
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
