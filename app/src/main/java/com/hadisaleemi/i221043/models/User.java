package com.hadisaleemi.i221043.models;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {
    public String uid;
    public String name;
    public String firstName;
    public String lastName;
    public String email;
    public String username;
    public String usernameLower;
    public String nameLower;
    public String bio;
    public String website;
    public String phone;
    public String gender;
    public String birthday;
    public String photoUrl;
    public String coverUrl;
    public String work;
    public String school;
    public String location;
    public String fcmToken;
    public boolean online;
    public boolean profileComplete;
    public long lastSeen;
    public long createdAt;

    public User() {
    }

    public String displayName() {
        if (name != null && !name.trim().isEmpty()) return name;
        if (username != null) return username;
        return "Kinnect user";
    }

    public String firstName() {
        if (firstName != null && !firstName.isEmpty()) return firstName;
        String n = displayName();
        int sp = n.indexOf(' ');
        return sp > 0 ? n.substring(0, sp) : n;
    }
}
