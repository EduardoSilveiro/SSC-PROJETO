package test;

import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;

import tukano.api.Result;
import tukano.api.User;
import tukano.clients.rest.RestBlobsClient;
import tukano.clients.rest.RestShortsClient;
import tukano.clients.rest.RestUsersClient;
import tukano.impl.rest.TukanoRestServer;
import utils.Hash;
import utils.Hex;

import org.glassfish.jersey.jackson.JacksonFeature;


public class Test {

	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s");
	}

	public static void main(String[] args ) throws Exception {
		new Thread( () -> {
			try {
				TukanoRestServer.main( new String[] {} );
			} catch( Exception x ) {
				x.printStackTrace();
			}
		}).start();


		Thread.sleep(10000);

		var serverURI = String.format("http://localhost:%s/rest", TukanoRestServer.PORT);

		var blobs = new RestBlobsClient(serverURI);
		var users = new RestUsersClient( serverURI);
		var shorts = new RestShortsClient(serverURI);

//
//	 users.createUser( new User("wales", "12345", "jimmy@wikipedia.pt", "Jimmy Wales")) ;
//  users.createUser( new User("liskov", "54321", "liskov@mit.edu", "Barbara Liskov")) ;
//		 show(users.createUser( new User("eduardo", "54321", "liskov@mit.edu", "Barbara Liskov") ));
//  show(users.createUser( new User("joao", "54321", "liskov@mit.edu", "Barbara Liskov") ));
// show(users.createUser( new User("jojjao", "54321", "liskov@mit.edu", "Barbara Liskov") ));
// show(users.createUser( new User("jo2jjao", "54321", "liskov@mit.edu", "Barbara Liskov") ));

		show(users.deleteUser("wales", "12345"));

		show(users.getUser("wales", "12345"));
		show(users.getUser("liskov", "54321"));

		 users.updateUser("wales", "12345", new User("wales", "12345", "jimmy@wikipedia.com", "updated " ) ) ;
//
//		show(users.searchUsers(""));
//
//
 		Result<tukano.api.Short> s1, s2;
//
 show(s2 = shorts.createShort("liskov", "54321"));
 show(s1 = shorts.createShort("wales", "12345"));
  show(shorts.createShort("wales", "12345"));
  show(shorts.createShort("wales", "12345"));
		 show(shorts.createShort("wales", "12345"));
//
//		 		var blobUrl = URI.create(s2.value().getBlobUrl());
//		//	System.out.println( "------->" + blobUrl );
//
//		//var blobId = new File( blobUrl.getPath() ).getName();
//		//System.out.println( "BlobID:" + blobId );
////
//		 var token = blobUrl.getQuery().split("=")[1];
////
//		//blobs.upload(blobUrl.toString(), randomBytes( 100 ), token);
//		//	System.out.println( Hex.of(Hash.sha256( token.getBytes() )) + "-->UPLOAD HERE"   ) ;
//
//		//var r = blobs.download(blobUrl.toString(), token);
//		//System.out.println( Hex.of(Hash.sha256( r.value() )) + "-->DOWNLOADED HERE" + Hex.of(Hash.sha256( r.value() )));
////		var d = blobs.delete("http://Tomas:8080/rest/blobs/liskov+ab1a4521-91d2-42a5-ab67-ac7fd021a5ed?token=1729178892270-837017D4C2679C7760DEE522915D058C".toString(), null);
////
	var s2id = s2.value().getShortId();
//
// 	show(shorts.follow("liskov", "wales", true, "54321"));
// 	show(shorts.followers("wales", "12345"));
//
 		show(shorts.like(s2id, "liskov", true, "54321"));
 		show(shorts.like(s2id, "liskov", true, "54321"));
 		show(shorts.likes(s2id , "54321"));
		show(shorts.getFeed("liskov", "12345"));
 		show(shorts.getShort( "liskov+898759d6-f0bb-4442-8493-4c3907c05b05" ));

 	show(shorts.getShorts( "wales" ));

		show(shorts.followers("wales", "12345"));
//
//		show(shorts.getFeed("liskov", "12345"));
//
// 		show(shorts.getShort( "wales+91fd3ad4-567b-4496-a32f-4af7c4818a38" ));
////
//
//		blobs.forEach( b -> {
//			var r = b.download(blobId);
//			System.out.println( Hex.of(Hash.sha256( bytes )) + "-->" + Hex.of(Hash.sha256( r.value() )));
//
//		});

		//show(users.deleteUser("wales", "12345"));

		System.exit(0);
	}


	private static Result<?> show( Result<?> res ) {
		if( res.isOK() )
			System.err.println("OK: " + res.value() );
		else
			System.err.println("ERROR:" + res.error());
		return res;

	}

	private static byte[] randomBytes(int size) {
		var r = new Random(1L);

		var bb = ByteBuffer.allocate(size);

		r.ints(size).forEach( i -> bb.put( (byte)(i & 0xFF)));

		return bb.array();

	}
}
