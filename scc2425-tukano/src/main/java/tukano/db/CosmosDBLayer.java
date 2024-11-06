package tukano.db;

import java.util.List;
import java.util.function.Supplier;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;

public class CosmosDBLayer {
	private static final String CONNECTION_URL = System.getProperty("COSMOSDB_URL");
	private static final String DB_KEY = System.getProperty("COSMOSDB_KEY");
	private static final String DB_NAME = System.getProperty("COSMOSDB_DATABASE");
	private static final String USERS_CONTAINER = "users";
	private static final String SHORTS_CONTAINER = "shorts";
	private static final String LIKES_CONTAINER = "likes";
	private static final String FOLLOWS_CONTAINER = "following";


	private static CosmosDBLayer instance;

	public static synchronized CosmosDBLayer getInstance() {
		if (instance != null)
			return instance;

		CosmosClient client = new CosmosClientBuilder()
		         .endpoint(CONNECTION_URL)
		         .key(DB_KEY)
		         .directMode()
		         .consistencyLevel(ConsistencyLevel.SESSION)
		         .connectionSharingAcrossClientsEnabled(true)
		         .contentResponseOnWriteEnabled(true)
		         .buildClient();
		instance = new CosmosDBLayer(client);
		return instance;	
	}
	
	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer usersContainer;
	private CosmosContainer shortsContainer;
	private CosmosContainer likesContainer;
	private CosmosContainer followsContainer;
	
	public CosmosDBLayer(CosmosClient client) {
		this.client = client;
	}
	
	private synchronized void init() {
		if (db != null)
			return;
		db = client.getDatabase(DB_NAME);
		usersContainer = db.getContainer(USERS_CONTAINER);
		shortsContainer = db.getContainer(SHORTS_CONTAINER);
		likesContainer = db.getContainer(LIKES_CONTAINER);
		followsContainer = db.getContainer(FOLLOWS_CONTAINER);
	}

	public void close() {
		client.close();
	}

	// User functions
	public <T> Result<T> getUser(String id, Class<T> clazz) {
		return tryCatch(() -> usersContainer.readItem(id, new PartitionKey(id), clazz).getItem());
	}
	
	public <T> Result<?> deleteUser(T obj) {
		return tryCatch(() -> usersContainer.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
	}
	
	public <T> Result<T> updateUser(T obj) {
		return tryCatch(() -> usersContainer.upsertItem(obj).getItem());
	}
	
	public <T> Result<T> insertUser(T obj) {
		return tryCatch(() -> usersContainer.createItem(obj).getItem());
	}
	
	public <T> Result<List<T>> queryUsers(Class<T> clazz, String queryStr) {
		return tryCatch(() -> {
			var res = usersContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
			return res.stream().toList();
		});
	}

	// Shorts functions
	public <T> Result<T> getShort(String id, Class<T> clazz) {
		return tryCatch(() -> shortsContainer.readItem(id, new PartitionKey(id), clazz).getItem());
	}
	
	public <T> Result<?> deleteShort(T obj) {
		return tryCatch(() -> shortsContainer.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
	}
	
	public <T> Result<T> updateShort(T obj) {
		return tryCatch(() -> shortsContainer.upsertItem(obj).getItem());
	}
	
	public <T> Result<T> insertShort(T obj) {
		return tryCatch(() -> shortsContainer.createItem(obj).getItem());
	}
	
	public <T> Result<List<T>> queryShorts(Class<T> clazz, String queryStr) {
		return tryCatch(() -> {
			var res = shortsContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
			return res.stream().toList();
		});
	}

	public <T> Result<T> insertFollow(T obj) {
		return tryCatch(() -> followsContainer.createItem(obj).getItem());
	}

	public <T> Result<?> deleteFollow(T obj) {
		return tryCatch(() -> followsContainer.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
	}
	
	public <T> Result<List<T>> queryFollows(Class<T> clazz, String queryStr) {
		return tryCatch(() -> {
			var res = followsContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
			return res.stream().toList();
		});
	}

	public <T> Result<T> insertLike(T obj) {
		return tryCatch(() -> likesContainer.createItem(obj).getItem());
	}

	public <T> Result<?> deleteLike(T obj) {
		return tryCatch(() -> likesContainer.deleteItem(obj, new CosmosItemRequestOptions()).getItem());
	}
	
	public <T> Result<List<T>> queryLikes(Class<T> clazz, String queryStr) {
		return tryCatch(() -> {
			var res = likesContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
			return res.stream().toList();
		});
	}



	<T> Result<T> tryCatch(Supplier<T> supplierFunc) {
		try {
			init();
			return Result.ok(supplierFunc.get());			
		} catch (CosmosException ce) {
			ce.printStackTrace();
			return Result.error(errorCodeFromStatus(ce.getStatusCode()));		
		} catch (Exception x) {
			x.printStackTrace();
			return Result.error(ErrorCode.INTERNAL_ERROR);						
		}
	}
	
	static Result.ErrorCode errorCodeFromStatus(int status) {
		return switch (status) {
			case 200 -> ErrorCode.OK;
			case 404 -> ErrorCode.NOT_FOUND;
			case 409 -> ErrorCode.CONFLICT;
			default -> ErrorCode.INTERNAL_ERROR;
		};
	}
}
