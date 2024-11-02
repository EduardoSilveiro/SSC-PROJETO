package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static utils.DB.getOne;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;
import tukano.api.*;
import tukano.api.Short;
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
					// .directMode()
					.gatewayMode()
					// replace by .directMode() for better performance
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
	private CosmosContainer feeds;
	private JavaShorts(CosmosClient client) {this.client = client;}

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
			return errorOrResult( okUser(userId, password), user -> {

				var shortId = format("%s+%s", userId, UUID.randomUUID());
				var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
				var shrt = new Short(shortId, userId, blobUrl);
				var blobUrl1 = URI.create(shrt.getBlobUrl());
				var token = blobUrl1.getQuery().split("=")[1];
				JavaBlobs.getInstance().upload(blobUrl ,randomBytes( 100 ),token );
				return errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
			});
		}
		return errorOrResult( okUser(userId, password), user -> {
			try {

				var shortId = format("%s+%s", userId, UUID.randomUUID());
				var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
				var shrt = new Short(shortId, userId, blobUrl);

				initShorts() ;
				ShortDAO shortDAO  = new ShortDAO(shrt).copyWithLikes_And_Token(0);
				Log.info(() -> format("ShortDAO : %s\n", shortDAO));
				Log.info(() -> format("Short  : %s\n", shrt));

				 shorts.createItem(shortDAO).getItem().toShort() ;

				Short s = shortDAO.toShort();

				var blobUrl1 = URI.create(s.getBlobUrl());
				var token = blobUrl1.getQuery().split("=")[1];
				JavaBlobs.getInstance().upload(blobUrl ,randomBytes( 100 ),token );
				return  Result.ok( s  )   ;
			} catch (Exception x) {
				Log.info("Error creating short: " + x.getMessage());
				x.printStackTrace();
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

		if(DB_MODE.equalsIgnoreCase("post")){
			var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
			var likes = DB.sql(query, Long.class);
			return errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
		}

		try {
			initShorts() ;

			CosmosItemResponse<ShortDAO> response = shorts.readItem(shortId, new PartitionKey(shortId), ShortDAO.class);
			ShortDAO shortDAO = response.getItem();

			var shrt = shortDAO.toShort();
			Log.info(() -> format("ShortDAO : %s\n", shortDAO));
			Log.info(() -> format("Short  : %s\n", shrt));

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


		if(DB_MODE.equalsIgnoreCase("post")){
			return errorOrResult( getShort(shortId), shrt ->
						errorOrResult( okUser( shrt.getOwnerId(), password), user ->
							DB.transaction(hibernate -> {
				hibernate.remove( shrt);

				var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
				hibernate.createNativeQuery( query, Likes.class).executeUpdate();

				JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
			})));
		}

 		return errorOrResult(getShort(shortId), shrt -> {
			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {
				initShorts();
				initLikes();

				// Execute a transaction-like operation
				try {
					// Delete the short
					shorts.deleteItem(shortId, new PartitionKey(shortId),  new CosmosItemRequestOptions()).getItem() ; // Use appropriate partition key
					// following.deleteItem(userId1, new PartitionKey(userId1), new CosmosItemRequestOptions()).getItem();

					// Delete likes associated with the shortId
					String query = String.format("SELECT * FROM c WHERE c.shortId = '%s'", shortId);
					CosmosPagedIterable<LikesDAO> allLikes = likes.queryItems(query, new CosmosQueryRequestOptions(), LikesDAO.class);

					// Remove each like associated with the shortId
					for (LikesDAO like : allLikes) {
						likes.deleteItem(like.getId(), new PartitionKey(like.getOwnerId()) ,new CosmosItemRequestOptions()).getItem() ;
					}

					// Delete the blob associated with the short
					JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());

					return ok(null);
				} catch (CosmosException e) {
					Log.info("Error deleting short: " + e.getMessage());
					e.printStackTrace();
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
					return Result.ok( );

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


	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		if(DB_MODE.equalsIgnoreCase("post")) {
			return errorOrResult( getShort(shortId), shrt -> {
				var l = new Likes(userId, shortId, shrt.getOwnerId());
				return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));
			});

		}
			return errorOrResult(getShort(shortId), shrt -> {

			return errorOrResult(okUser(userId, password), user -> {
				try {
					initLikes();
					LikesDAO like = new LikesDAO(userId, shortId, shrt.getOwnerId());
					if (isLiked) {

						CosmosItemResponse<LikesDAO> response = likes.createItem(like);
						Log.info(() -> format("Liked item created: %s", response.getItem()));
					} else {

						likes.deleteItem(like.getId(), new PartitionKey(like.getId()), new CosmosItemRequestOptions());
						Log.info(() -> format("Like item deleted for: %s", like.getId()));
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
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

//		final var SHORT_QUERY = format("SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'" , userId);

// 		List<Map> query1 = cosmos.query(Map.class , SHORT_QUERY, shorts).value();

////		List<Tuple<String, Long >> res1 = query1.stream()
//				.map(result -> new Tuple<>((String) result.get("shortId"), (Long) result.get("timestamp")))
//				.collect(Collectors.toList());

//		final var FOLLOW_QUERY = format("SELECT f.followee Following f WHERE f.follower = '%s'", userId);
//		List<Map> query2 = cosmos.query(Map.class , FOLLOW_QUERY, following).value();
////		List<String> followees = query2.stream().map(result -> result.get("followee").toString()).toList() ;

//		List<Tuple<String, Long >> resultTuples =new ArrayList<>() ;

//		for(String f : followees)  {}

//		res1.addAll(resultTuples);

		//	res1.sort((t1,t2) -> Long.compare(t2.getT2(), t1.getT2()));

//		List<String> result =new ArrayList<>();
//		for (Tuple<String, Long> s : res1) {
//			result.add(s.getT1());
//		}
 return Result.ok( )	;
	}
		
	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}
	
	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		Log.info("Error getting CAFAA: " + res.toString()) ;

		if( res.toString().equals("(FORBIDDEN)")  )
			return ok();
		else
			return error( res.error() );
	}
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
		
		return DB.transaction( (hibernate) -> {
						
			//delete shorts
			var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);		
			hibernate.createQuery(query1, Short.class).executeUpdate();
			
			//delete follows
			var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);		
			hibernate.createQuery(query2, Following.class).executeUpdate();
			
			//delete likes
			var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);		
			hibernate.createQuery(query3, Likes.class).executeUpdate();
			
		});
	}
	private static byte[] randomBytes(int size) {
		var r = new Random(1L);

		var bb = ByteBuffer.allocate(size);

		r.ints(size).forEach( i -> bb.put( (byte)(i & 0xFF)));

		return bb.array();

	}
}