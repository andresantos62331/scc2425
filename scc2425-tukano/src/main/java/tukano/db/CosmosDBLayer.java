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

import io.github.cdimascio.dotenv.Dotenv;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;

public class CosmosDBLayer {
    static Dotenv dotenv = Dotenv.configure().load(); // load .env file
    private static final String CONNECTION_URL = dotenv.get("COSMOSDB_URL");
	private static final String DB_KEY = dotenv.get("COSMOSDB_KEY");
	private static final String DB_NAME = dotenv.get("COSMOSDB_DATABASE");
	private static final String USERS_CONTAINER = "users";
	private static final String SHORTS_CONTAINER = "shorts";
	
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
	
	public CosmosDBLayer(CosmosClient client) {
		this.client = client;
	}
	
	private synchronized void init() {
		if (db != null)
			return;
		db = client.getDatabase(DB_NAME);
		usersContainer = db.getContainer(USERS_CONTAINER);
		shortsContainer = db.getContainer(SHORTS_CONTAINER);
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
