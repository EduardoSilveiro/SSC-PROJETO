package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import tukano.api.*;
import tukano.api.azure.RedisCache;
import utils.Constants;
import utils.DB;
import java.util.stream.Collectors;
import com.azure.cosmos.models.CosmosItemResponse;
import utils.Hash;
import utils.JSON;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
	private static final String CONNECTION_URL = Constants.eduardoConst.getDbUrl();
	private static final String DB_KEY = Constants.eduardoConst.getDbKey();
	private static final String DB_NAME = Constants.eduardoConst.getDbName();
	private static Users instance;

	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer users;
	private CosmosContainer feeds;
	static RedisCache cache = RedisCache.getInstance();

	public static String DB_MODE = Constants.eduardoConst.getDbMode();
	public static boolean CACHE_MODE = Constants.eduardoConst.isCacheActive();

	public static synchronized Users getInstance() {


		if (instance != null)
			return instance;
		if(DB_MODE.equalsIgnoreCase("post")){
			instance = new JavaUsers();
			return instance;
		}else{
			CosmosClient client = new CosmosClientBuilder()
					.endpoint(CONNECTION_URL)
					.key(DB_KEY)
					// .directMode()
					.gatewayMode()
					// replace by .directMode() for better performance
					.consistencyLevel(ConsistencyLevel.SESSION)
					.connectionSharingAcrossClientsEnabled(true)
					.contentResponseOnWriteEnabled(true)
					.buildClient();
			instance = new JavaUsers(client);
		}
		return instance;

	}

	public JavaUsers(CosmosClient client) {
		this.client = client;
	}
	public JavaUsers(){}
	<T> Result<T> tryCatch( Supplier<T> supplierFunc) {
		try {
			init();
			return Result.ok(supplierFunc.get());
		} catch( CosmosException ce ) {
			//ce.printStackTrace();
			return Result.error(Result.ErrorCode.valueOf(String.valueOf(ce.getStatusCode())));
		} catch( Exception x ) {
			x.printStackTrace();
			return Result.error( Result.ErrorCode.INTERNAL_ERROR);
		}
	}

	private synchronized void init() {
		if (db != null)
			return;
		//db = client.getDatabase(DB_NAME);
		//users = db.getContainer("users");
		users = client.getDatabase(DB_NAME).getContainer("users");

	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if (user.getUserId() == null || user.getPwd() == null || user.getEmail() == null || user.getDisplayName() == null) {
			Log.info("User data is incomplete: " + user);
			return Result.error(Result.ErrorCode.CONFLICT);
		}

		if(DB_MODE.equalsIgnoreCase("post")){
			errorOrValue( DB.insertOne( user), user.getId());
			if (CACHE_MODE) {
				UserDAO userDAO = new UserDAO(user);
				var key1 = "users:" + userDAO.getId();
				var value1 = JSON.encode(userDAO);
				cache.setValue(key1,value1);
				Log.info("Cache data is completed for POSTESQL: " + user);
			}
			return Result.ok(user.getId());
		}else{
			try {
				init();
				UserDAO userDAO = new UserDAO(user);
 				Log.info(() -> format("UserDAO : %s\n", userDAO));



				if (CACHE_MODE) {
					var key1 = "users:" + userDAO.getUserId();
					var value1 = JSON.encode(userDAO);
					cache.setValue(key1,value1);

				}

				return  tryCatch(() -> users.createItem(userDAO).getItem().toString());

			}   catch (CosmosException e) {
				Log.info(() -> format("UserDAO4444 : %s\n" ));
			Log.info("Error creating user: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.FORBIDDEN);
		}
		}
	}
	//NOT TESTED
	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null) {
			return Result.error(Result.ErrorCode.BAD_REQUEST);
		}

		if (CACHE_MODE) {
			var key1 = "users:" + userId;
			var value = cache.getValue(key1,UserDAO.class);
			Log.info("Cache data is completed for POSTESQL: " + value);
			if (!value.getPwd().equals(pwd)) {
				return Result.error(FORBIDDEN);
			}
			User user = new User(value.getUserId(),value.getPwd(),value.getEmail(), value.getDisplayName());
			return Result.ok(user);
		}

		if(DB_MODE.equalsIgnoreCase("post")){
			return validatedUserOrError( DB.getOne( userId, User.class), pwd);
		}else{
			try {
				init();

				CosmosItemResponse<UserDAO> response = users.readItem(userId, new PartitionKey(userId), UserDAO.class);
				UserDAO userDAO = response.getItem();

				if (!userDAO.getPwd().equals(pwd) || userDAO==null ) {
					Log.info("Error getting user122: " + Result.error(Result.ErrorCode.FORBIDDEN)) ;

					return Result.error(Result.ErrorCode.FORBIDDEN ) ;
				}

				User user = new User(userDAO.getUserId(), userDAO.getPwd(), userDAO.getEmail(), userDAO.getDisplayName());
				return Result.ok(user);
			} catch (CosmosException e) {
				Log.info("Error getting user: " + e.getMessage());
				e.printStackTrace();
				return Result.error(Result.ErrorCode.FORBIDDEN);
			}
		}
	}
	// Method to convert UserDAO to User
	private User convertToUser(UserDAO userDAO) {
		return new User(userDAO.getUserId(), userDAO.getPwd(), userDAO.getEmail(), userDAO.getDisplayName());
	}
  @Override
  public Result<User> updateUser(String userId, String pwd, User other) {
	  Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));
	  User user = new User();

	  // Check for invalid input
	  if (badUpdateUserInfo(userId, pwd, other)) {
		  return error(BAD_REQUEST);
	  }
	  Map<String,User> userMap = getFromCache(userId,pwd);


	  if(DB_MODE.equalsIgnoreCase("post")){

		  if (userMap != null) {
 			User postUser =  userMap.get("users:" + userId);
			  cache.setValue("users:" + userId, postUser.updateFrom(other));
			  Log.info("Cache data is completed for NO SQL: " + user);

			  return Result.ok(DB.updateOne( postUser.updateFrom(other)).value() ) ;
		  }
		  return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user1 ->
				  DB.updateOne( user1.updateFrom(other)));
	  }
	  try {

		  // Initialize Cosmos client if needed
		  init();
 			if (user != null) {

			  UserDAO newUserDAO = new UserDAO(other);

			  CosmosItemResponse<UserDAO> updatedResponse = users.replaceItem(newUserDAO, userId, new PartitionKey(userId), new CosmosItemRequestOptions());
			  User postUser =  userMap.get("users:" + userId);
			  cache.setValue("users:" + userId, postUser.updateFrom(other));
			  Log.info("Cache data is completed for NO SQL: " + user);
			  return Result.ok(other);

		  }
		  // Fetch the user from the database
		  CosmosItemResponse<UserDAO> response = users.readItem(userId, new PartitionKey(userId), UserDAO.class);
		  UserDAO existingUserDAO = response.getItem();

		  // Validate the existing user's password
		  if (existingUserDAO == null || !existingUserDAO.getPwd().equals(pwd)) {
			  return Result.error(Result.ErrorCode.CONFLICT); // User doesn't exist or password mismatch
		  }

		  // Update the existing user with the new values

		  UserDAO newUserDAO = new UserDAO(other);
		  // Replace the user item in Cosmos DB with the updated version
		  CosmosItemResponse<UserDAO> updatedResponse = users.replaceItem(newUserDAO, userId, new PartitionKey(userId), new CosmosItemRequestOptions());

		  // Return the updated user (UserDAO) object as a result, but convert it to a User

		  return Result.ok(other);

	  } catch (CosmosException e) {
		  Log.severe(() -> "Error updating user: " + e.getMessage());
		  e.printStackTrace();
		  return Result.error(Result.ErrorCode.INTERNAL_ERROR);
	  }
  }


  private Map<String,User> getFromCache (String userId, String pwd){
	  if (CACHE_MODE) {
		  var key1 = "users:" + userId;
		  var value = cache.getValue(key1,UserDAO.class);
		  Log.info("Cache data is completed for POSTESQL: " + value);
		  if (!value.getPwd().equals(pwd)) {
			  return null;
		  }

		  User newUser = new User(value.getUserId(),value.getPwd(),value.getEmail(), value.getDisplayName());
		  Map<String,User> userMap = new HashMap<>();
		  userMap.put(key1,newUser);
		  return userMap;

	  }
	  return null;
  }

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null) {
			return Result.error(Result.ErrorCode.BAD_REQUEST);
		}
		Map<String,User> userMap = getFromCache(userId,pwd);

		if(DB_MODE.equalsIgnoreCase("post")){
			if(userMap !=null) {
				User postUser =  userMap.get("users:" + userId);
				cache.delete("users:" + userId );
				Log.info("Cache data is deleted for PostSQL: " );

					// Delete user shorts and related info asynchronously in a separate thread
					Executors.defaultThreadFactory().newThread( () -> {
						JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
						JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
					}).start();

					return DB.deleteOne( postUser)  ;

			}
			return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> {
				// Delete user shorts and related info asynchronously in a separate thread
				Executors.defaultThreadFactory().newThread( () -> {
					JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
					JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
				}).start();

				return DB.deleteOne( user);
			});
		}
		try {
			init();
			if(userMap !=null) {
				User postUser =  userMap.get("users:" + userId);
				cache.delete("users:" + userId );
				Log.info("Cache data is deleted for PostSQL: " );

				// Delete user shorts and related info asynchronously in a separate thread
				Executors.defaultThreadFactory().newThread( () -> {
					JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
					JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
				}).start();
				UserDAO userDAO = new UserDAO(postUser);
				return Result.ok(userDAO);

			}
			CosmosItemResponse<UserDAO> response = users.readItem(userId, new PartitionKey(userId), UserDAO.class);
			UserDAO userDAO = response.getItem();

			if (!userDAO.getPwd().equals(pwd)) {
				return Result.error(Result.ErrorCode.CONFLICT);
			}

			users.deleteItem(userId, new PartitionKey(userId),new CosmosItemRequestOptions());

			Executors.defaultThreadFactory().newThread(() -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			return Result.ok(userDAO);
		} catch (CosmosException e) {
			Log.info("Error deleting user: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}
	}


	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());

		var hitss = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		if(DB_MODE.equalsIgnoreCase("post")){
			return ok(hitss);
		}

		try {
			init();

			// Prepare the query - using string concatenation for pattern matching
			String queryText = String.format(
					"SELECT * FROM User u WHERE CONTAINS(UPPER(u.userId), '%s')",
					pattern.toUpperCase().replace("'", "''") // Escape single quotes to prevent issues
			);

			// Execute the query
			CosmosPagedIterable<UserDAO> results = users.queryItems(queryText, new CosmosQueryRequestOptions(), UserDAO.class);

			// Process the results, converting UserDAO to User and copying without password
			List<User> hits = results.stream()
					.map(this::convertToUser) // Convert UserDAO to User
					.collect(Collectors.toList());

			return Result.ok(hits);
		} catch (CosmosException e) {
			Log.info("Error deleting user: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}
	}


	
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}
	
	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}
	
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}
}
