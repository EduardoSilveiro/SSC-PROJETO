package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static utils.DB.getOne;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.azure.cosmos.models.*;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.util.CosmosPagedIterable;

import redis.clients.jedis.resps.Tuple;
import tukano.api.*;
import tukano.api.Short;
import tukano.api.azure.RedisCache;
import tukano.impl.data.FollowingDAO;
import tukano.impl.data.LikesDAO;
import utils.Constants;
import com.azure.cosmos.*;
import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlQuerySpec;

import java.util.Arrays;
import java.util.stream.Collectors;

import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import com.azure.cosmos.models.CosmosItemResponse;
import utils.Hash;
public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	private static final String CONNECTION_URL = Constants.eduardoConst.getDbUrl();
	private static final String DB_KEY = Constants.eduardoConst.getDbKey();
	private static final String DB_NAME = Constants.eduardoConst.getDbName();
	private static Shorts instance;
	private static RedisCache cache = RedisCache.getInstance(); // Cache instance
	public static boolean CACHE_MODE = Constants.eduardoConst.isCacheActive();

	public static String DB_MODE = Constants.eduardoConst.getDbMode();

	synchronized public static Shorts getInstance() {
		if (instance != null)
			return instance;
		if (DB_MODE.equalsIgnoreCase("post")) {
			instance = new JavaShorts();
			return instance;
		} else {
			CosmosClient client = new CosmosClientBuilder()
					.endpoint(CONNECTION_URL)
					.key(DB_KEY)
					.gatewayMode()
					.consistencyLevel(ConsistencyLevel.SESSION)
					.connectionSharingAcrossClientsEnabled(true)
					.contentResponseOnWriteEnabled(true)
					.buildClient();
			instance = new JavaShorts(client);
			return instance;
		}
	}

	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer shorts;
	private CosmosContainer following;
	private CosmosContainer likes;

	private JavaShorts(CosmosClient client) { this.client = client; }
	public JavaShorts(){}

	private synchronized void initShorts() {
		if (shorts == null) {
			shorts = client.getDatabase(DB_NAME).getContainer("shorts");
		}
	}

	private synchronized void initFollowing() {
		if (following == null) {
			following = client.getDatabase(DB_NAME).getContainer("following");
		}
	}

	private synchronized void initLikes() {
		if (likes == null) {
			likes = client.getDatabase(DB_NAME).getContainer("likes");
		}
	}

	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		if(DB_MODE.equalsIgnoreCase("post")){
			return errorOrResult(okUser(userId, password), user -> {

				var shortId = format("%s+%s", userId, UUID.randomUUID());
				var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
				var shrt = new Short(shortId, userId, blobUrl);

				var blobUrl1 = URI.create(shrt.getBlobUrl());
				var token = blobUrl1.getQuery().split("=")[1];
				JavaBlobs.getInstance().upload(blobUrl ,randomBytes( 100 ),token );
				// Save in DB and Cache
				errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));

				// Cache the short after creation
				if (CACHE_MODE) {
					var cacheKey = "shorts:" + shortId;
					cache.setValue(cacheKey, shrt);
					Log.info(() -> format("Cache data is completed for short: %s", shortId));
				}

				return Result.ok(shrt);
			});
		}

		return errorOrResult(okUser(userId, password), user -> {
			try {
				var shortId = format("%s+%s", userId, UUID.randomUUID());
				var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
				var shrt = new Short(shortId, userId, blobUrl);

				initShorts();
				ShortDAO shortDAO = new ShortDAO(shrt)      ;
						//.copyWithLikes_And_Token(0) ;
				Log.info(() -> format("ShortDAO : %s\n", shortDAO));
				Log.info(() -> format("Short  : %s\n", shrt));


				CosmosItemResponse<ShortDAO> response = shorts.createItem(shortDAO) ;
				ShortDAO shortDAO2  = response.getItem();
				Log.info(() -> format("Created item: %s", shortDAO2 ));

				// Cache after DB update
				if (CACHE_MODE) {
					var cacheKey = "shorts:" + shortId;
					cache.setValue(cacheKey, shrt);
					Log.info(() -> format("Cache data is completed for short: %s", shortId));
				}

//				var blobUrl1 = URI.create(shortDAO.getBlobUrl());
//				var token = blobUrl1.getQuery().split("=")[1];
//				JavaBlobs.getInstance().upload(blobUrl.toString() ,randomBytes( 100 ),token );
				return Result.ok(shrt);
			}  catch (CosmosException e) {
				Log.info("Error creating short: " + e.getMessage());
				e.printStackTrace();
				return Result.error(Result.ErrorCode.INTERNAL_ERROR);
			}
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if (shortId == null){
			return error(BAD_REQUEST);
		}

		// First, check if the short is cached
		if (CACHE_MODE) {
			var cacheKey = "shorts:" + shortId;
			Short cachedShort = cache.getValue(cacheKey, Short.class);
			if (cachedShort != null) {
				Log.info(() -> format("Cache hit for shortId: %s", shortId));
				return Result.ok(cachedShort);
			}
		}

		try {
			initShorts();
			CosmosItemResponse<ShortDAO> response = shorts.readItem(shortId, new PartitionKey(shortId), ShortDAO.class);
			ShortDAO shortDAO = response.getItem();

			var shrt = shortDAO.toShort();
			Log.info(() -> format("ShortDAO : %s\n", shortDAO));
			Log.info(() -> format("Short  : %s\n", shrt));

			// Cache the result for future access
			if (CACHE_MODE) {
				var cacheKey = "shorts:" + shortId;
				cache.setValue(cacheKey, shrt);
				Log.info(() -> format("Cache data is completed for short: %s", shortId));
			}

			return Result.ok(shrt);
		} catch (CosmosException e) {
			Log.info("Error getting short: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		if (DB_MODE.equalsIgnoreCase("post")) {
			return errorOrResult(getShort(shortId), shrt ->
					errorOrResult(okUser(shrt.getOwnerId(), password), user ->
							DB.transaction(hibernate -> {
								hibernate.remove(shrt);
								// Invalidate cache after deletion
								if (CACHE_MODE) {
									var cacheKey = "shorts:" + shortId;
									cache.delete(cacheKey);
									Log.info(() -> format("Cache invalidated for short: %s", shortId));
								}
								var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
								hibernate.createNativeQuery(query, Likes.class).executeUpdate();
								JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
							})));
		}

		return errorOrResult(getShort(shortId), shrt -> {
			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
				initShorts();
				initLikes();

				// Execute a transaction-like operation
				try {
					// Delete the short
					shorts.deleteItem(shortId, new PartitionKey(shortId), new CosmosItemRequestOptions()).getItem(); // Use appropriate partition key
					// Remove from cache
					if (CACHE_MODE) {
						var cacheKey = "shorts:" + shortId;
						cache.delete(cacheKey);
						Log.info(() -> format("Cache invalidated for short: %s", shortId));
					}

					// Delete likes associated with the shortId
					String query = String.format("SELECT * FROM c WHERE c.shortId = '%s'", shortId);
					CosmosPagedIterable<LikesDAO> allLikes = likes.queryItems(query, new CosmosQueryRequestOptions(), LikesDAO.class);

					// Remove each like associated with the shortId
					for (LikesDAO like : allLikes) {
						likes.deleteItem(like.getId(), new PartitionKey(like.getOwnerId()), new CosmosItemRequestOptions()).getItem();
					}

					// Delete the blob associated with the short
					// JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
					return Result.ok( );
				} catch (Exception e) {
					Log.severe(() -> "Error deleting short: " + e.getMessage());
					return Result.error(Result.ErrorCode.INTERNAL_ERROR);
				}
			});
		});
	}




	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		if(DB_MODE.equalsIgnoreCase("post")){
			var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
			return errorOrValue( okUser(userId), DB.sql( query, String.class));
		}

		try {
			initShorts() ;

			String query = String.format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);


			SqlQuerySpec querySpec = new SqlQuerySpec(query);

			CosmosPagedIterable<ShortDAO> queryResults = shorts.queryItems(querySpec, new CosmosQueryRequestOptions(), ShortDAO.class);


			List<String> shortIds = new ArrayList<>();
			queryResults.forEach(shortItem -> shortIds.add(shortItem.getShortId()));

			return Result.ok(shortIds);
		} catch (CosmosException e) {
			Log.info("Error getting shorts: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}

	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

		if(DB_MODE.equalsIgnoreCase("post")){
			return errorOrResult(okUser(userId1, password), user -> {
				var f = new Following(userId1, userId2);
				return errorOrVoid(okUser(userId2), isFollowing ? DB.insertOne(f) : DB.deleteOne(f));

			});
		}
		return errorOrResult( okUser(userId1, password), user -> {

			try {
				initFollowing();
				Result<Void> result = null;
				FollowingDAO followingDAO = new FollowingDAO(userId1, userId2);

				if (isFollowing) {

					CosmosItemResponse<FollowingDAO>  response = following.createItem(followingDAO) ;

					FollowingDAO followingDAO1 = response.getItem();
					Log.info(followingDAO1.toString());
					return Result.ok(  );

				} else {
					following.deleteItem(userId1, new PartitionKey(userId1), new CosmosItemRequestOptions()).getItem();
					return Result.ok( );

				}
			} catch (CosmosException e) {
				Log.info("Error following: " + e.getMessage());
				e.printStackTrace();
				return Result.error(Result.ErrorCode.INTERNAL_ERROR);
			}
		});
	}


	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		if(DB_MODE.equalsIgnoreCase("post")){
			var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
			return errorOrValue( okUser(userId, password), DB.sql(query, String.class));
		}
		return errorOrResult(
				okUser(userId, password),
				user -> {
					try {

						initFollowing();


						String query = String.format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
						SqlQuerySpec querySpec = new SqlQuerySpec(query);


						CosmosPagedIterable<FollowingDAO> queryResults = following.queryItems(querySpec, new CosmosQueryRequestOptions(), FollowingDAO.class);


						List<String> followers = new ArrayList<>();
						queryResults.forEach(item -> followers.add(item.getFollower()));

						return Result.ok(followers);
					} catch (CosmosException e) {
						Log.info("Error getting followers: " + e.getMessage());
						e.printStackTrace();
						return Result.error(Result.ErrorCode.INTERNAL_ERROR);
					}
				}
		);
	}


	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		if (DB_MODE.equalsIgnoreCase("post")) {
			return errorOrResult(getShort(shortId), shrt -> {
				var l = new Likes(userId, shortId, shrt.getOwnerId());
				return errorOrVoid(okUser(userId, password), isLiked ? DB.insertOne(l) : DB.deleteOne(l));
			});
		}

		return errorOrResult(getShort(shortId), shrt -> {
			return errorOrResult(okUser(userId, password), user -> {
				try {
					initLikes();
					LikesDAO like = new LikesDAO(userId, shortId, shrt.getOwnerId());

					// If the user likes the short, add the like
					if (isLiked) {
						CosmosItemResponse<LikesDAO> response = likes.createItem(like);

						Log.info(() -> format("Liked item created: %s", response.getItem()));

						// Increment the totalLikes in the shorts container
						updateTotalLikes(shortId, 1);
					} else {
						// If the user unlikes the short, remove the like
						likes.deleteItem(like.getId(), new PartitionKey(like.getId()), new CosmosItemRequestOptions());
						Log.info(() -> format("Like item deleted for: %s", like.getId()));

						// Decrement the totalLikes in the shorts container
						updateTotalLikes(shortId, -1);
					}

					return Result.ok();
				} catch (CosmosException e) {
					Log.info("Error liking: " + e.getMessage());
					e.printStackTrace();
					return Result.error(Result.ErrorCode.INTERNAL_ERROR);
				}
			});
		});
	}

	// Helper method to update the totalLikes field in the shorts container
	private void updateTotalLikes(String shortId, int likeChange) {
		try {
			// Read the current short from the Cosmos DB
			initShorts();
			CosmosItemResponse<ShortDAO> response = shorts.readItem(shortId, new PartitionKey(shortId), ShortDAO.class);
			ShortDAO shortDAO = response.getItem();

			// Update the totalLikes field
			int newTotalLikes = shortDAO.getTotalLikes() + likeChange;
			shortDAO.setTotalLikes(newTotalLikes);

			// Update the short in the database
			shorts.replaceItem(shortDAO, shortId, new PartitionKey(shortId), new CosmosItemRequestOptions());

			// Update the cache after modifying the totalLikes
			if (CACHE_MODE) {
				var cacheKey = "shorts:" + shortId;
				cache.setValue(cacheKey, shortDAO.toShort());  // Ensure the cache reflects the updated totalLikes
				Log.info(() -> format("Cache data updated for short: %s with new totalLikes: %d", shortId, newTotalLikes));
			}
		} catch (CosmosException e) {
			Log.severe(() -> "Error updating totalLikes for shortId: " + shortId + " : " + e.getMessage());
		}
	}



	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));


		if(DB_MODE.equalsIgnoreCase("post")) {
			return errorOrResult( getShort(shortId), shrt -> {

				var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

				return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
			});
		}
		return errorOrResult(getShort(shortId), shrt -> {
			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
				try {
					initLikes(); // Ensure we are using the "likes" container

					// Define the SQL query to get users who liked the short
					String query = String.format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);
					SqlQuerySpec querySpec = new SqlQuerySpec(query);

					// Execute the query against the Cosmos DB
					CosmosPagedIterable<LikesDAO> queryResults = likes.queryItems(querySpec, new CosmosQueryRequestOptions(), LikesDAO.class);

					// Collect user IDs who liked the short
					List<String> userIds = new ArrayList<>();
					queryResults.forEach(like -> userIds.add(like.getUserId()));

					return Result.ok(userIds);
				} catch (CosmosException e) {
					Log.info("Error getting likes: " + e.getMessage());
					e.printStackTrace();
					return Result.error(Result.ErrorCode.INTERNAL_ERROR);
				}
			});
		});
	}


	@Override
	public Result<List<String>> getFeed(String userId, String password) {
//		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));
//
//		// Check if we are in 'Post' mode or 'Cosmos' mode
//		if (DB_MODE.equalsIgnoreCase("post")) {
//			// Use DB.sql to fetch the user's posts (shorts where ownerId = userId)
//			final var SHORT_QUERY = "SELECT s.shortId, s.timestamp FROM Short s WHERE s.ownerId = :userId";
//			List<Object[]> userPosts = DB.sql(SHORT_QUERY, Object[].class, userId);
//
//			// Use DB.sql to fetch the followees for the user
//			final var FOLLOW_QUERY = "SELECT f.followee FROM Following f WHERE f.follower = :userId";
//			List<String> followees = DB.sql(FOLLOW_QUERY, String.class, userId);
//
//			// Collect posts from followees
//			List<Tuple<String, Long>> resultTuples = new ArrayList<>();
//			for (String followee : followees) {
//				// Query followee posts (shorts where ownerId = followee)
//				String followeeQuery = "SELECT s.shortId, s.timestamp FROM Short s WHERE s.ownerId = :followee";
//				List<Object[]> followeePosts = DB.sql(followeeQuery, Object[].class, followee);
//				for (Object[] post : followeePosts) {
//					resultTuples.add(new Tuple<>((String) post[0], (Long) post[1]));
//				}
//			}
//
//			// Add the user's own posts to the result
//			for (Object[] post : userPosts) {
//				resultTuples.add(new Tuple<>((String) post[0], (Long) post[1]));
//			}
//
//			// Sort all posts by timestamp in descending order
//			resultTuples.sort((t1, t2) -> Long.compare(t2.getT2(), t1.getT2()));
//
//			// Extract sorted shortIds into a result list
//			List<String> result = resultTuples.stream()
//					.map(Tuple::getT1)
//					.collect(Collectors.toList());
//
//			return Result.ok(result);
//		}
//
//		// Case for 'Cosmos' mode (assuming it is not implemented with Hibernate)
//		try {
//			// Use Hibernate to fetch the posts of the user (similar to 'post' mode)
//			String userShortQuery = "SELECT s.shortId, s.timestamp FROM Short s WHERE s.ownerId = :userId";
//			List<Object[]> userPosts = DB.sql(userShortQuery, Object[].class, userId);
//
//			// Fetch the users the user is following
//			String followeeQuery = "SELECT f.followee FROM Following f WHERE f.follower = :userId";
//			List<String> followeesResponse = DB.sql(followeeQuery, String.class, userId);
//
//			// Collect followees from the query
//			List<String> followees = followeesResponse.stream()
//					.collect(Collectors.toList());
//
//			// List to hold all posts
//			List<Tuple<String, Long>> resultTuples = new ArrayList<>();
//
//			// Add the user's own posts to the list
//			for (Object[] post : userPosts) {
//				resultTuples.add(new Tuple<>((String) post[0], (Long) post[1]));
//			}
//
//			// Add the posts from each followee to the list
//			for (String followee : followees) {
//				String followeePostQuery = "SELECT s.shortId, s.timestamp FROM Short s WHERE s.ownerId = :ownerId";
//				List<Object[]> followeePosts = DB.sql(followeePostQuery, Object[].class, followee);
//				for (Object[] post : followeePosts) {
//					resultTuples.add(new Tuple<>((String) post[0], (Long) post[1]));
//				}
//			}
//
//			// Sort all posts by timestamp in descending order
//			resultTuples.sort((t1, t2) -> Long.compare(t2.getT2(), t1.getT2()));
//
//			// Extract shortIds into a result list
//			List<String> result = resultTuples.stream()
//					.map(Tuple::getT1)
//					.collect(Collectors.toList());
//
//			return Result.ok(result);
//
//		} catch (Exception e) {
//			Log.severe(() -> "Error fetching feed: " + e.getMessage());
//			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
//		}
		return ok();}



	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		Log.info("Error getting : " + res.toString()) ;

		if( res.toString().equals("(FORBIDDEN)")  )
			return ok();
		else
			return error( res.error() );
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
//		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));
//
//		if (!Token.isValid(token, userId))
//			return error(FORBIDDEN);
//
//		if (DB_MODE.equalsIgnoreCase("post")) {
//
//			return DB.transaction((hibernate) -> {
//
//				// Delete shorts
//				var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);
//				hibernate.createQuery(query1, Short.class).executeUpdate();
//
//				// Delete follows
//				var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
//				hibernate.createQuery(query2, Following.class).executeUpdate();
//
//				// Delete likes
//				var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
//				hibernate.createQuery(query3, Likes.class).executeUpdate();
//
//				return Result.ok();
//			});
//		} else if (DB_MODE.equalsIgnoreCase("cosmos")) {
//
//
//			try {
//				// Delete shorts from the Cosmos DB
//				initShorts();
//				String shortQuery = "SELECT s.shortId FROM Short s WHERE s.ownerId = @userId";
//				FeedResponse<ShortDAO> shortItems = shorts.queryItems(shortQuery, new CosmosQueryRequestOptions().setQueryParameters(new SqlParameter("@userId", userId)), ShortDAO.class);
//				for (ShortDAO shortItem : shortItems) {
//					shorts.deleteItem(shortItem.getId(), new PartitionKey(shortItem.getId()), new CosmosItemRequestOptions());
//					Log.info(() -> format("Deleted Short: %s", shortItem.getId()));
//				}
//
//				// Delete following from Cosmos DB
//				initFollowing();
//				String followingQuery = "SELECT f.id FROM Following f WHERE f.follower = @userId OR f.followee = @userId";
//				FeedResponse<FollowingDAO> followingItems = following.queryItems(followingQuery, new CosmosQueryRequestOptions().setQueryParameters(new SqlParameter("@userId", userId)), FollowingDAO.class);
//				for (FollowingDAO followingItem : followingItems) {
//					following.deleteItem(followingItem.getId(), new PartitionKey(followingItem.getId()), new CosmosItemRequestOptions());
//					Log.info(() -> format("Deleted Following: %s", followingItem.getId()));
//				}
//
//				// Delete likes from Cosmos DB
//				initLikes();
//				String likesQuery = "SELECT l.id FROM Likes l WHERE l.ownerId = @userId OR l.userId = @userId";
//				FeedResponse<LikesDAO> likeItems = likes.queryItems(likesQuery, new CosmosQueryRequestOptions().setQueryParameters(new SqlParameter("@userId", userId)), LikesDAO.class);
//				for (LikesDAO likeItem : likeItems) {
//					likes.deleteItem(likeItem.getId(), new PartitionKey(likeItem.getId()), new CosmosItemRequestOptions());
//					Log.info(() -> format("Deleted Like: %s", likeItem.getId()));
//				}
//
//				return Result.ok();
//			} catch (CosmosException e) {
//				Log.severe(() -> "Error deleting items: " + e.getMessage());
//				return Result.error(Result.ErrorCode.INTERNAL_ERROR);
//			}
//		} else {
//			return Result.error(Result.ErrorCode.INTERNAL_ERROR); // Handle invalid DB mode
//		}
		return ok();}

	private static byte[] randomBytes(int size) {
		var r = new Random(1L);

		var bb = ByteBuffer.allocate(size);

		r.ints(size).forEach( i -> bb.put( (byte)(i & 0xFF)));

		return bb.array();

	}
}