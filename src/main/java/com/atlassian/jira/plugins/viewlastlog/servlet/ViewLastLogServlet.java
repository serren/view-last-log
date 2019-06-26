package com.atlassian.jira.plugins.viewlastlog.servlet;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.util.JiraHome;
import com.atlassian.jira.plugins.viewlastlog.exception.JiraVersionIsNotSupportedException;
import com.atlassian.jira.plugins.viewlastlog.exception.NoPermissionException;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.ApplicationUsers;
import com.atlassian.jira.util.BuildUtilsInfo;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.util.concurrent.Assertions;

public class ViewLastLogServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger log = Logger
            .getLogger(ViewLastLogServlet.class);

    private final String TEMPLATE_PATH = "/templates/viewLastLog.vm";
    private final String JIRA_VIEW_LOG_USERS = "jira-view-log-users";
    private final String CONTENT_TYPE = "text/html;charset=utf-8";
    private final int MAX_ENTRIES = 50;
    private final int JIRA_MAX_VERSION = 8;

    private final UserManager userManager;
    private final LoginUriProvider loginUriProvider;
    private final TemplateRenderer templateRenderer;
    private final GroupManager groupManager;

    public ViewLastLogServlet(final UserManager userManager,
            final LoginUriProvider loginUriProvider,
            final TemplateRenderer templateRenderer,
            final GroupManager groupManager) {
        this.userManager = Assertions.notNull("userManager", userManager);
        this.loginUriProvider = Assertions.notNull("loginUriProvider",
                loginUriProvider);
        this.templateRenderer = Assertions.notNull("templateRenderer",
                templateRenderer);
        this.groupManager = Assertions.notNull("groupManager", groupManager);
        log.setLevel(Level.ERROR);
    }

    @Override
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        UserProfile up = userManager.getRemoteUser(request);
        if (up == null) {
            redirectToLogin(request, response);
            return;
        }
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("i18n", ComponentAccessor.getI18nHelperFactory()
                .getInstance(getCurrentUser()));
        try {
            String userName = getCurrentUser().getName();
            if (!groupManager.isUserInGroup(ApplicationUsers.byKey(userName),
                    JIRA_VIEW_LOG_USERS)
                    && !userManager.isSystemAdmin(up.getUserKey())) {
                throw new NoPermissionException(
                        getI18Helper()
                                .getText(
                                        "com.atlassian.jira.plugins.viewlastlog.gui.permissionError"));
            }
            BuildUtilsInfo bi = (BuildUtilsInfo) ComponentAccessor
                    .getComponent(BuildUtilsInfo.class);
            if (bi.getVersionNumbers()[0] > JIRA_MAX_VERSION) {
                throw new JiraVersionIsNotSupportedException(
                        getI18Helper()
                                .getText(
                                        "com.atlassian.jira.plugins.viewlastlog.gui.versionError"));
            }
            String debugParam = request.getParameter("debug");
            if (log.isDebugEnabled()) {
                log.debug((new StringBuilder()).append("Debug: ")
                        .append(debugParam).toString());
            }
            if (debugParam != null && Boolean.parseBoolean(debugParam)) {
                log.setLevel(Level.DEBUG);
            } else {
                log.setLevel(Level.ERROR);
            }

            String maxEntriesParam = request.getParameter("maxEntries");
            int maxEntires = MAX_ENTRIES;
            if (log.isDebugEnabled())
                log.debug((new StringBuilder()).append("MaxEntriesParam: ")
                        .append(maxEntriesParam).toString());
            if (maxEntriesParam != null) {
                maxEntires = Integer.parseInt(maxEntriesParam);
                if (maxEntires < 0) {
                    throw new IllegalArgumentException(
                            getI18Helper()
                                    .getText(
                                            "com.atlassian.jira.plugins.viewlastlog.gui.negativeNumberError",
                                            maxEntriesParam));
                }
            }

            String queryParam = request.getParameter("query");
            if (log.isDebugEnabled())
                log.debug((new StringBuilder()).append("QueryParam: ")
                        .append(queryParam).toString());
            String query = StringUtils.EMPTY;
            if (StringUtils.isNotEmpty(queryParam))
                query = queryParam;

            String logFileParam = request.getParameter("logFile");
            if (StringUtils.isNotBlank(logFileParam)) {
                logFileParam = logFileParam.trim().toLowerCase();
            }
            if (log.isDebugEnabled())
                log.debug((new StringBuilder()).append("logFileParam: ")
                        .append(logFileParam).toString());

            List<String> logFiles = getAvailableLogFiles();
            List<String> logRecords = new ArrayList<String>();
            if (logFiles.contains(logFileParam)) {
                if (maxEntires > 0) {
                    logRecords = getLogRecords(query, logFileParam, maxEntires);
                }
            }
            context.put("logFile", logFileParam);
            context.put("log", logRecords);
            context.put("maxEntries", Integer.valueOf(maxEntires));
            context.put("query", query);
            context.put("logFiles", logFiles);
        } catch (Exception e) {
            createErrorContext(
                    getI18Helper()
                            .getText(
                                    "com.atlassian.jira.plugins.viewlastlog.gui.commonError",
                                    e.getMessage()), context);
        }
        response.setContentType(CONTENT_TYPE);
        templateRenderer.render(TEMPLATE_PATH, context, response.getWriter());
    }

    private void redirectToLogin(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        response.sendRedirect(loginUriProvider.getLoginUri(getUri(request))
                .toASCIIString());
    }

    private URI getUri(HttpServletRequest request) {
        StringBuffer builder = request.getRequestURL();
        if (request.getQueryString() != null) {
            builder.append("?");
            builder.append(request.getQueryString());
        }
        return URI.create(builder.toString());
    }

    private void createErrorContext(String errorMessage,
            Map<String, Object> context) {
        context.put("error", Boolean.valueOf(true));
        context.put("errorMessage", errorMessage);
    }

    private List<String> getLogRecords(String query, String logFile,
            int maxEntires) throws IOException {
        List<String> logRecords = new ArrayList<String>();
        ReversedLinesFileReader br = null;
        try {
            File file = new File((new StringBuilder())
                    .append(getJiraHome().getLogDirectory().getAbsolutePath())
                    .append("/").append(logFile).toString());
            br = new ReversedLinesFileReader(file);
            for (String line = br.readLine(); line != null; line = br
                    .readLine()) {
                if (logRecords.size() > maxEntires) {
                    break;
                }
                if (StringUtils.isNotBlank(query)) {
                    if (line.toLowerCase().contains(query.toLowerCase())) {
                        logRecords.add(line);
                    }
                } else {
                    logRecords.add(line);
                }
            }
        } finally {
            if (br != null) {
                br.close();
            }
        }
        Collections.reverse(logRecords);
        return logRecords;
    }

    private List<String> getAvailableLogFiles() {
        List<String> logFiles = new ArrayList<String>();
        File[] children = getJiraHome().getLogDirectory().listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isFile())
                    logFiles.add(child.getName().toLowerCase());
            }
        }
        return logFiles;
    }

    private JiraHome getJiraHome() {
        return (JiraHome) ComponentAccessor.getComponent(JiraHome.class);
    }

    private ApplicationUser getCurrentUser() {
        return ComponentAccessor.getJiraAuthenticationContext()
                .getLoggedInUser();
    }

    private I18nHelper getI18Helper() {
        return ComponentAccessor.getJiraAuthenticationContext().getI18nHelper();
    }
}