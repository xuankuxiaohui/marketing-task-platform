package com.mkt.identity.application;

import com.mkt.identity.query.AuditQuery;
import com.mkt.identity.response.AuditView;
import com.mkt.kernel.PageData;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryAppService {

    private static final RowMapper<AuditView> ROW = (rs, rowNum) -> new AuditView(
            rs.getLong("id"),
            rs.getString("module"),
            rs.getString("action"),
            rs.getObject("operator_id") == null ? null : rs.getLong("operator_id"),
            rs.getString("operator_name"),
            rs.getString("ip"),
            rs.getString("user_agent"),
            rs.getString("request_summary"),
            rs.getString("result"),
            rs.getString("error_message"),
            rs.getObject("cost_ms") == null ? null : rs.getInt("cost_ms"),
            rs.getString("trace_id"),
            toInstant(rs.getTimestamp("created_at")));

    private final JdbcTemplate jdbc;

    public AuditQueryAppService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageData<AuditView> page(AuditQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (query.operatorId() != null) {
            where.append(" AND operator_id = ?");
            args.add(query.operatorId());
        }
        if (notBlank(query.module())) {
            where.append(" AND module = ?");
            args.add(query.module().trim());
        }
        if (notBlank(query.action())) {
            where.append(" AND action = ?");
            args.add(query.action().trim());
        }
        if (notBlank(query.result())) {
            where.append(" AND result = ?");
            args.add(query.result().trim());
        }
        if (query.from() != null) {
            where.append(" AND created_at >= ?");
            args.add(Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            where.append(" AND created_at <= ?");
            args.add(Timestamp.from(query.to()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_audit_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.page().pageSize());
        pageArgs.add(query.page().offset());
        List<AuditView> rows = jdbc.query(
                "SELECT id, module, action, operator_id, operator_name, ip, user_agent, request_summary, result,"
                        + " error_message, cost_ms, trace_id, created_at FROM sys_audit_log"
                        + where
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW,
                pageArgs.toArray());
        return new PageData<>(total == null ? 0L : total, rows);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
