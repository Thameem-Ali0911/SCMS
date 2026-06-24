package com.scms.common;

/**
 * Roles — centralised role-name constants.
 *
 * MENTOR NOTE — why this class exists (v2.0 production hardening):
 * The v1.3 codebase scattered the literal strings "ROLE_ADMIN", "ADMIN",
 * "USER" etc. across AdminController, ComplaintController, AdminService,
 * and SecurityConfig. A typo in any one of those locations (e.g. "ADMN")
 * fails silently — Spring Security just never matches that rule, which is
 * a much harder bug to find than a compile error.
 *
 * By centralising the values here, every reference is one symbol that the
 * compiler checks. Renaming a role becomes a one-line change instead of a
 * project-wide grep-and-pray.
 *
 * Role names themselves are still stored as data in the `roles` table
 * (Open/Closed Principle — see Role.java) — this class only centralises
 * the well-known constant role names the application logic depends on.
 */
public final class Roles {

    private Roles() {
        // constants holder — never instantiated
    }

    public static final String USER  = "USER";
    public static final String STAFF = "STAFF";
    public static final String ADMIN = "ADMIN";

    public static final String ROLE_USER  = "ROLE_USER";
    public static final String ROLE_STAFF = "ROLE_STAFF";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** All role names that are valid targets for AdminService.changeUserRole(). */
    public static final String[] ASSIGNABLE = { USER, STAFF, ADMIN };

    public static boolean isValid(String roleName) {
        if (roleName == null) return false;
        String upper = roleName.toUpperCase();
        for (String r : ASSIGNABLE) {
            if (r.equals(upper)) return true;
        }
        return false;
    }
}
