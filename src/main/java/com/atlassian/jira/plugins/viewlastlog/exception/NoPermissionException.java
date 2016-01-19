package com.atlassian.jira.plugins.viewlastlog.exception;

public class NoPermissionException extends Exception {

    private static final long serialVersionUID = 1L;

    public NoPermissionException(String arg0) {
        super(arg0);
    }
}