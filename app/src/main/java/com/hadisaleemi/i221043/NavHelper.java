package com.hadisaleemi.i221043;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

public class
NavHelper {

    public static final int HOME = 0, FRIENDS = 1, MARKET = 2, NOTIF = 3, MENU = 4;

    public static void setup(final Activity a, int selected, String title) {
        int[] tabs = {R.id.tab0, R.id.tab1, R.id.tab2, R.id.tab3, R.id.tab4};
        for (int i = 0; i < 5; i++) {
            final int index = i;
            final int sel = selected;
            click(a, tabs[i], () -> {
                if (index == sel) return;
                switch (index) {
                    case HOME: goHome(a); break;
                    case FRIENDS: go(a, FriendsActivity.class); break;
                    case MARKET: go(a, MarketplaceActivity.class); break;
                    case NOTIF: go(a, NotificationsActivity.class); break;
                    case MENU: go(a, MenuActivity.class); break;
                }
            });
        }

        click(a, R.id.btnMessenger, () -> a.startActivity(new Intent(a, ChatsActivity.class)));
        click(a, R.id.btnSearch, () -> a.startActivity(new Intent(a, SearchActivity.class)));
    }

    public static void go(Activity a, Class<?> target) {
        a.startActivity(new Intent(a, target));
        a.finish();
    }

    public static void goHome(Activity a) {
        Intent i = new Intent(a, StartSocialMediaActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        a.startActivity(i);
        a.finish();
    }

    public static void click(Activity a, int id, Runnable action) {
        View v = a.findViewById(id);
        if (v != null) v.setOnClickListener(x -> action.run());
    }
}
