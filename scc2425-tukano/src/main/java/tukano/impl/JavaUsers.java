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
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.UserDAO;
import tukano.api.Users;
import utils.Constants;
import utils.DB;

import com.azure.cosmos.models.CosmosItemResponse;
import utils.Hash;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
	private static final String CONNECTION_URL = Constants.eduardoConst.getDbUrl();
	private static final String DB_KEY = Constants.eduardoConst.getDbKey();
	private static final String DB_NAME = Constants.eduardoConst.getDbName();
	private static Users instance;

	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer users;

	public static synchronized Users getInstance() {
		if (instance != null)
			return instance;

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
		return instance;

	}

	public JavaUsers(CosmosClient client) {
		this.client = client;
	}
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
		users = client.getDatabase("scc2324").getContainer("users");

	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));
		if (user.getUserId() == null || user.getPwd() == null || user.getEmail() == null || user.getDisplayName() == null) {
			Log.info("User data is incomplete: " + user);
			return Result.error(Result.ErrorCode.CONFLICT);
		}
		try {
			init();
			UserDAO userDAO = new UserDAO(user);
			Log.info(() -> format("UserDAO : %s\n", userDAO));
			return tryCatch(() -> users.createItem(userDAO).getItem().toString());
		} catch (Exception x) {
			Log.info("Error creating user: " + x.getMessage());
			x.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}
	}
	//NOT TESTED
	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null) {
			return Result.error(Result.ErrorCode.BAD_REQUEST);
		}

		try {
			init();

			CosmosItemResponse<UserDAO> response = users.readItem(userId, new PartitionKey(userId), UserDAO.class);
			UserDAO userDAO = response.getItem();

			if (!userDAO.getPwd().equals(pwd) || userDAO==null ) {
				return Result.error(Result.ErrorCode.CONFLICT);
			}


			return Result.ok(userDAO);
		} catch (CosmosException e) {
			Log.info("Error deleting user: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}
		
	}
  //NOT TESTED
	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		// Check for invalid input
		if (badUpdateUserInfo(userId, pwd, other)) {
			return error(BAD_REQUEST);
		}

		try {
			init();

			// Fetch the user
			CosmosItemResponse<UserDAO> response = users.readItem(userId, new PartitionKey(userId), UserDAO.class);
			UserDAO existingUserDAO = response.getItem();


			if (existingUserDAO == null || !existingUserDAO.getPwd().equals(pwd)) {
				return Result.error(Result.ErrorCode.CONFLICT);
			}

			// Update the User
			existingUserDAO.updateFrom(other);

			// Replace the existing user in the database with the updated version
			CosmosItemResponse<UserDAO> updatedResponse = users.replaceItem(existingUserDAO, userId, new PartitionKey(userId), new CosmosItemRequestOptions());

			// Return the updated user as the result
			UserDAO updatedUserDAO = updatedResponse.getItem();
			return Result.ok(updatedUserDAO);

		} catch (CosmosException e) {
			Log.info("Error updating user: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}
	}


	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null) {
			return Result.error(Result.ErrorCode.BAD_REQUEST);
		}

		try {
			init();

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
		var hits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
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
