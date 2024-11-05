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
import utils.JSON;

import tukano.db.CosmosDBLayer; 



public class JavaUsersNoSQL implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;
	private CosmosDBLayer dbLayer;

	synchronized public static Users getInstance() {
		if (instance == null)
			instance = new JavaUsersNoSQL();
		return instance;
	}

	private JavaUsersNoSQL() {
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

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			var userKey = "user:" + id;
			String cachedUser = jedis.get(userKey);
			if (cachedUser != null) {
				User user = JSON.decode(cachedUser, User.class);
				if (user.getPwd().equals(pwd)) {
					return ok(user);
				} else {
					return error(FORBIDDEN);
				}
			}

			Result<User> dbResult = dbLayer.getUser(id, User.class);
			if (dbResult.isOK()) {
				User user = dbResult.value();
				if (user.getPwd().equals(pwd)) {
					jedis.set(userKey,JSON.encode(user)); 
					return ok(user);
				} else {
					return error(FORBIDDEN);
				}
			}

			return dbResult;
		}

	}

	@Override
	public Result<User> updateUser(String id, String pwd, User other) {
		Log.info(() -> format("updateUser : id = %s, pwd = %s, user: %s\n", id, pwd, other));

		if (badUpdateUserInfo(id, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(dbLayer.getUser(id, User.class), pwd), user -> {
			Result<User> updatedUser = dbLayer.updateUser(user.updateFrom(other));
			if (updatedUser.isOK()) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var userKey = "user:" + id;
					jedis.del(userKey); // Invalidate cache for updated user
					//jedis.set(userKey, JSON.encode(updatedUser.value())); // Repopulate cache with updated user
				}
			}
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
				JavaShorts.getInstance().deleteAllShorts(id, pwd, Token.get(id));
				JavaBlobs.getInstance().deleteAllBlobs(id, Token.get(id));
			}).start();

			// Delete the user from the database
			@SuppressWarnings("unchecked")
			Result<User> result = (Result<User>) dbLayer.deleteUser(user);

			// Invalidate cache for the deleted user
			if (result.isOK()) {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					var userKey = "user:" + id;
					jedis.del(userKey); // Delete the cache entry for the user
				}
			}
	
			return result;
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : pattern = %s\n", pattern));

		String cacheKey = "search:" + pattern.toUpperCase();					// TODO

		/*try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			// Check cache
			String cachedResult = jedis.get(cacheKey);
			if (cachedResult != null) {
				List<User> users = JSON.decodeList(cachedResult, User.class); // Convert JSON string back to list
				// should see if the method decodeList is done correctly =?
				// System.out.println(users);
				//delete if cache gets easily full and
				return ok(users);
			}
		}*/
		//this is commented because there is probably a better way to work with lists in rediscache

		// Query database if not in cache
		var query = format("SELECT * FROM User u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = dbLayer.queryUsers(User.class, query);
		Log.info("hits = " + hits);
		Log.info("hits.v = " + hits.value());

		/*try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			// Cache result in Redis for future use
			jedis.set(cacheKey, JSON.encode(hits));
		}*/

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
