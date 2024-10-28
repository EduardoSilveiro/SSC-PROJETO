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
import java.util.function.Supplier;
import java.util.List;
import java.util.ArrayList ;
import java.util.UUID;
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
import utils.Constants;
import com.azure.cosmos.*;

import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlQuerySpec;


import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import com.azure.cosmos.models.CosmosItemResponse;
import utils.Hash;
public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	private static final String CONNECTION_URL = Constants.tomasConst.getDbUrl();
	private static final String DB_KEY = Constants.tomasConst.getDbKey();
	private static final String DB_NAME = Constants.tomasConst.getDbName();
	private static Shorts instance;
	
	synchronized public static Shorts getInstance() {
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
		instance = new JavaShorts(client);
		return instance;
	}
	private CosmosClient client;
	private CosmosDatabase db;
	private CosmosContainer shorts;
	private CosmosContainer following;

	private CosmosContainer likes;
	private CosmosContainer feeds;
	private JavaShorts(CosmosClient client) {this.client = client;}



	private synchronized void init(String container) {
		if (db != null)
			return;
		//db = client.getDatabase(DB_NAME);
		//users = db.getContainer("users");
		shorts = client.getDatabase("scc2425").getContainer(container);

	}
	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {
			try {
				var shortId = format("%s+%s", userId, UUID.randomUUID());
				var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
				var shrt = new Short(shortId, userId, blobUrl);
				init("shorts");
				ShortDAO shortDAO  = new ShortDAO(shrt).copyWithLikes_And_Token(0);
				Log.info(() -> format("ShortDAO : %s\n", shortDAO));
				Log.info(() -> format("Short  : %s\n", shrt));

				return  Result.ok( shorts.createItem(shortDAO).getItem().toShort().copyWithLikes_And_Token(0) )   ;
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

		if( shortId == null )
			return error(BAD_REQUEST);
		try {
			init("shorts");

			CosmosItemResponse<ShortDAO> response = shorts.readItem(shortId, new PartitionKey(shortId), ShortDAO.class);
			ShortDAO shortDAO = response.getItem();

			var shrt = shortDAO.toShort();
			Log.info(() -> format("ShortDAO : %s\n", shortDAO));
			Log.info(() -> format("Short  : %s\n", shrt));

			return Result.ok(shrt);
		} catch (CosmosException e) {
			Log.info("Error getting user: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}

	}

	
	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));
		
		return errorOrResult( getShort(shortId), shrt -> {
			
			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
				return DB.transaction( hibernate -> {

					hibernate.remove( shrt);
					
					var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
					hibernate.createNativeQuery( query, Likes.class).executeUpdate();
					
					JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
				});
			});	
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));
		try {
			init("shorts");

			String query = String.format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);

			// Define the SQL query specification
			SqlQuerySpec querySpec = new SqlQuerySpec(query);



			// Execute the query
			CosmosPagedIterable<ShortDAO> queryResults = shorts.queryItems(querySpec, new CosmosQueryRequestOptions(), ShortDAO.class);

			// Process results
			List<String> shortIds = new ArrayList<>();
			queryResults.forEach(shortItem -> shortIds.add(shortItem.getShortId()));




			return Result.ok(shortIds);
		} catch (CosmosException e) {
			Log.info("Error getting user: " + e.getMessage());
			e.printStackTrace();
			return Result.error(Result.ErrorCode.INTERNAL_ERROR);
		}

	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));
	
		
		return errorOrResult( okUser(userId1, password), user -> {

			try {
				init("following");
				Result<Void> result;
				FollowingDAO followingDAO = new FollowingDAO(userId1, userId2);

				if (isFollowing) {

					return Result.ok(   CosmosItemResponse<FollowingDAO>  response = following.createItem(followingDAO));
					;
				} else {
					return Result.ok(following.deleteItem(userId1, new PartitionKey(userId1), new CosmosItemRequestOptions()).getItem());
					;
				}
			}catch (Exception x) {
					Log.info("Error creating user: " + x.getMessage());
					x.printStackTrace();
					return Result.error(Result.ErrorCode.INTERNAL_ERROR);
				}
 		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);		
		return errorOrValue( okUser(userId, password), DB.sql(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));	
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);					
			
			return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
				UNION			
				SELECT s.shortId, s.timestamp FROM Short s, Following f 
					WHERE 
						f.followee = s.ownerId AND f.follower = '%s' 
				ORDER BY s.timestamp DESC""";

		return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));		
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
	
}