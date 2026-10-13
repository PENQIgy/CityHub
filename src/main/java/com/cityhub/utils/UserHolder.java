package com.cityhub.utils;

import com.cityhub.dto.UserDTO;

/**
 * 基于 ThreadLocal 的当前登录用户上下文。
 *
 * @author CityHub
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        tl.set(user);
    }

    public static UserDTO getUser() {
        return tl.get();
    }

    /**
     * 取当前登录用户 id，未登录返回 null。
     */
    public static Long getUserId() {
        UserDTO user = tl.get();
        return user == null ? null : user.getId();
    }

    public static void removeUser() {
        tl.remove();
    }
}
