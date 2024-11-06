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

import cache.RedisCache;
import redis.clients.jedis.Jedis;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import utils.JSON;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (badUserInfo(user))
			return error(BAD_REQUEST);

		return errorOrValue(DB.insertOne(user), user.getUserId());
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			var userKey = "user:" + userId;
			String cachedUser = jedis.get(userKey);
			if (cachedUser != null) {
				User user = JSON.decode(cachedUser, User.class);
				if (user.getPwd().equals(pwd)) {
					return ok(user);
				} else {
					return error(FORBIDDEN);
				}
			}

			Result<User> dbResult = DB.getOne(userId, User.class);
			if (dbResult.isOK()) {
				User user = dbResult.value();
				if (user.getPwd().equals(pwd)) {
					jedis.set(userKey, JSON.encode(user));
					return ok(user);
				} else {
					return error(FORBIDDEN);
				}
			}

			return dbResult;
		}
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
			Result<User> updatedUser = DB.updateOne(user.updateFrom(other));
			if (updatedUser.isOK()) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var userKey = "user:" + userId;
					jedis.del(userKey); // Invalidate cache for updated user
				}
			}
			return updatedUser;
		});
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null)
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			Result<User> result = DB.deleteOne(user);

			// Invalidate cache for the deleted user
			if (result.isOK()) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var userKey = "user:" + userId;
					jedis.del(userKey); // Delete the cache entry for the user
				}
			}

			return result;
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : patterns = %s\n", pattern));

		//String cacheKey = "search:" + pattern.toUpperCase();

        /*try (Jedis jedis = RedisCache.getCachePool().getResource()) {
            // Check cache
            String cachedResult = jedis.get(cacheKey);
            if (cachedResult != null) {
                List<User> users = JSON.decodeList(cachedResult, User.class);
                return ok(users);
            }
        }*/

        // Query database if not in cache
        var query = format("SELECT * FROM Users u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
        var hits = DB.sql(query, User.class)
                .stream()
                .map(User::copyWithoutPassword)
                .toList();

        /*try (Jedis jedis = RedisCache.getCachePool().getResource()) {
            // Cache result in Redis for future use
            jedis.set(cacheKey, JSON.encode(hits));
        }*/

        return ok(hits);
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

	private boolean badUpdateUserInfo(String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && !userId.equals(info.getUserId()));
	}
}