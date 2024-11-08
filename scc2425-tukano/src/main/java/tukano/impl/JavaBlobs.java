package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {

	private static Blobs instance;
	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());
	static String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc242555806;AccountKey=IgxfusKPp9A582bM4UjUNkMXULHtWC1n6FZ5RV7HRdv4La35sC0vk4wyWJbEtxaNpdT5EoMrMTFq+ASt9H5ZGQ==;EndpointSuffix=core.windows.net";
	public String baseURI;
	private BlobStorage storage;
	private BlobContainerClient blobs;
	synchronized public static Blobs getInstance() {
		if (instance != null)
			return instance;

		BlobContainerClient blobs = new BlobContainerClientBuilder()
				.connectionString(storageConnectionString)
				.containerName("blobs")
				.buildClient();

		instance = new JavaBlobs(blobs);
		return instance;
	}

	private JavaBlobs(BlobContainerClient blobs) {
		this.blobs = blobs;
	}


	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));
		if( !validBlobId( blobId, token)  ) {
			return error(FORBIDDEN);
		}
		BlobClient blobClient = blobs.getBlobClient(blobId);
		blobClient.upload(BinaryData.fromBytes(bytes));

		return Result.ok(null);

	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		if( !Token.isValid(token, blobId)  )
			return error(FORBIDDEN);
		BlobClient blob = blobs.getBlobClient(blobId);
		BinaryData data = blob.downloadContent();
		return  Result.ok(data.toBytes());
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		if( !Token.isValid(token, blobId)  )
			return error(FORBIDDEN);
		BlobClient blob = blobs.getBlobClient(blobId);
		boolean isDeleted = blob.deleteIfExists();
		return Result.ok(null);
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
//			List<BlobItem> blobs1 = blobs.listBlobs().stream().collect(Collectors.toList());
//		 for (BlobItem blobItem : containerClient.listBlobs()) {
//             BlobClient blobClient = containerClient.getBlobClient(blobItem.getName());
//             blobClient.delete();
//		 }
		return storage.delete( toPath(userId));
	}

	private boolean validBlobId(String blobId, String token) {
		return Token.isValid(token, blobId);
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
}
