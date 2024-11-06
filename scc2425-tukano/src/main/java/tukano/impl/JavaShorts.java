package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import cache.RedisCache;
import redis.clients.jedis.Jedis;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import utils.JSON;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	synchronized public static Shorts getInstance() {
		if (instance == null)
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {
	}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult(okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null)
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			// Try fetching the short from cache
			var shortKey = "short:" + shortId;
			String cachedShort = jedis.get(shortKey);
			if (cachedShort != null) {
				Short short1 = JSON.decode(cachedShort, Short.class);
				return ok(short1);
			}

			var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
			var likes = DB.sql(query, Long.class);
			return errorOrValue((Result<Short>) DB.getOne(shortId, Short.class), shrt -> {
				var resultShort = shrt.copyWithLikes_And_Token(likes.get(0));
				jedis.set(shortKey, JSON.encode(resultShort)); // Populate cache with the fetched user
				return resultShort;
			});
		}
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
				return DB.transaction(hibernate -> {

					hibernate.remove(shrt);

					var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
					hibernate.createNativeQuery(query, Likes.class).executeUpdate();

					JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());

					// Invalidate cache for deleted short
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						jedis.del("short:" + shortId);
					}
				});
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var cacheKey = "user_shorts:" + userId;

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			// Attempt to user's shorts from cache
			var cachedShorts = jedis.get(cacheKey);
			if (cachedShorts != null) {
				return ok(JSON.decodeList(cachedShorts, String.class));
			}

			var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
			var shortIds = DB.sql(String.class, query);
			jedis.set(cacheKey, JSON.encode(shortIds));
			return errorOrValue(okUser(userId), shortIds);
		}
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2,
				isFollowing, password));

		return errorOrResult(okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		String cacheKey = "followers:" + userId;

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			// Try fetching followers from cache
			String cachedFollowers = jedis.get(cacheKey);
			if (cachedFollowers != null) {
				return ok(JSON.decodeList(cachedFollowers, String.class));
			}

			// Fetch from database if not in cache
			var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
			Result<List<String>> followers = errorOrValue(okUser(userId, password), DB.sql(query, String.class));

			// Store result in cache if found
			if (followers.isOK()) {
				jedis.set(cacheKey, JSON.encode(followers.value()));
			}
			return followers;
		}
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
				password));

		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		String cacheKey = "likes:" + shortId;

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			// Try fetching likes from cache
			String cachedLikes = jedis.get(cacheKey);
			if (cachedLikes != null) {
				return ok(JSON.decodeList(cachedLikes, String.class));
			}

			// Fetch from database if not in cache
			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);
			Result<List<String>> likes = errorOrValue(okUser(shortId, password), DB.sql(query, String.class));

			// Store result in cache if found
			if (likes.isOK()) {
				jedis.set(cacheKey, JSON.encode(likes.value()));
			}
			return likes;
		}
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		String cacheKey = "feed:" + userId;

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			// Try fetching feed from cache
			String cachedFeed = jedis.get(cacheKey);
			if (cachedFeed != null) {
				return ok(JSON.decodeList(cachedFeed, String.class));
			}

			// Fetch from database if not in cache
			final var QUERY_FMT = """
					SELECT s.shortId, s.timestamp FROM Shorts s WHERE s.ownerId = '%s'
					UNION
					SELECT s.shortId, s.timestamp FROM Shorts s, Following f
						WHERE
							f.followee = s.ownerId AND f.follower = '%s'
					ORDER BY s.timestamp DESC""";
			Result<List<String>> feed = errorOrValue(okUser(userId, password),
					DB.sql(format(QUERY_FMT, userId, userId), String.class));

			// Store result in cache if found
			if (feed.isOK()) {
				jedis.set(cacheKey, JSON.encode(feed.value()));
			}
			return feed;
		}
	}

	protected Result<User> okUser(String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser(String userId) {
		var res = okUser(userId, "");
		if (res.error() == FORBIDDEN)
			return ok();
		else
			return error(res.error());
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if (!Token.isValid(token, userId))
			return error(FORBIDDEN);

		return DB.transaction(hibernate -> {
			// Step 1: Retrieve all short IDs before deletion to clear the cache
			var shortsQuery = format("SELECT s.shortId FROM Shorts s WHERE s.ownerId = '%s'", userId);
			List<String> shortIds = DB.sql(String.class, shortsQuery);

			// Step 2: Invalidate the cache for each short before deleting from the database
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				for (String shortId : shortIds) {
					// Remove each short's cache and its likes cache
					jedis.del("short:" + shortId);
					jedis.del("likes:" + shortId);
				}
			}
   
			// Step 3: Delete all shorts for the user from the database
			var query1 = format("DELETE FROM Shorts s WHERE s.ownerId = '%s'", userId);
			hibernate.createQuery(query1, Short.class).executeUpdate();

			// Step 4: Delete all follow records associated with the user
			var query2 = format("DELETE FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
			hibernate.createQuery(query2, Following.class).executeUpdate();

			// Step 5: Delete all like records associated with the user
			var query3 = format("DELETE FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
			hibernate.createQuery(query3, Likes.class).executeUpdate();

			// Step 6: Invalidate other related cache entries
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				// Delete user's shorts cache
				jedis.del("user_shorts:" + userId);

				// Delete cache for the user's feed
				jedis.del("feed:" + userId);

				// Delete cache for followers
				jedis.del("followers:" + userId);
			}
		});
	}
}