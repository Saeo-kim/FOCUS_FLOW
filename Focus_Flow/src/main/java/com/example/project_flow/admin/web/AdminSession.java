package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Where the signed-in administrator lives: the session holds the id, the request holds the loaded user. */
public final class AdminSession {

    static final String SESSION_USER_ID = "focusflow.admin.userId";
    static final String REQUEST_USER = "focusflow.admin.user";

    private AdminSession() {
    }

    public static Long userId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (Long) session.getAttribute(SESSION_USER_ID);
    }

    /** The user the interceptor verified for this request. Only valid behind {@link AdminAuthInterceptor}. */
    public static AppUser user(HttpServletRequest request) {
        return (AppUser) request.getAttribute(REQUEST_USER);
    }

    public static void signIn(HttpServletRequest request, AppUser user) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId(); // a new id at sign-in, so a planted session id is useless
        }
        request.getSession(true).setAttribute(SESSION_USER_ID, user.getId());
    }

    public static void signOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
