package com.scms.common;

/**
 * AuditActions — centralised audit "action" string constants.
 *
 * MENTOR NOTE — why this matters for AuditLog specifically:
 * AuditLog.action is a free-text column ("CREATE", "STATUS_CHANGE", …).
 * The v1.3 codebase wrote these as inline literals in ComplaintService.
 * A single typo ("STAUS_CHANGE") would silently corrupt the audit trail —
 * exactly the failure mode the v1.3 report called out under Auditability.
 * Centralising them here gives compile-time safety for an otherwise
 * untyped column.
 */
public final class AuditActions {

    private AuditActions() {}

    public static final String CREATE        = "CREATE";
    public static final String UPDATE        = "UPDATE";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ASSIGN        = "ASSIGN";
    public static final String SOFT_DELETE   = "SOFT_DELETE";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGOUT        = "LOGOUT";
    public static final String ROLE_CHANGE   = "ROLE_CHANGE";
    public static final String STATUS_TOGGLE = "STATUS_TOGGLE";
}
