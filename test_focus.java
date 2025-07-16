public class TestActivity {
        void promotionFocusChange(boolean hasFocus) {

        setHintVisibility(etPromotionCode, tilPromotionCode, R.string.hint_enter_promotion_code, R.string.hint_enter_promotion_code);
        if (!hasFocus) {
            hideKeyboard();
        } else {
            showKeyboard();
        }
    }
}