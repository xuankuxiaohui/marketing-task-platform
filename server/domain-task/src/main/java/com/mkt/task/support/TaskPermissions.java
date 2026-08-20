package com.mkt.task.support;

/** Appendix B task permission codes (design §4.4). */
public final class TaskPermissions {

    public static final String DEFINITION_CREATE = "task:definition:create";
    public static final String DEFINITION_UPDATE = "task:definition:update";
    public static final String DEFINITION_COPY = "task:definition:copy";
    public static final String DEFINITION_DELETE = "task:definition:delete";
    public static final String DEFINITION_QUERY = "task:definition:query";
    public static final String DEFINITION_PUBLISH = "task:definition:publish";
    public static final String DEFINITION_SCHEDULE = "task:definition:schedule";
    public static final String DEFINITION_OFFLINE = "task:definition:offline";
    public static final String EXPRESSION_VALIDATE = "task:expression:validate";
    public static final String MUTEX_QUERY = "task:mutex-group:query";
    public static final String MUTEX_CREATE = "task:mutex-group:create";
    public static final String MUTEX_UPDATE = "task:mutex-group:update";
    public static final String MUTEX_DELETE = "task:mutex-group:delete";
    public static final String CROWD_QUERY = "task:crowd:query";
    public static final String CROWD_CREATE = "task:crowd:create";
    public static final String CROWD_UPDATE = "task:crowd:update";
    public static final String CROWD_DELETE = "task:crowd:delete";
    public static final String INSTANCE_QUERY = "task:instance:query";
    public static final String INSTANCE_ABANDON = "task:instance:abandon";

    private TaskPermissions() {}
}
