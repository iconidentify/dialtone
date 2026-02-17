/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.chat;

import java.util.*;

public class ChatRoom {

    private String title;
    private final List<User> users;

    public ChatRoom(String title) {
        if (title == null || title.isEmpty()) {
            this.title = "Dialtone Connected - Untitled Room";
        } else {
            this.title = title.trim();
        }
        this.users = new ArrayList<>();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title == null || title.isEmpty()) {
            this.title = "Dialtone Connected - Untitled Room";
        } else {
            this.title = title.trim();
        }
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    public void addUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (!users.contains(user)) {
            users.add(user);
        }
    }

    public void addUser(String username) {
        addUser(new User(username));
    }

    public boolean removeUser(User user) {
        return users.remove(user);
    }

    public boolean removeUser(String username) {
        return users.removeIf(user -> user.getUsername().equals(username));
    }

    public boolean hasUser(String username) {
        return users.stream().anyMatch(user -> user.getUsername().equals(username));
    }

    public int getUserCount() {
        return users.size();
    }

    public void clearUsers() {
        users.clear();
    }

    @Override
    public String toString() {
        return "ChatRoom{" +
                "title='" + title + '\'' +
                ", userCount=" + users.size() +
                ", users=" + users +
                '}';
    }
}