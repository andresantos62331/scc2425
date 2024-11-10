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

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.JSON;

import tukano.db.CosmosDBLayer; 


public class JavaShortsNoSQLNoCache implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShortsNoSQLNoCache.class.getName());

	private static Shorts instance;
	private CosmosDBLayer dbLayer;

	synchronized public static Shorts getInstance() {
		if (instance == null)
			instance = new JavaShortsNoSQLNoCache();
		return instance;
	}

	private JavaShortsNoSQLNoCache() {
		dbLayer = CosmosDBLayer.getInstance();
	}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult(okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(dbLayer.insertShort(shrt), s -> s.copyWithLikes_And_Token(0));
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

			var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
			var likes = dbLayer.queryLikes(Likes.class, query).value().size();

			return errorOrValue( (Result<Short>) dbLayer.getShort(shortId, Short.class), shrt -> {
				var resultShort = shrt.copyWithLikes_And_Token(likes);
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

					dbLayer.deleteShort(shrt);

					var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
					dbLayer.queryLikes(Likes.class,query);

					JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());

					// Invalidate cache for deleted short
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						jedis.del("short:" + shortId);
					}

					return ok();
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

			var query = format("SELECT s.shortId FROM Shorts s WHERE s.ownerId = '%s'", userId);
			var shortIds = dbLayer.queryShorts(String.class, query);
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
			return errorOrVoid(okUser(userId2), isFollowing ? dbLayer.insertFollow(f) : dbLayer.deleteFollow(f));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));
		
		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
		return errorOrValue(okUser(userId, password), dbLayer.queryFollows(String.class, query));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked,
				password));

		return errorOrResult(getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid(okUser(userId, password), isLiked ? dbLayer.insertLike(l) : dbLayer.deleteLike(l));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

			return errorOrValue(okUser(shrt.getOwnerId(), password), dbLayer.queryLikes(String.class, query));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Shorts s WHERE	s.ownerId = '%s'
				UNION
				SELECT s.shortId, s.timestamp FROM Shorts s, Following f
					WHERE
						f.followee = s.ownerId AND f.follower = '%s'
				ORDER BY s.timestamp DESC""";

		return errorOrValue(okUser(userId, password), dbLayer.queryShorts(String.class, format(QUERY_FMT, userId, userId)));
	}

	protected Result<User> okUser(String userId, String pwd) {
		return JavaUsersNoSQL.getInstance().getUser(userId, pwd);
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

			// delete shorts
			var query1 = format("DELETE Shorts s WHERE s.ownerId = '%s'", userId);
			dbLayer.queryShorts(Short.class, query1);

			// delete follows
			var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
			dbLayer.queryFollows(Following.class, query2);

			// delete likes
			var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
			dbLayer.queryLikes(Likes.class, query3);

			return ok();
	}

}