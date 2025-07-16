public class TestActivity {
        public void onCheckedChangedHealthyRewardCheck() {
        if (cbHealthyAwards.isChecked()) {
            cbHealthyAwards.setTypeface(Typeface.createFromAsset(getAssets(), Constants.FONT_BOLD));
        } else {
            cbHealthyAwards.setTypeface(Typeface.createFromAsset(getAssets(), Constants.FONT_REGULAR));
        }
    }

        void promotionFocusChange(boolean hasFocus) {
        setHintVisibility(etPromotionCode, tilPromotionCode, R.string.hint_enter_promotion_code, R.string.hint_enter_promotion_code);
        if (!hasFocus) {
            hideKeyboard();
        } else {
            showKeyboard();
        }
    }

        public boolean onEditorActionPromoCode(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            validatePromotionCode();
            hideKeyboard();
            rlParent.requestFocus();
        }
        return false;
    }
}