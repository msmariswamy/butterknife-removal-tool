// Example of what the plugin converts FROM (ButterKnife code):

public class CheckoutActivity extends AppCompatActivity {
    @BindView(R.id.username_input)
    EditText usernameInput;
    
    @BindView(R.id.password_input)
    EditText passwordInput;
    
    @BindView(R.id.checkout_button)
    Button checkoutButton;
    
    @OnClick(R.id.checkout_button)
    void onCheckoutClick() {
        String username = usernameInput.getText().toString();
        String password = passwordInput.getText().toString();
        // Handle checkout logic
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout_new);
        ButterKnife.bind(this);
    }
}
