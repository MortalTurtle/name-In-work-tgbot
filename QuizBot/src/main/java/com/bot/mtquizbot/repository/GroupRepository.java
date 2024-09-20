package com.bot.mtquizbot.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.bot.mtquizbot.models.Role;
import com.bot.mtquizbot.models.TestGroup;
import com.bot.mtquizbot.models.User;
import com.bot.mtquizbot.models.mapper.TestGroupMapper;

@Repository
public class GroupRepository implements IGroupRepository {

    private static final String SQL_SELECT_BY_ID = "" + 
        "SELECT * FROM quizdb.groups WHERE id = ?";
    private static final String SQL_INSERT = "" +
        "INSERT INTO quizdb.groups (id, name, description) VALUES (?, ?, ?)";
    private static final String SQL_ADD_ROLE = "" +
        "INSERT INTO quizdb.group_users (group_id, user_id, group_role_id) VALUES(?, ?, ?)" +
        "ON CONFLICT(group_id, user_id) DO UPDATE SET group_role_id = $3";

    protected final static TestGroupMapper mapper = new TestGroupMapper();
    protected final JdbcTemplate template;

    public GroupRepository(@Qualifier("bot-db") JdbcTemplate template) {
        this.template = template;
    }

    @Override
    public TestGroup getById(String id) {
        return DataAccessUtils.singleResult(
            template.query(SQL_SELECT_BY_ID, mapper, id)
        );
    }

    @Override
    public void insert(TestGroup entity) {
        var result = template.update(SQL_INSERT,
            entity.getId(), 
            entity.getName(),
            entity.getDescription());
    }

    @Override
    public void delete(TestGroup entity) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void addUserRole(TestGroup group, User user, Role role) {
        var result = template.update(SQL_ADD_ROLE,
        group.getId(),
        user.getId(),
        role.getId());
    }

}
