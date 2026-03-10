package com.winlator.contentdialog;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.navigation.NavigationView;
import app.gamenative.R;
import com.winlator.inputcontrols.ControllerManager;

public class NavigationDialog extends ContentDialog {

    public static final int ACTION_KEYBOARD = 1;
    public static final int ACTION_INPUT_CONTROLS = 2;
    public static final int ACTION_EXIT_GAME = 3;
    public static final int ACTION_EDIT_CONTROLS = 4;
    public static final int ACTION_EDIT_PHYSICAL_CONTROLLER = 5;
    /** In-game Steam invite list — only added when pendingInviteCount > 0. */
    public static final int ACTION_GAME_INVITES = 6;

    public interface NavigationListener {
        void onNavigationItemSelected(int itemId);
    }

    public NavigationDialog(@NonNull Context context, NavigationListener listener) {
        this(context, listener, 0);
    }

    public NavigationDialog(@NonNull Context context, NavigationListener listener, int pendingInviteCount) {
        super(context, R.layout.navigation_dialog);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(R.drawable.navigation_dialog_background);
        }
        // Hide the title bar and bottom bar for a clean menu-only dialog
        findViewById(R.id.LLTitleBar).setVisibility(View.GONE);
        findViewById(R.id.LLBottomBar).setVisibility(View.GONE);

        GridLayout grid = findViewById(R.id.main_menu_grid);
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            grid.setColumnCount(pendingInviteCount > 0 ? 6 : 5);
        } else {
            grid.setColumnCount(2);
        }

        // Check if physical controller is connected
        ControllerManager controllerManager = ControllerManager.getInstance();
        controllerManager.scanForDevices();
        boolean hasPhysicalController = !controllerManager.getDetectedDevices().isEmpty();

        addMenuItem(context, grid, R.drawable.icon_keyboard, R.string.keyboard, ACTION_KEYBOARD, listener, 1.0f);
        addMenuItem(context, grid, R.drawable.icon_input_controls, R.string.input_controls, ACTION_INPUT_CONTROLS, listener, 1.0f);
        addMenuItem(context, grid, R.drawable.icon_popup_menu_edit, R.string.edit_controls, ACTION_EDIT_CONTROLS, listener, 1.0f);
        if (hasPhysicalController) {
            addMenuItem(context, grid, R.drawable.icon_gamepad, R.string.edit_physical_controller, ACTION_EDIT_PHYSICAL_CONTROLLER, listener, 1.0f);
        }
        if (pendingInviteCount > 0) {
            // Show Steam invites item with pending count appended to the label.
            addMenuItemWithBadge(context, grid, R.drawable.icon_friends, R.string.game_invites, ACTION_GAME_INVITES, listener, pendingInviteCount);
        }
        addMenuItem(context, grid, R.drawable.icon_exit, R.string.exit_game, ACTION_EXIT_GAME, listener, 1.0f);
    }

    private void addMenuItem(Context context, GridLayout grid, int iconRes, int titleRes, int itemId, NavigationListener listener, float alpha) {
        int padding = dpToPx(5, context);
        LinearLayout layout = new LinearLayout(context);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnClickListener(view -> {
            listener.onNavigationItemSelected(itemId);
            dismiss();
        });

        int size = dpToPx(40, context);
        View icon = new View(context);
        icon.setBackground(AppCompatResources.getDrawable(context, iconRes));
        if (icon.getBackground() != null) {
            icon.getBackground().setTint(context.getColor(R.color.navigation_dialog_item_color));
        }
        icon.setAlpha(alpha); // Apply alpha for greying out
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(lp);
        layout.addView(icon);

        int width = dpToPx(96, context);
        TextView text = new TextView(context);
        text.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        text.setText(context.getString(titleRes));
        text.setGravity(Gravity.CENTER);
        text.setLines(2);
        text.setTextColor(context.getColor(R.color.navigation_dialog_item_color));
        text.setAlpha(alpha); // Apply alpha for greying out
        Typeface tf = ResourcesCompat.getFont(context, R.font.bricolage_grotesque_regular);
        if (tf != null) {
            text.setTypeface(tf);
        }
        layout.addView(text);

        grid.addView(layout);
    }


    /** Adds a menu item with a small numeric badge overlay on the icon. */
    private void addMenuItemWithBadge(Context context, GridLayout grid, int iconRes,
                                      int titleRes, int itemId, NavigationListener listener, int badgeCount) {
        int padding = dpToPx(5, context);
        LinearLayout layout = new LinearLayout(context);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setOnClickListener(view -> {
            listener.onNavigationItemSelected(itemId);
            dismiss();
        });

        int iconSize = dpToPx(40, context);

        // Overlay container so badge can float on top of the icon
        android.widget.FrameLayout iconContainer = new android.widget.FrameLayout(context);
        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        containerLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconContainer.setLayoutParams(containerLp);

        View icon = new View(context);
        icon.setBackground(AppCompatResources.getDrawable(context, iconRes));
        if (icon.getBackground() != null) {
            icon.getBackground().setTint(context.getColor(R.color.navigation_dialog_item_color));
        }
        iconContainer.addView(icon, new android.widget.FrameLayout.LayoutParams(iconSize, iconSize));

        // Badge circle (Steam blue)
        TextView badge = new TextView(context);
        badge.setText(badgeCount > 9 ? "9+" : String.valueOf(badgeCount));
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9);
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(createBadgeDrawable(context));
        int badgeSize = dpToPx(14, context);
        android.widget.FrameLayout.LayoutParams badgeLp =
                new android.widget.FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.TOP | Gravity.END);
        iconContainer.addView(badge, badgeLp);

        layout.addView(iconContainer);

        int width = dpToPx(96, context);
        TextView text = new TextView(context);
        text.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
        text.setText(context.getString(titleRes));
        text.setGravity(Gravity.CENTER);
        text.setLines(2);
        text.setTextColor(context.getColor(R.color.navigation_dialog_item_color));
        Typeface tf = ResourcesCompat.getFont(context, R.font.bricolage_grotesque_regular);
        if (tf != null) {
            text.setTypeface(tf);
        }
        layout.addView(text);

        grid.addView(layout);
    }

    private android.graphics.drawable.GradientDrawable createBadgeDrawable(Context context) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        // Steam blue #1E9FFF
        d.setColor(0xFF1E9FFF);
        return d;
    }

    public int dpToPx(float dp, Context context){
        return (int) (dp * context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }
}
