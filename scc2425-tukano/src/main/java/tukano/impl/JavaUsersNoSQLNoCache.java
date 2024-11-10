package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.db.CosmosDBLayer;

public class JavaUsersNoSQLNoCache implements Users {

    private static Logger Log = Logger.getLogger(JavaUsersNoSQLNoCache.class.getName());

    private static Users instance;
    private CosmosDBLayer dbLayer;

    synchronized public static Users getInstance() {
        if (instance == null)
            instance = new JavaUsersNoSQLNoCache();
        return instance;
    }

    private JavaUsersNoSQLNoCache() {
        dbLayer = CosmosDBLayer.getInstance();
    }

    @Override
    public Result<String> createUser(User user) {
        Log.info(() -> format("createUser : %s\n", user));

        if (badUserInfo(user))
            return error(BAD_REQUEST);

        return errorOrValue(dbLayer.insertUser(user), user.getUserId());
    }

    @Override
    public Result<User> getUser(String id, String pwd) {
        Log.info(() -> format("getUser : id = %s, pwd = %s\n", id, pwd));

        if (id == null)
            return error(BAD_REQUEST);

        Result<User> dbResult = dbLayer.getUser(id, User.class);
        if (dbResult.isOK()) {
            User user = dbResult.value();
            if (user.getPwd().equals(pwd)) {
                return ok(user);
            } else {
                return error(FORBIDDEN);
            }
        }

        return dbResult;
    }

    @Override
    public Result<User> updateUser(String id, String pwd, User other) {
        Log.info(() -> format("updateUser : id = %s, pwd = %s, user: %s\n", id, pwd, other));

        if (badUpdateUserInfo(id, pwd, other))
            return error(BAD_REQUEST);

        return errorOrResult(validatedUserOrError(dbLayer.getUser(id, User.class), pwd), user -> {
            Result<User> updatedUser = dbLayer.updateUser(user.updateFrom(other));
            return updatedUser;
        });
    }

    @Override
    public Result<User> deleteUser(String id, String pwd) {
        Log.info(() -> format("deleteUser : id = %s, pwd = %s\n", id, pwd));

        if (id == null || pwd == null)
            return error(BAD_REQUEST);

        return errorOrResult(validatedUserOrError(dbLayer.getUser(id, User.class), pwd), user -> {

            // Delete user shorts and related info asynchronously in a separate thread
            Executors.defaultThreadFactory().newThread(() -> {
                JavaShortsNoSQL.getInstance().deleteAllShorts(id, pwd, Token.get(id));
                JavaBlobs.getInstance().deleteAllBlobs(id, Token.get(id));
            }).start();

            // Delete the user from the database
            @SuppressWarnings("unchecked")
            Result<User> result = (Result<User>) dbLayer.deleteUser(user);

            return result;
        });
    }

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        Log.info(() -> format("searchUsers : pattern = %s\n", pattern));

        // Query database directly without caching
        var query = format("SELECT * FROM Users u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
        var hits = dbLayer.queryUsers(User.class, query);
        Log.info("hits = " + hits);
        Log.info("hits.v = " + hits.value());

        return ok(hits.value());
    }

    private Result<User> validatedUserOrError(Result<User> res, String pwd) {
        if (res.isOK())
            return res.value().getPwd().equals(pwd) ? res : error(FORBIDDEN);
        else
            return res;
    }

    private boolean badUserInfo(User user) {
        return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
    }

    private boolean badUpdateUserInfo(String id, String pwd, User info) {
        return (id == null || pwd == null || info.getUserId() != null && !id.equals(info.getUserId()));
    }
}
